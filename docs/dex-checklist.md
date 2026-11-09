# Samsung DeX hardware checks

These checks need a physical Samsung device, an external display, a keyboard, and a mouse.
The Android emulator cannot establish Samsung shortcut behavior.

## Record the environment

- Samsung model:
- Android version:
- One UI version:
- Wired or wireless DeX:
- Keyboard model and connection:
- Keyboard layout on Android and Omarchy:
- Mouse model and connection:
- Remote Gliff version:
- Display resolution and refresh rate:

## Connection and display

1. Connect through SSH with a verified fingerprint.
2. Open a terminal in a private desktop.
3. Resize the DeX window.
4. Verify that the private desktop follows the window size.
5. Maximize Gliff with the native DeX window controls.
6. Select **Hide controls** in Gliff.
7. Press Shift+Esc.
8. Verify that the toolbar and local input return.
9. Reconnect in mirror mode.
10. Verify that the image keeps its aspect ratio.
11. Move Gliff between the tablet display and the external display.
12. Verify that the connection stops safely or continues without a black frame.

## Keyboard

For each combination, record the remote action and any local DeX action.
Repeat Super combinations with Caps Lock → Super enabled.
Remote custom bindings can differ from these common defaults.

| Input | Expected remote result | Physical Super | Caps → Super |
|---|---|---|---|
| Super+Enter | Terminal | Pending | Pending |
| Super+Space | Launcher | Pending | Pending |
| Super+1 through Super+0 | Workspace selection | Pending | Pending |
| Super+Shift+1 through Super+Shift+0 | Move window | Pending | Pending |
| Super+arrow keys | Directional action | Pending | Pending |
| Super+Shift+arrow keys | Directional move | Pending | Pending |
| Super+Ctrl+arrow keys | Configured remote action | Pending | Pending |
| Super+F | Full screen | Pending | Pending |
| Super+Shift+Q | Close window | Pending | Pending |
| Alt+Tab and Alt+Shift+Tab | Window selection | Pending | Not applicable |
| Ctrl+C, Ctrl+V, Ctrl+Shift+C/V | Copy and paste | Pending | Not applicable |
| Ctrl+Alt+Delete | Configured remote action | Pending | Not applicable |
| F1–F12, Home, End, Page Up/Down | Application input | Pending | Not applicable |
| Right Alt and non-US punctuation | Correct layout behavior | Pending | Not applicable |
| Numpad, Num Lock, Caps Lock | Correct input and lock state | Pending | Not applicable |
| Shift+Esc | Local release only | Pending | Pending |

## Input safety

1. Hold Ctrl and switch to another DeX app.
2. Return to Gliff.
3. Verify that Ctrl is not held remotely.
4. Repeat this check with Super, Alt, Shift, and a mouse button.
5. Disconnect the keyboard during a held key.
6. Verify that input is not stuck remotely.
7. Interrupt the network connection.
8. Verify that the app reports the error without a frozen Android interface.
9. Reconnect with the same host key.
10. Verify that the app does not request fingerprint approval again.

## Mouse and clipboard

1. Verify left, middle, right, back, and forward buttons.
2. Verify vertical and horizontal scroll.
3. Verify pointer alignment at all four edges and across letterbox borders.
4. Drag a remote window while holding Super.
5. Enable clipboard sharing with a trusted test host.
6. Copy Unicode text in each direction.
7. Verify that a marked-sensitive Android clip does not reach the host.
8. Disable clipboard sharing.
9. Verify that subsequent local text is not offered remotely.

## Release gate

A release needs recorded results for the target device and firmware.
A claim that every keybinding works requires these results, not only forwarded-event tests.
