import unittest

from release import validate


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.env = {"GITEA_TOKEN": "test", "GITHUB_TOKEN": "test"}
        self.gradle = "versionName '0.1.0'"

    def test_matching_preview(self):
        validate("v0.1.0-preview.1", self.gradle, "", self.env)

    def test_rejects_stable_and_wrong_versions(self):
        for tag in ["v0.1.0", "v0.2.0-preview.1", "v0.1.0-preview.0"]:
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                validate(tag, self.gradle, "", self.env)

    def test_rejects_uncommitted_changes(self):
        with self.assertRaises(ValueError):
            validate("v0.1.0-preview.1", self.gradle, " M README.md", self.env)

    def test_requires_both_credentials_before_publication(self):
        for token in self.env:
            with self.subTest(token=token), self.assertRaisesRegex(ValueError, token):
                validate(
                    "v0.1.0-preview.1",
                    self.gradle,
                    "",
                    {k: v for k, v in self.env.items() if k != token},
                )
