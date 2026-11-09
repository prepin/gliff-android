# Releases and the GitHub mirror

The primary repository is https://git.prepin.net/prepin/gliff-android.
Forgejo mirrors branches and tags to https://github.com/prepin/gliff-android.
The mirror uses a writable deploy key limited to that GitHub repository.
Forgejo mirrors on each push and also checks hourly.
Releases are not part of Git mirroring, so GoReleaser publishes each release to both hosts.

## Build and check

Requirements: mise, Python 3, Git, and Android SDK platform 36 with accepted licenses.
The mise configuration installs Java 21 and GoReleaser 2.20.0.

```sh
mise trust
mise install
export ANDROID_HOME=/path/to/android-sdk
mise run check
mise run build
```

## Publish a preview

The preview APK is debuggable and uses the local Android development key.
Different machines can produce different signing keys.
Do not publish a stable release until production signing and secure key storage exist.

1. Update the Android version in `app/build.gradle`.
2. Update `docs/release-notes.md` with the changes and test results.
3. Commit all changes.
4. Create a preview tag that matches the Android version.

   ```sh
   git tag -a v0.1.0-preview.1 -m "First Android development preview"
   mise run mirror
   ```

5. Export a Forgejo token with repository-write access as `GITEA_TOKEN` through your secret manager.
6. Export the GitHub token from the authenticated CLI.

   ```sh
   export GITHUB_TOKEN="$(gh auth token)"
   mise run release
   unset GITEA_TOKEN GITHUB_TOKEN
   ```

The release task runs checks and builds the APK before publication.
The script rejects dirty trees, unmatched tags, and absent tokens before it publishes to either host.
GoReleaser uploads `gliff-android-debug.apk` and `gliff-android_checksums.txt` to each host.
Credentials stay outside the repository.

For a local run without publication:

```sh
mise run release:snapshot
```

The two hosts are independent.
If the second publication fails, inspect both releases before retrying.
Never remove a published tag to repair an asset.
The `dist/` and `dist-github/` directories contain local release output and are ignored by Git.
