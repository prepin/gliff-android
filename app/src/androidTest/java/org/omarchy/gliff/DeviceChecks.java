package org.omarchy.gliff;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Point;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.Base64;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Real-device checks without a test framework dependency. Test credentials are arguments, never production assets. */
public final class DeviceChecks extends Instrumentation {
    private Bundle args;
    private MainActivity activity;
    private int checks;

    @Override public void onCreate(Bundle arguments) { args = arguments; start(); }

    @Override public void onStart() {
        Bundle results = new Bundle();
        try {
            Intent intent = new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            int display = Integer.parseInt(args.getString("display", "-1"));
            if (display >= 0 && Build.VERSION.SDK_INT >= 28) {
                activity = (MainActivity) startActivitySync(intent, ActivityOptions.makeBasic().setLaunchDisplayId(display)
                        .setLaunchBounds(new Rect(160, 100, 1760, 1000)).toBundle());
            } else activity = (MainActivity) startActivitySync(intent);
            waitForIdleSync();
            int actualDisplay = activity.getWindowManager().getDefaultDisplay().getDisplayId();
            check(display < 0 || actualDisplay == display, "Activity is on requested display: " + actualDisplay);
            check(findText("Connect") != null, "Connection form opens");
            String identity = args.getString("identity");
            if (identity == null) throw new AssertionError("Pass -e identity with a disposable test SSH key (Base64)");
            runOnMainSync(() -> {
                try {
                    setField("privateKey", Base64.getDecoder().decode(identity));
                    edit("SSH host").setText(args.getString("host", "10.0.2.2"));
                    edit("SSH username").setText(args.getString("user"));
                    edit("SSH port").setText(args.getString("port", "22222"));
                    ((CheckBox) findContaining("Private desktop")).setChecked(true);
                    ((CheckBox) findContaining("Share text clipboard")).setChecked(Boolean.parseBoolean(args.getString("shareClipboard", "true")));
                    ((Button) findText("Connect")).performClick();
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            if (!"true".equals(args.getString("rejectChanged")))
                await(() -> getField("session") != null, 15000,
                        "Display surface available (device must be awake, unlocked, and Gliff visible)");
            if ("true".equals(args.getString("rejectChanged"))) {
                await(() -> {
                    AlertDialog dialog = (AlertDialog) getField("trustDialog");
                    if (dialog != null && dialog.isShowing()) throw new AssertionError("Changed host key must not offer an approval dialog");
                    return findContaining("HostKey has been changed") != null;
                }, 30000, "Changed SSH host key is rejected before authentication");
                results.putString("stream", "PASS: " + checks + " device checks\n");
                finish(Activity.RESULT_OK, results);
                return;
            }
            await(() -> {
                AlertDialog dialog = (AlertDialog) getField("trustDialog");
                if (dialog != null && dialog.isShowing()) {
                    View message = dialog.findViewById(android.R.id.message);
                    String expected = args.getString("fingerprint");
                    if (expected == null || !((TextView) message).getText().toString().contains(expected))
                        throw new AssertionError("Unexpected SSH fingerprint");
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                    return true;
                }
                return findContaining("Connected ·") != null;
            }, 30000, "SSH host identity verified");
            await(() -> findContaining("Connected ·") != null, 30000, "SSH and Gliff handshake succeeds");
            await(() -> {
                TextureView texture = (TextureView) findClass(activity.getWindow().getDecorView(), TextureView.class);
                if (texture == null || !texture.isAvailable()) return false;
                Bitmap bitmap = texture.getBitmap(64, 64);
                if (bitmap == null) return false;
                HashSet<Integer> colors = new HashSet<>();
                for (int y = 0; y < 64; y += 4) for (int x = 0; x < 64; x += 4) colors.add(bitmap.getPixel(x, y));
                bitmap.recycle();
                return colors.size() > 8;
            }, 20000, "MediaCodec renders nonblank H.264 video to the desktop");

            if (!"false".equals(args.getString("exerciseInput", "true"))) {
                if ("true".equals(args.getString("systemInput"))) {
                    systemInputChecks();
                    if (display >= 0) systemPointerChecks(display);
                }
                runOnMainSync(() -> {
                    ((Button) findText("Capture")).performClick();
                    key(117, 125, true, KeyEvent.META_META_ON);
                    key(66, 28, true, KeyEvent.META_META_ON);
                    key(66, 28, false, KeyEvent.META_META_ON);
                    key(117, 125, false, 0);
                    key(61, 15, true, KeyEvent.META_ALT_ON); // raw Tab must not navigate the local UI
                    key(61, 15, false, 0);
                    DesktopView view = (DesktopView) getField("desktop");
                    pointer(view, MotionEvent.ACTION_HOVER_MOVE, 0, 0);
                    pointer(view, MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.BUTTON_SECONDARY, 0);
                    pointer(view, MotionEvent.ACTION_BUTTON_RELEASE, 0, 0);
                    pointer(view, MotionEvent.ACTION_SCROLL, 0, 1);
                    key(113, 29, true, KeyEvent.META_CTRL_ON);
                    activity.onWindowFocusChanged(false); // Releases a held modifier and pointer capture.
                    check(!((InputState) getField("input")).held(29), "Focus loss releases held Ctrl");
                    activity.onWindowFocusChanged(true);
                    ((Button) findText("Capture")).performClick();
                    key(59, 42, true, KeyEvent.META_SHIFT_ON);
                    key(111, 1, true, KeyEvent.META_SHIFT_ON);
                    key(111, 1, false, KeyEvent.META_SHIFT_ON);
                    key(59, 42, false, 0);
                    check(!(Boolean) getField("grabbed"), "Shift+Escape releases capture");
                    if (display < 0) {
                        ((Button) findText("Hide controls")).performClick();
                        check(((View) getField("toolbarContainer")).getVisibility() == View.GONE, "Hide controls removes the toolbar");
                        key(59, 42, true, KeyEvent.META_SHIFT_ON);
                        key(111, 1, true, KeyEvent.META_SHIFT_ON);
                        key(111, 1, false, KeyEvent.META_SHIFT_ON);
                        check(((View) getField("toolbarContainer")).getVisibility() == View.VISIBLE, "Release restores the toolbar");
                    }
                });
            }
            if (display >= 0) desktopWindowChecks();
            SystemClock.sleep(1500);
            screenshot("device-check.png");
            runOnMainSync(() -> ((Button) findText("Disconnect")).performClick());
            check(findText("Connect") != null, "Disconnect returns to the connection form");
            results.putString("stream", "PASS: " + checks + " device checks\n");
            finish(Activity.RESULT_OK, results);
        } catch (Throwable error) {
            if (activity != null) runOnMainSync(() -> {
                StringBuilder ui = new StringBuilder();
                ui.append("window=").append(activity.getWindow().getDecorView().getWidth()).append('x')
                        .append(activity.getWindow().getDecorView().getHeight()).append(" multiWindow=")
                        .append(activity.isInMultiWindowMode()).append(" | ");
                describe(activity.getWindow().getDecorView(), ui);
                Bundle update = new Bundle(); update.putString("stream", "Gliff UI at failure: " + ui + "\n"); sendStatus(0, update);
            });
            for (java.util.Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
                if (!entry.getKey().getName().startsWith("gliff-")) continue;
                Bundle update = new Bundle(); update.putString("stream", entry.getKey().getName() + ": "
                        + java.util.Arrays.toString(entry.getValue()) + "\n"); sendStatus(0, update);
            }
            results.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, results);
        }
    }

    private void systemPointerChecks(int display) throws Exception {
        int[] center = new int[2];
        runOnMainSync(() -> {
            ((Button) findText("Capture")).performClick();
            DesktopView view = (DesktopView) getField("desktop");
            view.getLocationOnScreen(center);
            center[0] += view.getWidth() / 2;
            center[1] += view.getHeight() / 2;
        });
        shell("input mouse -d " + display + " tap " + center[0] + " " + center[1]);
        shell("input mouse -d " + display + " scroll " + center[0] + " " + center[1] + " --axis VSCROLL,1");
        waitForIdleSync();
        runOnMainSync(() -> {
            check(activity.hasWindowFocus(), "System pointer events stayed on the DeX window");
            ((Button) findText("Release")).performClick();
        });
    }

    private void shell(String command) throws Exception {
        try (java.io.InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(
                getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).executeShellCommand(command))) {
            byte[] buffer = new byte[1024];
            while (in.read(buffer) != -1) { }
        }
    }

    private void desktopWindowChecks() throws Exception {
        int[] initial = new int[2];
        Object connection = getField("session");
        runOnMainSync(() -> {
            initial[0] = activity.getWindow().getDecorView().getWidth();
            initial[1] = activity.getWindow().getDecorView().getHeight();
        });
        int width = initial[0] > 1400 ? 1280 : 1600;
        shell("am task resize " + activity.getTaskId() + " 160 100 " + (160 + width) + " 1000");
        await(() -> Math.abs(activity.getWindow().getDecorView().getWidth() - width) < 32
                        && getField("session") == connection,
                7000, "DeX window resized without disconnect: " + initial[0] + "px to " + width + "px");
        remoteSizeCheck();
        SystemClock.sleep(400);
        Point screen = new Point();
        activity.getWindowManager().getDefaultDisplay().getRealSize(screen);
        shell("am task resize " + activity.getTaskId() + " 0 0 " + screen.x + " " + screen.y);
        await(() -> activity.getWindow().getDecorView().getWidth() >= screen.x * 0.95
                        && activity.getWindow().getDecorView().getHeight() >= screen.y * 0.90
                        && getField("session") == connection,
                7000, "DeX window fills the external display without disconnect");
        remoteSizeCheck();
        waitForIdleSync();
        SystemClock.sleep(250);
        runOnMainSync(() -> ((Button) findText("Hide controls")).performClick());
        await(() -> ((View) getField("toolbarContainer")).getVisibility() == View.GONE
                        && getField("session") == connection,
                7000, "Hide controls removes the toolbar on the DeX display");
        remoteSizeCheck();
        waitForIdleSync();
        SystemClock.sleep(150);
        screenshot("fullscreen-check.png");
        injectKey(KeyEvent.KEYCODE_ESCAPE, true, KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON);
        injectKey(KeyEvent.KEYCODE_ESCAPE, false, 0);
        await(() -> ((View) getField("toolbarContainer")).getVisibility() == View.VISIBLE
                        && !(Boolean) getField("grabbed") && getField("session") == connection,
                7000, "System-dispatched Shift+Escape restores controls and releases DeX input");
        shell("am task resize " + activity.getTaskId() + " 160 100 " + (160 + width) + " 1000");
        await(() -> Math.abs(activity.getWindow().getDecorView().getWidth() - width) < 32
                        && ((View) getField("toolbarContainer")).getVisibility() == View.VISIBLE
                        && getField("session") == connection,
                7000, "DeX window restores to its previous size without disconnect");
        remoteSizeCheck();
    }

    private void remoteSizeCheck() throws Exception {
        if (!"false".equals(args.getString("exerciseInput", "true"))) return;
        Field width = DesktopView.class.getDeclaredField("viewW"), height = DesktopView.class.getDeclaredField("viewH");
        width.setAccessible(true); height.setAccessible(true);
        await(() -> {
            DesktopView view = (DesktopView) getField("desktop");
            if (view == null) return false;
            double fit = Math.min(1.0, Math.min(3840.0 / view.getWidth(), 2160.0 / view.getHeight()));
            try {
                return Math.abs(width.getInt(view) - Math.max(640, (int) (view.getWidth() * fit))) <= 4
                        && Math.abs(height.getInt(view) - Math.max(480, (int) (view.getHeight() * fit))) <= 4;
            } catch (IllegalAccessException e) { throw new RuntimeException(e); }
        }, 20000, "Private remote desktop follows the DeX viewport dimensions");
    }

    private void screenshot(String name) throws Exception {
        UiAutomation automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        Bitmap image = Build.VERSION.SDK_INT >= 34 ? automation.takeScreenshot(activity.getWindow()) : automation.takeScreenshot();
        if (image == null) throw new AssertionError("Screenshot could not capture Gliff's window");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(
                new java.io.File(getTargetContext().getExternalFilesDir(null), name))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally { image.recycle(); }
    }

    private void describe(View view, StringBuilder out) {
        if (out.length() > 4096) return;
        if (view instanceof TextView && !(view instanceof EditText)) out.append(((TextView) view).getText()).append(" | ");
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            describe(((ViewGroup) view).getChildAt(i), out);
    }

    private void systemInputChecks() throws Exception {
        runOnMainSync(() -> ((Button) findText("Capture")).performClick());
        await(activity::hasWindowFocus, 5000, "App has focus before system input injection");
        runOnMainSync(() -> {
            try {
                boolean requested = (Boolean) activity.getWindow().getAttributes().getClass()
                        .getMethod("hasKeyboardCapture").invoke(activity.getWindow().getAttributes());
                check(requested && activity.checkSelfPermission("android.permission.CAPTURE_KEYBOARD") == 0,
                        "Android keyboard capture requested with permission granted");
            } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        });
        int[][] chords = {{117,66}, {117,62}, {57,61}, {57,59,61}, {113,59,31},
                {117,8}, {117,59,8}, {117,19}, {117,113,22}, {117,131}};
        for (int[] chord : chords) {
            runOnMainSync(() -> {
                if (!activity.hasWindowFocus() || !(Boolean) getField("grabbed"))
                    throw new AssertionError("System input escaped Gliff before the next test chord");
            });
            int meta = 0;
            for (int code : chord) {
                meta |= modifier(code);
                injectKey(code, true, meta);
            }
            for (int i = chord.length - 1; i >= 0; i--) {
                meta &= ~modifier(chord[i]);
                injectKey(chord[i], false, meta);
            }
            waitForIdleSync();
            runOnMainSync(() -> check(activity.hasWindowFocus() && (Boolean) getField("grabbed"),
                    "System-dispatched chord stayed in Gliff: " + java.util.Arrays.toString(chord)));
        }
        runOnMainSync(() -> ((Button) findText("Release")).performClick());
    }

    private static int modifier(int code) {
        switch (code) {
            case 117: return KeyEvent.META_META_ON | KeyEvent.META_META_LEFT_ON;
            case 113: return KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
            case 57: return KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
            case 59: return KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
            default: return 0;
        }
    }

    private void injectKey(int code, boolean down, int meta) {
        long now = SystemClock.uptimeMillis();
        KeyEvent event = new KeyEvent(now, now, down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP,
                code, 0, meta, -1, 0, 0, InputDevice.SOURCE_KEYBOARD);
        if (!getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).injectInputEvent(event, true))
            throw new AssertionError("Android rejected injected key " + code);
    }

