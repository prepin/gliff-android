# Development preview

This APK uses a development signature and permits Android debugging.
Do not treat this preview as a production security release.
Different development signing keys require an uninstall before installation, which removes saved preferences and trusted host keys.

## Included

- SSH authentication and host-key verification.
- H.264 Single420 video through Android MediaCodec.
- Private desktops and mirror mode.
- DeX keyboard capture, shortcut forwarding, mouse input, and window resizing.
- Optional text clipboard sharing, disabled by default.

## Verification

- 20 Android unit tests passed.
- 10 Python checks passed, including release guards.
- 27 DeX fixture checks passed on a Galaxy S26 and a 4K LG display.
- 16 checks passed with the original Gliff server on DeX.
- All 10 injected shortcut chords reached the SSH peer intact.
- Changed SSH host keys stopped the connection.
- Android lint reported no errors and nine warnings.

See `artifacts/verification.md` in the repository for logs and screenshots.
The release includes the APK and its SHA-256 checksum.

## Known limits

Physical USB/Bluetooth peripherals and every custom shortcut remain unverified.
Android and DeX can reserve shortcuts before Gliff receives them.
Use the native DeX controls to maximize the window.
Use **Hide controls** to remove the Gliff toolbar.
Press **Shift+Esc** to release input and restore the toolbar.

This preview has no audio, file transfer, automatic reconnection, or AVC444 video.
