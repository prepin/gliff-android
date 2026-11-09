# Verification record

Date: November 9, 2026.

## Build

```text
testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
BUILD SUCCESSFUL
20 JUnit tests, 0 failures, 0 errors
Android lint: 0 errors, 9 warnings
Python fixture, shortcut-verifier, and release-guard checks: 10 tests, OK
```

Build environment: Linux x86-64, JDK 21, Gradle 8.13, Android Gradle Plugin 8.13.2, Android SDK 36.
The remaining lint warnings concern compatibility attributes, a Gradle update, drawing allocations, XML view constructors, click detection, and untranslated text.
The touch handler calls `performClick()` through its shared motion handler. Lint does not trace that call.

## Release automation

`mise run release:snapshot` passed with GoReleaser 2.20.0 and Java 21.
Both the Forgejo and GitHub configurations passed `goreleaser check` and produced SHA-256 checksums.
The release guards reject dirty trees, mismatched versions, stable tags, and absent credentials.
The Forgejo push mirror uses a repository-only GitHub deploy key.
The initial push reached GitHub automatically, with no mirror error and the same commit on both hosts.

## APK

File: `gliff-android-debug.apk`.
Size: 8,155,482 bytes.
Android minimum: API 26.
Signature: APK Signature Scheme v2, one development signer.
All runtime libraries and application license notices appear in 17 APK asset files.

```text
5acbb8cacb3b7a0ef35bdb703c76eb6fb7199c674f67ce93aaf4cca0b8769a79  gliff-android-debug.apk
```

## Android emulator

Android API 36, x86-64, 1920×1080 display, density 160.
The tests used disposable Ed25519 credentials and a loopback-only SSH server.

```text
PASS Connection form opens
PASS SSH host identity verified
PASS SSH and Gliff handshake succeeds
PASS MediaCodec renders nonblank H.264 video to the desktop
PASS Focus loss releases held Ctrl
PASS Shift+Escape releases capture
PASS Full screen hides the toolbar
PASS Release restores the toolbar
PASS Disconnect returns to the connection form
PASS: 9 device checks

PASS: SSH fixture verified 120 protocol messages:
video ACKs, Super/Enter/Tab, focus-loss release, mouse, scroll, clipboard, Bye
```

The fixture also verified synthesized Alt, both right-click states, pointer coordinates, and the absence of remote Escape presses from the release combination.
These input checks call Android activity handlers. They do not prove interception of Samsung system shortcuts.

## Changed host key

The second SSH server used a different key at the same address.
The app rejected it before authentication, without an approval dialog.

```text
PASS Connection form opens
PASS Changed SSH host key is rejected before authentication
PASS: 2 device checks
```

## Original Gliff server

Server: installed upstream `gliff-server 0.4.2`.
Transport: SSH exec channel.
Server mode: private desktop, CPU encoding, shared-memory capture, Single420 H.264.
Observed stream: 1920×924.
The check sent no keyboard or mouse input to the host.
The private output disappeared after disconnect.

```text
PASS Connection form opens
PASS SSH host identity verified
PASS SSH and Gliff handshake succeeds
PASS MediaCodec renders nonblank H.264 video to the desktop
PASS Disconnect returns to the connection form
PASS: 5 device checks
```

Screenshot: [upstream-desktop.png](upstream-desktop.png).

## XKB layouts

The host `xkbcli` compiler accepted keymaps from the compiled Java generator:

```text
PASS XKB compilation: us/
PASS XKB compilation: gb/
PASS XKB compilation: de/nodeadkeys
PASS XKB compilation: fr/
PASS XKB compilation: cz/qwerty
```

## Physical Galaxy S26

Device: Samsung SM-S942B, Android 17, API 37, build S942BXXU5BZJA.
These checks used the phone display, not DeX.
An ADB reverse tunnel connected the phone to a loopback-only SSH server.
The temporary tunnel and SSH server stopped after each check.

```text
PASS: 22 device checks
PASS: 10 system-dispatched shortcut chords reached the SSH peer intact
PASS: SSH fixture verified 165 protocol messages
PASS: Changed SSH host key rejected before authentication (2 checks)
PASS: Original gliff-server connection and video output (6 checks)
```

Android granted `android.permission.CAPTURE_KEYBOARD`.
The capture request was active during the system-dispatch checks.
Each complete chord reached the SSH fixture with the correct modifier releases:

- Super+Enter and Super+Space.
- Alt+Tab and Alt+Shift+Tab.
- Ctrl+Shift+C.
- Super+1 and Super+Shift+1.
- Super+Up and Super+Ctrl+Right.
- Super+F1.

The [device log](galaxy-decoder.log) identified the decoder as `c2.exynos.h264.decoder`.
The fixture received 89 decoded-frame acknowledgments, at an observed cadence of 31.7 frames per second.
That cadence is not an end-to-end latency measurement.

Clipboard sharing stayed disabled on the phone.
The fixture recorded zero nonempty clipboard offers and zero clipboard data transmissions.
The upstream check negotiated a 1080×1956 stream from a private output.
That new private output was empty except for its cursor.

The fixture screenshot is [galaxy-video.png](galaxy-video.png).
Detailed results are in [galaxy-fixture-check.log](galaxy-fixture-check.log),
[galaxy-changed-host-check.log](galaxy-changed-host-check.log), and
[galaxy-upstream-check.log](galaxy-upstream-check.log).

## Actual DeX external display

Device: the same Galaxy S26, connected to an LG UltraFine display.
DeX display ID: 16. Display mode: 3840×2160 at 30 Hz.
ADB used an authorized wireless debugging connection.
The instrumentation explicitly launched Gliff on display 16 and verified that display ID.
Window screenshots capture Gliff itself, not the phone screen.

```text
PASS: 27 DeX fixture checks
PASS: 10 system-dispatched shortcut chords reached the SSH peer intact
PASS: SSH fixture verified 228 protocol messages
PASS: Changed SSH host key rejected on DeX (3 checks)
PASS: Original gliff-server on DeX (16 checks)
```

The DeX checks included system-dispatched mouse clicks and vertical scroll.
The SSH peer received complete left-click and right-click press/release sequences.
All ten previously listed keyboard chords also reached the SSH peer intact on the external display.
No local clipboard text was offered or sent.

The window changed from 1600 pixels wide to 1280 pixels wide without reconnecting.
It then filled the external display and returned to the smaller size, with the same SSH session.
The original server reported private desktop dimensions that matched the current viewport at every stage.
System-dispatched Shift+Esc restored the Gliff controls and released input.

Two device-testing changes are included in the current APK:

- Mouse gestures without button masks now produce complete clicks and drags, with cleanup after cancel or focus loss.
- The toolbar button is named **Hide controls**. Native DeX controls own window maximization and sizing.

The size changes in the automated test used Android task-resize commands.
They do not establish that an app-level fullscreen API changes Samsung DeX window modes.
The test no longer makes that claim.

Evidence:

- [DeX fixture log](dex-fixture-check.log).
- [DeX upstream-server log](dex-upstream-check.log).
- [DeX changed-host-key log](dex-changed-host-check.log).
- [Display-filling video test screenshot](dex-fullscreen.png).
- [Restored-window screenshot](dex-window.png).

## Remaining manual checks

No physical keyboard or mouse was attached, as agreed for this test session.
Physical USB/Bluetooth input, every possible custom shortcut, and end-to-end latency remain unverified.
The app cannot guarantee that every shortcut reaches Omarchy.
The manual procedure is in [docs/dex-checklist.md](../docs/dex-checklist.md).

This version also omits Dual420/AVC444, file and image clipboard transfers, audio, tabs, and automatic reconnection.
