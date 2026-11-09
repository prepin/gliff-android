#!/usr/bin/env python3
"""Run the APK against a disposable, loopback-only SSH server. Never uses a personal SSH key."""

import argparse
import base64
import getpass
import json
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import time


def run(*command, **kwargs):
    kwargs.setdefault("timeout", 60)
    return subprocess.run(command, check=True, text=True, **kwargs)


SYSTEM_CHORDS = [
    [125, 28],
    [125, 57],
    [56, 15],
    [56, 42, 15],
    [29, 42, 46],
    [125, 2],
    [125, 42, 2],
    [125, 103],
    [125, 29, 106],
    [125, 59],
]


def verify_system_chords(keys):
    for chord in SYSTEM_CHORDS:
        expected = [[key, True] for key in chord] + [
            [key, False] for key in reversed(chord)
        ]
        assert any(keys[i : i + len(expected)] == expected for i in range(len(keys))), (
            f"System chord did not reach the server intact: {chord}"
        )
    print(
        f"PASS: {len(SYSTEM_CHORDS)} system-dispatched shortcut chords reached the SSH peer intact"
    )


def verify_events(path, clipboard=True, system_input=False, display=False):
    events = [json.loads(line) for line in path.read_text().splitlines()]
    assert events[0]["type"] == 0, (
        "Hello must precede all queued input/clipboard messages"
    )
    assert any(e["type"] == 16 and e["fields"] == [123456] for e in events), (
        "Missing handshake pong"
    )
    assert sum(e["type"] == 6 for e in events) >= 2, "No decoded frame ACKs"
    keys = [e["fields"] for e in events if e["type"] == 2]
    for key in [125, 28, 15, 56, 29, 42]:
        assert [key, True] in keys and [key, False] in keys, (
            f"Missing key press/release: {key}"
        )
    if system_input:
        verify_system_chords(keys)
    if display:
        for state in [True, False]:
            assert any(
                e["type"] == 4 and e["fields"] == [272, state] for e in events
            ), "System-dispatched DeX click did not reach the server"
    assert [1, True] not in keys, "Release hotkey leaked to the remote host"
    assert any(e["type"] == 4 and e["fields"] == [273, True] for e in events), (
        "Missing right click"
    )
    assert any(e["type"] == 4 and e["fields"] == [273, False] for e in events), (
        "Missing right release"
    )
    assert any(e["type"] == 5 and e["fields"][1] == -15.0 for e in events), (
        "Missing scroll"
    )
    assert any(
        e["type"] == 3
        and abs(e["fields"][0] - 320) < 2
        and abs(e["fields"][1] - 180) < 2
        for e in events
    ), "Incorrect pointer mapping"
    if clipboard:
        assert any(e["type"] == 13 for e in events), (
            "Remote clipboard was not acknowledged"
        )
    else:
        assert not any(e["type"] == 8 and e["fields"][1] for e in events), (
            "Private clipboard was offered"
        )
        assert not any(e["type"] == 10 for e in events), (
            "Private clipboard contents were sent"
        )
    assert any(e["type"] == 12 for e in events), "Missing graceful Bye"
    print(
        f"PASS: SSH fixture verified {len(events)} protocol messages: video ACKs, Super/Enter/Tab, focus-loss release, mouse, scroll, {'clipboard' if clipboard else 'clipboard disabled (no text offered or sent)'}, Bye"
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--port", type=int, default=22222)
    parser.add_argument(
        "--display",
        type=int,
        help="Launch and verify the app on this external display ID",
    )
    parser.add_argument(
        "--clipboard",
        action="store_true",
        help="Test clipboard sharing on a physical device (overwrites its clipboard). Enabled by default only on emulators.",
    )
    parser.add_argument(
        "--upstream",
        action="store_true",
        help="Use installed gliff-server; creates a temporary private Hyprland output, sends no keyboard/mouse input",
    )
    parser.add_argument(
        "--changed-host",
        action="store_true",
        help="After a normal fixture run, prove that a changed host key is rejected",
    )
    parser.add_argument(
        "--skip-install",
        action="store_true",
        help="Reuse APKs already installed from the current build",
    )
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    emulator = args.serial.startswith("emulator-")
    clipboard = emulator or args.clipboard
    name = "upstream-check" if args.upstream else "device-check"
    work = (
        root
        / ".tools"
        / (
            "dex-" + name
            if args.display is not None
            else name
            if emulator
            else "physical-" + name
        )
    )
    work.mkdir(parents=True, exist_ok=True)
    sshd = shutil.which("sshd")
    if not sshd:
        sys.exit("Install OpenSSH server tools (sshd) first.")
    for key in ["host", "host-changed", "client"]:
        if not (work / key).exists():
            run("ssh-keygen", "-q", "-t", "ed25519", "-N", "", "-f", str(work / key))
    if not args.upstream:
        run(
            "ffmpeg",
            "-hide_banner",
            "-loglevel",
            "error",
            "-f",
            "lavfi",
            "-i",
            "testsrc2=size=640x360:rate=30",
            "-t",
            "4",
            "-c:v",
            "libx264",
            "-preset",
            "ultrafast",
            "-tune",
            "zerolatency",
            "-x264-params",
            "aud=1:keyint=30:repeat-headers=1",
            "-f",
            "h264",
            "-y",
            str(work / "video.h264"),
        )
    command = (
        "gliff-server --stdio --headless --low-bandwidth --video cpu --capture shm"
        if args.upstream
        else (
            f"{shlex.quote(sys.executable)} {shlex.quote(str(root / 'scripts/fixture_server.py'))}"
        )
    )
    if not args.upstream:
        command = f"env GLIFF_FIXTURE_DIR={shlex.quote(str(work))} " + command
    host_key = "host-changed" if args.changed_host else "host"
    config = work / "sshd_config"
    config.write_text(f"""Port {args.port}
ListenAddress 127.0.0.1
HostKey {work / host_key}
PidFile {work / "sshd.pid"}
AuthorizedKeysFile {work / "client.pub"}
StrictModes no
PasswordAuthentication no
KbdInteractiveAuthentication no
UsePAM no
AllowUsers {getpass.getuser()}
AllowTcpForwarding no
X11Forwarding no
PermitTunnel no
ForceCommand {command}
LogLevel VERBOSE
""")
    if not args.changed_host:
        (work / "events.jsonl").write_text("")
    fingerprint = run(
        "ssh-keygen", "-lf", str(work / "host.pub"), capture_output=True
    ).stdout.split()[1]
    identity = base64.b64encode((work / "client").read_bytes()).decode()
    adb = ["adb", "-s", args.serial]
    if not args.skip_install:
        run(
            *adb,
            "install",
            "--no-streaming",
            "-r",
            str(root / "app/build/outputs/apk/debug/app-debug.apk"),
        )
        run(
            *adb,
            "install",
            "--no-streaming",
            "-r",
            str(
                root
                / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
            ),
        )
    suffix = "-changed" if args.changed_host else ""
    with (work / f"sshd{suffix}.log").open("w") as log:
        server = subprocess.Popen(
            [sshd, "-D", "-e", "-f", str(config)], stdout=log, stderr=log
        )
        reverse_created = False
        try:
            time.sleep(0.3)
            if server.poll() is not None:
                sys.exit((work / f"sshd{suffix}.log").read_text())
            if not emulator:
                run(
                    *adb,
                    "reverse",
                    "--no-rebind",
                    f"tcp:{args.port}",
                    f"tcp:{args.port}",
                )
                reverse_created = True
            result = run(
                *adb,
                "shell",
                "am",
                "instrument",
                "-w",
                "-e",
                "identity",
                identity,
                "-e",
                "user",
                getpass.getuser(),
                "-e",
                "host",
                "10.0.2.2" if emulator else "127.0.0.1",
                "-e",
                "shareClipboard",
                str(clipboard).lower(),
                "-e",
                "systemInput",
                str(not emulator).lower(),
                "-e",
                "display",
                str(args.display if args.display is not None else -1),
                "-e",
                "port",
                str(args.port),
                "-e",
                "fingerprint",
                fingerprint,
                "-e",
                "exerciseInput",
                "false" if args.upstream else "true",
                "-e",
                "rejectChanged",
                "true" if args.changed_host else "false",
                "org.omarchy.gliff.test/org.omarchy.gliff.DeviceChecks",
                capture_output=True,
                timeout=150,
            )
            (work / f"instrumentation{suffix}.txt").write_text(
                result.stdout + result.stderr
            )
            print(result.stdout)
            assert "PASS: " in result.stdout, (
                "Device checks failed; see instrumentation.txt and sshd.log"
            )
            if not args.upstream and not args.changed_host:
                verify_events(
                    work / "events.jsonl",
                    clipboard,
                    not emulator,
                    args.display is not None,
                )
            if not args.changed_host:
                run(
                    *adb,
                    "pull",
                    "/sdcard/Android/data/org.omarchy.gliff/files/device-check.png",
                    str(work / "screenshot.png"),
                )
                if args.display is not None:
                    run(
                        *adb,
                        "pull",
                        "/sdcard/Android/data/org.omarchy.gliff/files/fullscreen-check.png",
                        str(work / "fullscreen.png"),
                    )
        finally:
            try:
                if reverse_created:
                    run(*adb, "reverse", "--remove", f"tcp:{args.port}")
            finally:
                server.terminate()
                server.wait(timeout=10)


if __name__ == "__main__":
    main()