    private void key(int androidCode, int scan, boolean down, int meta) {
        long now = SystemClock.uptimeMillis();
        activity.dispatchKeyEvent(new KeyEvent(now, now, down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP,
                androidCode, 0, meta, -1, scan, 0, InputDevice.SOURCE_KEYBOARD));
    }

    private void pointer(DesktopView view, int action, int buttons, float scroll) {
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0; properties.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = view.getWidth() / 2f; coords.y = view.getHeight() / 2f;
        coords.setAxisValue(MotionEvent.AXIS_VSCROLL, scroll);
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, 1,
                new MotionEvent.PointerProperties[]{properties}, new MotionEvent.PointerCoords[]{coords},
                0, buttons, 1, 1, -1, 0, InputDevice.SOURCE_MOUSE, 0);
        view.onGenericMotionEvent(event); event.recycle();
    }

    private void await(BooleanSupplier condition, long timeout, String description) {
        long deadline = SystemClock.uptimeMillis() + timeout;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<Boolean> ok = new AtomicReference<>(false);
            runOnMainSync(() -> ok.set(condition.getAsBoolean()));
            if (ok.get()) { check(true, description); return; }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out: " + description);
    }

    private void check(boolean passed, String description) {
        if (!passed) throw new AssertionError(description);
        checks++;
        Bundle update = new Bundle(); update.putString("stream", "PASS " + description + "\n"); sendStatus(0, update);
    }

    private EditText edit(String description) { return (EditText) findDescription(activity.getWindow().getDecorView(), description); }
    private View findText(String text) { return find(activity.getWindow().getDecorView(), text, false); }
    private View findContaining(String text) { return find(activity.getWindow().getDecorView(), text, true); }
    private View find(View root, String text, boolean contains) {
        if (root instanceof TextView) {
            String value = ((TextView) root).getText().toString();
            if (contains ? value.contains(text) : value.equals(text)) return root;
        }
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View found = find(((ViewGroup) root).getChildAt(i), text, contains);
            if (found != null) return found;
        }
        return null;
    }
    private View findDescription(View root, String description) {
        if (description.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View found = findDescription(((ViewGroup) root).getChildAt(i), description); if (found != null) return found;
        }
        return null;
    }
    private View findClass(View root, Class<?> type) {
        if (type.isInstance(root)) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View found = findClass(((ViewGroup) root).getChildAt(i), type); if (found != null) return found;
        }
        return null;
    }
    private Object getField(String name) {
        try { Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    private void setField(String name, Object value) throws Exception {
        Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); f.set(activity, value);
    }
}
