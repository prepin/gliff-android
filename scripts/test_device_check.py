import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location(
    "device_check", Path(__file__).with_name("device-check.py")
)
device_check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(device_check)


class SystemChordTests(unittest.TestCase):
    def test_commands_have_a_bounded_default_timeout(self):
        with patch.object(device_check.subprocess, "run") as command:
            device_check.run("adb", "devices")
            command.assert_called_once_with(
                ("adb", "devices"), check=True, text=True, timeout=60
            )

    def test_explicit_test_timeout_is_preserved(self):
        with patch.object(device_check.subprocess, "run") as command:
            device_check.run("adb", "shell", timeout=150)
            command.assert_called_once_with(
                ("adb", "shell"), check=True, text=True, timeout=150
            )

    def test_requires_every_press_and_release_in_order(self):
        events = []
        for chord in device_check.SYSTEM_CHORDS:
            events.extend([[key, True] for key in chord])
            events.extend([[key, False] for key in reversed(chord)])
        device_check.verify_system_chords(events)
        events.pop()
        with self.assertRaises(AssertionError):
            device_check.verify_system_chords(events)


if __name__ == "__main__":
    unittest.main()
