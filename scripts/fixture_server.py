#!/usr/bin/env python3
"""Gliff v1 peer for device tests. Run ONLY as a forced command of a loopback test SSH server."""

import json
import os
from pathlib import Path
import struct
import sys
import threading
import time

import cbor2


def access_units(data):
    """Split Annex B at access-unit delimiters inserted by ffmpeg's aud=1."""
    import re

    boundaries = [
        m.start() for m in re.finditer(b"\x00\x00\x00\x01\x09|\x00\x00\x01\x09", data)
    ]
    if not boundaries or boundaries[0] != 0:
        raise ValueError("Test H.264 must start with AUD; encode with x264 aud=1")
    return [data[a:b] for a, b in zip(boundaries, boundaries[1:] + [len(data)])]


def receive(stream):
    header = stream.read(8)
    if not header:
        raise EOFError
    if len(header) != 8:
        raise ValueError("Truncated header")
    body, payload = struct.unpack("<II", header)
    if not 0 < body <= 1024 * 1024 or payload > 32 * 1024 * 1024:
        raise ValueError("Invalid frame size")
    data = stream.read(body + payload)
    if len(data) != body + payload:
        raise ValueError("Truncated frame")
    kind, fields = cbor2.loads(data[:body])
    return kind, fields, data[body:]


def wire(kind, fields, payload=b""):
    body = cbor2.dumps([kind, fields])
    return struct.pack("<II", len(body), len(payload)) + body + payload


def main():
    directory = Path(os.environ["GLIFF_FIXTURE_DIR"])
    log = (directory / "events.jsonl").open("a", buffering=1)
    lock = threading.Lock()
    stop = threading.Event()

    def send(kind, fields, payload=b""):
        with lock:
            sys.stdout.buffer.write(wire(kind, fields, payload))
            sys.stdout.buffer.flush()

    def record(kind, fields, payload):
        log.write(
            json.dumps({"type": kind, "fields": fields, "payload_len": len(payload)})
            + "\n"
        )

    kind, hello, payload = receive(sys.stdin.buffer)
    record(kind, hello, payload)
    assert kind == 0 and hello[0] == 1 and 1 in hello[2][0] and 2 in hello[2][3], (
        "Bad client Hello"
    )
    send(0, [1, [True, "HEADLESS-TEST"], [["HEADLESS-TEST", 640, 360, 1000]], []])
    send(12, [123456])
    send(1, [1, 2, 1, 640, 360, 1000, b"", None, 640, 360, 30])

    frames = access_units((directory / "video.h264").read_bytes())

    def video():
        try:
            for index in range(900):
                if stop.wait(1 / 30):
                    break
                data = frames[index % len(frames)]
                send(
                    2,
                    [index + 1, index * 33333, index % 30 == 0, [], len(data), 0],
                    data,
                )
        except (BrokenPipeError, OSError):
            stop.set()

    threading.Thread(target=video, daemon=True).start()
    offered = False
    try:
        while True:
            kind, fields, payload = receive(sys.stdin.buffer)
            record(kind, fields, payload)
            if kind == 12:
                break
            if kind == 6 and not offered:
                send(5, [42, ["text/plain;charset=utf-8"], []])
                offered = True
            if kind == 9:
                assert fields[1] == 42
                data = b"Hello from the Gliff test host"
                send(7, [fields[0], 0, len(data), True], data)
            if kind == 8 and fields[1]:
                send(6, [2, fields[0], [0, ["text/plain;charset=utf-8"]]])
    except EOFError:
        pass
    finally:
        stop.set()
        log.close()


if __name__ == "__main__":
    main()
