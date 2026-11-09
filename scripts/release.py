#!/usr/bin/env python3
"""Publish the same development APK to both release hosts."""

import os
from pathlib import Path
import re
import subprocess


def validate(tag, gradle, dirty, env):
    version = re.search(r"versionName '([^']+)'", gradle).group(1)
    # shortcut: previews only, add stable releases after production signing exists.
    if not re.fullmatch(r"v" + re.escape(version) + r"-preview\.[1-9][0-9]*", tag):
        raise ValueError(f"Tag must be v{version}-preview.N and match the APK version")
    if dirty:
        raise ValueError("Commit all changes before publishing")
    for token in ["GITEA_TOKEN", "GITHUB_TOKEN"]:
        if not env.get(token):
            raise ValueError(f"Export {token} before publishing")


def main():
    tag = subprocess.check_output(
        ["git", "describe", "--exact-match", "--tags", "HEAD"], text=True
    ).strip()
    dirty = subprocess.check_output(["git", "status", "--porcelain"], text=True)
    validate(tag, Path("app/build.gradle").read_text(), dirty, os.environ)
    for config in [".goreleaser.yaml", ".goreleaser.github.yaml"]:
        subprocess.run(
            [
                "goreleaser",
                "release",
                "--clean",
                "--config",
                config,
                "--release-notes",
                "docs/release-notes.md",
            ],
            check=True,
        )


if __name__ == "__main__":
    main()
