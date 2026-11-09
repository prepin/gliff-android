# Verification

## Host checks

```sh
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
python3 scripts/test_fixture.py
python3 scripts/test_device_check.py
```

The JUnit tests cover upstream wire bytes, malformed frames, key mapping, letterbox coordinates, and clipboard transfer limits.
The Python tests cover the SSH fixture framing and H.264 access-unit parser.

## Android emulator checks

Requirements: a running emulator, `adb`, OpenSSH tools, Python `cbor2`, and FFmpeg with `libx264`.
The test uses disposable SSH keys, not personal credentials.
The SSH server listens only on `127.0.0.1` and stops after the test.
The emulator reaches that server through `10.0.2.2`.

```sh
python3 scripts/device-check.py --serial emulator-5554
python3 scripts/device-check.py --serial emulator-5554 --changed-host
```

The first command installs the app and the instrumentation APK.
The second command reuses the address with a different host key.
It verifies that the app rejects the changed key without an approval dialog.
The second command requires the first command to complete successfully.

The instrumentation verifies actual H.264 output from MediaCodec, not only encoded message construction.
It also checks input release, full screen, and return to the connection form.
The SSH fixture verifies the forwarded protocol messages, including frame acknowledgments and clipboard acknowledgments.

The input checks call Android activity handlers with key and mouse events.
These checks do not establish that Samsung delivers system-reserved shortcuts to those handlers.

Logs and screenshots appear in `.tools/device-check/`.
The test modifies only the selected Android device and temporary project files.

## Upstream compatibility

On a host with an active Hyprland session and the original `gliff-server`:

```sh
python3 scripts/device-check.py --serial emulator-5554 --upstream --port 22223
```

This check creates a temporary private Hyprland output through Gliff.
It sends no keyboard or mouse input to the desktop.
The output disappears after disconnect.
An empty private output can contain only a black background and a cursor.

## Physical Android device over USB or wireless ADB

Before the check, unlock the device and leave Gliff visible on the display.
A sleeping or locked device can prevent Android from creating the video surface.

For an authorized USB device, use its serial from `adb devices -l`:

```sh
python3 scripts/device-check.py --serial DEVICE_SERIAL --port 22224
python3 scripts/device-check.py --serial DEVICE_SERIAL --port 22224 --changed-host
python3 scripts/device-check.py --serial DEVICE_SERIAL --port 22225 --upstream
```

The script creates an ADB reverse tunnel to the loopback SSH server.
It removes that tunnel after the check, including after an error.
No network-facing SSH listener or wireless debugging port is necessary.

Clipboard tests are disabled on physical devices by default.
The SSH fixture rejects a result that offers or sends local clipboard text.
The optional `--clipboard` flag enables clipboard checks and can replace the device clipboard.

On physical devices, the fixture check also injects shortcut chords through Android input dispatch.
It verifies each complete chord at the SSH peer, including modifier releases.
These checks exercise system dispatch, but do not replace a physical-keyboard check in DeX.
The checks do not inject lock, power, or destructive system actions.

Logs and screenshots appear in `.tools/physical-device-check/` and `.tools/physical-upstream-check/`.

## DeX display

After wireless ADB pairing, use the connected device address as the serial.
Use the DeX display ID from `adb shell dumpsys display`.

```sh
python3 scripts/device-check.py --serial PHONE_IP:ADB_PORT --display DISPLAY_ID --port 22226
python3 scripts/device-check.py --serial PHONE_IP:ADB_PORT --display DISPLAY_ID --port 22227 --upstream
```

The instrumentation checks that Gliff actually runs on the requested display.
On Android 14 or later, it captures the Gliff window rather than the phone display.
Keyboard chords pass through Android input dispatch.
Mouse clicks and scroll events target the selected display explicitly.

The `--skip-install` flag reuses APKs that are already installed from the current build.
After a build change, reinstall the affected APK before that flag is used.
Each ADB command has a timeout, so a stalled wireless install cannot wait indefinitely.

DeX logs and screenshots appear in `.tools/dex-device-check/` and `.tools/dex-upstream-check/`.

## Samsung DeX

The manual procedure is in [dex-checklist.md](dex-checklist.md).
No Samsung device was attached during the emulator checks.
