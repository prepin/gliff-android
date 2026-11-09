import io
import unittest

from fixture_server import access_units, receive, wire


class FixtureTests(unittest.TestCase):
    def test_frames_roundtrip_with_payload(self):
        self.assertEqual(
            (2, [30, True], b"payload"),
            receive(io.BytesIO(wire(2, [30, True], b"payload"))),
        )

    def test_access_unit_delimiters(self):
        aud = b"\x00\x00\x00\x01\x09\xf0"
        self.assertEqual(
            [aud + b"abc", aud + b"def"], access_units(aud + b"abc" + aud + b"def")
        )
        with self.assertRaises(ValueError):
            access_units(b"not video")

    def test_truncated_frame_is_rejected(self):
        with self.assertRaises(ValueError):
            receive(io.BytesIO(wire(1, [])[:-1]))


if __name__ == "__main__":
    unittest.main()
