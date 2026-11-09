# Gliff for Android

A native Android client for [Gliff](https://github.com/omacom/gliff), focused on Samsung DeX.
The app connects to an existing Omarchy session through SSH.
The remote host needs the original `gliff-server`, without modifications.

**Status: development build, tested on a Galaxy S26.**
Emulator and Galaxy S26 checks cover SSH, video decoding, and input forwarding.
Ten shortcut chords passed through Android input dispatch on the Galaxy.
DeX external-display checks also exercise window resizing and system-dispatched input.
Physical USB and Bluetooth keyboards and mice remain untested.

## Current features

- SSH password authentication and imported private keys, including Ed25519.
- SHA-256 host fingerprints, saved host keys, and rejection of changed host keys.
- Private desktops that follow the window size, or a mirror of a remote output.
- Hardware-capable H.264 decoding through Android MediaCodec, with frame acknowledgments for upstream rate control.
- Physical Linux keycodes for Super, Ctrl, Alt, Shift, function keys, navigation keys, and key combinations.
- Samsung Meta-key capture requests and Android 16 QPR2 keyboard-capture requests.
- An optional Caps Lock → Super mapping for shortcuts that DeX intercepts.
- A selectable XKB layout and variant, compiled on the remote host.
- Mouse movement, five buttons, horizontal and vertical scroll, and touch-to-click.
- Pointer capture, remote cursor shapes, hideable controls, and release of held input after focus loss.
- Opt-in text clipboard sharing, with a 1 MiB limit and rejection of marked-sensitive clips.

## Install

Download the APK and checksums from the [Forgejo releases](https://git.prepin.net/prepin/gliff-android/releases).
The same files appear on [GitHub releases](https://github.com/prepin/gliff-android/releases).
The APK is debuggable and uses a development signature, not a production release signature.

With Android platform tools:

```sh
adb install -r artifacts/gliff-android-debug.apk
```

Alternatively, copy the APK to the Samsung device and open it from the file manager.
Android requires permission to install apps from that source.

## Connect from DeX

1. Install the original Gliff package on the Omarchy host.

   ```sh
   omarchy pkg add gliff
   ```

2. Log in to Hyprland on that host.
3. Verify that SSH access works from another machine.
4. Open **Gliff** in DeX.
5. Enter the SSH host, username, and port in separate fields.
6. Enter the SSH password, or import a private key.
7. For an encrypted private key, enter its passphrase in the password field.
8. Select a private desktop or a mirror.
9. Select the XKB layout that matches your physical keyboard.
10. Select **Connect**.
11. Compare the displayed fingerprint through a separate trusted channel.
12. After the fingerprint matches, select **Trust and connect**.
13. Click the picture or select **Capture**.

A new private desktop can initially be empty.
Click its picture before the first keyboard shortcut.
Super+Enter, or the terminal entry in **Keys…**, opens an application.
For the existing desktop and its open applications, disable **Private desktop** and use mirror mode.

The app starts this command through an SSH exec channel:

```text
gliff-server --stdio --low-bandwidth --headless
```

Mirror mode replaces `--headless` with `--output 'auto'`, or the selected output name.
The app never opens an unauthenticated desktop port.

## Keyboard and mouse

**Shift+Esc** releases the keyboard and mouse.
Release also restores the toolbar after **Hide controls**.
The release combination stays local and does not reach Omarchy.

For a display-filling desktop, maximize Gliff with the native DeX window controls.
Then select **Hide controls** to remove the Gliff toolbar.
Shift+Esc restores the toolbar but does not resize the DeX window.
Gliff leaves window sizing to the native DeX controls.

**Caps Lock → Super** provides an alternative to the physical Super/Windows key.
The **Super** button holds Super until the next click or input release.
The **Keys…** menu sends selected combinations directly, including Alt+Tab.
The menu uses common default combinations, not the remote list of custom bindings.

Physical scan codes preserve key positions.
The selected XKB layout controls the resulting characters.
The app sends that keymap to Gliff without changes to the host keyboard configuration.
Layout detection from Android is not automatic.

### Important shortcut limit

An Android app cannot guarantee access to every system shortcut.
Samsung firmware can intercept combinations before the app receives them.
Android always reserves some system actions, even with keyboard capture.
The Samsung capture hook is not a stable public API.

For a combination that stays local, use Caps Lock → Super or the **Keys…** menu.
The hardware checklist is in [docs/dex-checklist.md](docs/dex-checklist.md).
The current implementation does **not** establish that all DeX keybindings work.

## Security and privacy

- Passwords and imported keys stay in memory, not application preferences or backups.
- The app remembers the last host, username, output, and connection choices.
- The app saves trusted host keys in its private storage.
- A changed host key stops the connection. The app does not offer automatic replacement.
- Clipboard sharing is disabled by default. An enabled host can request the offered text without a local paste.
- Android needs eager retrieval of remote text. A remote clipboard offer can replace the Android clipboard.
- Clips marked sensitive are not shared. Unmarked secrets cannot be detected reliably.
- Android can reject large clipboard transactions. The app reports that error without replacing the existing clipboard.
- The app includes no analytics, relay service, or credential logging.

## Build and tests

Requirements: JDK 21 and Android SDK platform 36.
Gradle installs the required build tools after SDK license acceptance.

```sh
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
```

The APK appears at `app/build/outputs/apk/debug/app-debug.apk`.
Detailed device checks are in [docs/testing.md](docs/testing.md).
The verification record is in [artifacts/verification.md](artifacts/verification.md).
The [release guide](docs/releases.md) covers mise tasks, GoReleaser, and the GitHub mirror.

## Deliberate limits

- One connection at a time. No tabs, saved password vault, SSH agent, or SSH config import.
- No automatic reconnection. A lost connection releases input and returns to the connection form.
- H.264 Single420 only. Colored text has less color detail than upstream Dual420/AVC444.
- Video is limited to 3840×2160 pixels, with a maximum dimension of 4096 pixels and a 32 MiB frame limit.
- Clipboard support covers text only. No file transfer, images, or shared audio.
- External keyboards are the primary input method. No remote software keyboard or IME composition.
- Physical USB/Bluetooth keyboards and mice, and end-to-end latency, still need manual checks.

## License

MIT. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
