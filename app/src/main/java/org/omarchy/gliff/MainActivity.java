package org.omarchy.gliff;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.HashSet;
import java.util.Set;

public final class MainActivity extends Activity implements DesktopView.Listener {
    private final Handler main = new Handler(Looper.getMainLooper());
    private EditText host, user, port, password, output, keyboardLayout, keyboardVariant;
    private CheckBox headless, clipboardOption, capsOption;
    private TextView status, keyLabel;
    private LinearLayout toolbar;
    private View toolbarContainer;
    private DesktopView desktop;
    private Surface surface;
    private volatile GliffSession session;
    private volatile TextClipboard clipboard;
    private ConnectionConfig config;
    private String credential = "";
    private String keymap = "";
    private byte[] privateKey;
    private boolean grabbed, capsToSuper, sharingClipboard, escapeRelease, superLatched;
    private volatile int generation;
    private Button grabButton, superButton;
    private AlertDialog trustDialog;
    private CountDownLatch trustWait;
    private final Set<Integer> syntheticModifiers = new HashSet<>();
    private final InputState input = new InputState((type, fields) -> send(type, fields));
    private final ClipboardManager.OnPrimaryClipChangedListener clipboardChanged = this::offerLocalClipboard;
    private final InputManager.InputDeviceListener devices = new InputManager.InputDeviceListener() {
        @Override public void onInputDeviceAdded(int id) { }
        @Override public void onInputDeviceChanged(int id) { }
        @Override public void onInputDeviceRemoved(int id) { releaseInput(); }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        getSystemService(InputManager.class).registerInputDeviceListener(devices, main);
        showConnectionForm(null);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextSize(size); view.setTextColor(Color.rgb(220, 225, 220));
        return view;
    }

    private EditText field(LinearLayout parent, String name, String value, int type) {
        TextView title = label(name, 13);
        LinearLayout.LayoutParams titleLayout = new LinearLayout.LayoutParams(-1, -2);
        titleLayout.topMargin = dp(12);
        parent.addView(title, titleLayout);
        EditText edit = new EditText(this);
        edit.setSingleLine(true); edit.setInputType(type); edit.setText(value);
        edit.setContentDescription(name);
        edit.setSaveEnabled(false);
        parent.addView(edit, new LinearLayout.LayoutParams(-1, dp(50)));
        title.setLabelFor(View.generateViewId()); edit.setId(title.getLabelFor());
        return edit;
    }

    private Button button(String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text); button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        button.setMinWidth(dp(48)); button.setMinimumHeight(dp(48));
        return button;
    }

    private CheckBox option(LinearLayout parent, String text, boolean checked) {
        CheckBox check = new CheckBox(this);
        check.setText(text); check.setChecked(checked); check.setMinHeight(dp(48));
        parent.addView(check);
        return check;
    }

    private void showConnectionForm(String error) {
        systemBars(false);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        ScrollView scroll = new ScrollView(this);
        FrameLayout frame = new FrameLayout(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(28), dp(24), dp(32));
        FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        frame.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            int width = Math.min(r - l, dp(640));
            if (width > 0 && layout.width != width) { layout.width = width; form.setLayoutParams(layout); }
        });
        frame.addView(form, layout); scroll.addView(frame); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        scroll.requestApplyInsets();
        TextView title = label("Gliff", 36); title.setTextColor(0xffa7c080); form.addView(title);
        form.addView(label("Your Omarchy desktop. Over SSH. On DeX.", 17));
        TextView explanation = label("Run gliff-server on a logged-in Hyprland host. No extra listening port or relay is needed.", 14);
        explanation.setPadding(0, dp(12), 0, dp(8)); form.addView(explanation);
        if (error != null) { TextView message = label(error, 14); message.setTextColor(0xffe67e80); form.addView(message); }
        int plain = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        host = field(form, "SSH host", prefs.getString("host", ""), plain | InputType.TYPE_TEXT_VARIATION_URI);
        user = field(form, "SSH username", prefs.getString("user", ""), plain);
        port = field(form, "SSH port", prefs.getString("port", "22"), InputType.TYPE_CLASS_NUMBER);
        password = field(form, "SSH password / private-key passphrase (not saved)", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        LinearLayout keyRow = new LinearLayout(this);
        keyRow.addView(button("Import SSH key", this::importKey));
        keyRow.addView(button("Clear key", () -> { wipeKey(); keyLabel.setText("Password authentication"); }));
        form.addView(keyRow);
        keyLabel = label(privateKey == null ? "Password authentication" : "SSH key imported for this app session", 13);
        form.addView(keyLabel);
        headless = option(form, "Private desktop · follows the DeX window size", prefs.getBoolean("headless", true));
        output = field(form, "Mirror output (empty = focused output; ignored for private desktop)", prefs.getString("output", ""), plain);
        keyboardLayout = field(form, "Keyboard layout (XKB name: us, gb, de, fr, cz…)", prefs.getString("layout", "us"), plain);
        keyboardVariant = field(form, "Keyboard variant (optional: nodeadkeys, qwerty…)", prefs.getString("variant", ""), plain);
        capsOption = option(form, "Use Caps Lock as Super (avoids DeX shortcut conflicts)", prefs.getBoolean("caps", false));
        clipboardOption = option(form, "Share text clipboard with this trusted host (up to 1 MiB)", prefs.getBoolean("clipboard", false));
        form.addView(label("Choose the layout that matches your physical keyboard. Shift+Esc releases input. Android can reserve shortcuts even in capture mode.", 13));
        form.addView(button("Connect", this::connect), new LinearLayout.LayoutParams(-1, dp(56)));
        form.addView(button("Forget trusted SSH hosts…", () -> new AlertDialog.Builder(this)
                .setTitle("Forget SSH host keys?")
                .setMessage("Every host will require fingerprint verification again. Do this only after verifying why a host key changed.")
                .setNegativeButton("Cancel", null).setPositiveButton("Forget", (d, w) -> {
                    File file = new File(getFilesDir(), "known_hosts");
                    if (file.exists() && !file.delete()) toast("Could not remove saved host keys.");
                    else toast("SSH host keys forgotten.");
                }).show()));
    }

    private void connect() {
        try {
            config = new ConnectionConfig(host.getText().toString(), user.getText().toString(),
                    Integer.parseInt(port.getText().toString()), headless.isChecked(), output.getText().toString());
            keymap = XkbKeymap.create(keyboardLayout.getText().toString().trim(), keyboardVariant.getText().toString().trim());
        } catch (NumberFormatException e) { toast("Enter an SSH port from 1 to 65535."); return; }
        catch (IllegalArgumentException e) { toast(e.getMessage()); return; }
        credential = password.getText().toString();
        password.setText("");
        capsToSuper = capsOption.isChecked(); sharingClipboard = clipboardOption.isChecked();
        getPreferences(MODE_PRIVATE).edit().putString("host", config.host).putString("user", config.user)
                .putString("port", Integer.toString(config.port)).putString("output", config.output)
                .putString("layout", keyboardLayout.getText().toString().trim()).putString("variant", keyboardVariant.getText().toString().trim())
                .putBoolean("headless", config.headless).putBoolean("caps", capsToSuper)
                .putBoolean("clipboard", sharingClipboard).apply();
        showDesktop();
    }

    private void showDesktop() {
        generation++;
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        toolbar = new LinearLayout(this); toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(0xff232a2e);
        android.widget.HorizontalScrollView barScroll = new android.widget.HorizontalScrollView(this);
        barScroll.setHorizontalScrollBarEnabled(false); barScroll.setFillViewport(true);
        toolbar.setPadding(dp(8), 0, dp(8), 0);
        TextView brand = label("Gliff  ·  " + config.host, 16); brand.setTextColor(0xffa7c080);
        toolbar.addView(brand);
        grabButton = button("Capture", () -> setGrabbed(!grabbed)); toolbar.addView(grabButton);
        superButton = button("Super", () -> {
            if (session == null) return;
            if (!grabbed) setGrabbed(true);
            superLatched = !superLatched; input.key(125, superLatched);
            superButton.setText(superLatched ? "Super ●" : "Super");
            desktop.requestFocus();
        }); toolbar.addView(superButton);
        toolbar.addView(button("Keys…", this::specialKeys));
        toolbar.addView(button("Hide controls", () -> {
            toolbarContainer.setVisibility(View.GONE); status.setVisibility(View.GONE);
            systemBars(true); setGrabbed(true);
        }));
        toolbar.addView(button("Disconnect", () -> disconnect(null)));
        barScroll.addView(toolbar); root.addView(barScroll, new LinearLayout.LayoutParams(-1, dp(52)));
        toolbarContainer = barScroll;
        status = label("Connecting…", 12); status.setPadding(dp(12), dp(3), dp(12), dp(3));
        root.addView(status);
        desktop = new DesktopView(this, this);
        root.addView(desktop, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(), 0);
        desktop.requestFocus();
    }

    @Override public void surfaceReady(Surface surface) {
        this.surface = surface;
        if (session == null && config != null) startSession();
    }

    private void startSession() {
        int token = generation;
        DesktopView target = desktop;
        GliffSession next = new GliffSession(surface, new GliffSession.Listener() {
            @Override public void status(String text) { main.post(() -> { if (token == generation) status.setText(text); }); }
            @Override public void configured(int w, int h, int vw, int vh, int scale) {
                main.post(() -> { if (token == generation) target.configure(w, h, vw, vh, scale); });
            }
            @Override public void cursor(Protocol.Frame frame) throws java.io.IOException {
                if (token == generation) target.cursor(frame);
            }
            @Override public void clipboard(Protocol.Frame frame) throws java.io.IOException {
                TextClipboard bridge = clipboard;
                if (token == generation && bridge != null) bridge.handle(frame);
            }
            @Override public void ended(String reason) {
                main.post(() -> { if (token == generation) disconnected(reason); });
            }
        });
        session = next;
        clipboard = new TextClipboard((type, fields, data) -> next.sendPayload(type, fields, data),
                text -> main.post(() -> {
                    if (token != generation || !hasWindowFocus()) return;
                    try {
                        getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Gliff remote", text));
                    } catch (RuntimeException e) {
                        toast("Android rejected the clipboard text. The existing clipboard was not changed.");
                    }
                }), message -> main.post(() -> { if (token == generation) toast(message); }));
        clipboard.enable(sharingClipboard);
        getSystemService(ClipboardManager.class).addPrimaryClipChangedListener(clipboardChanged);
        next.start(config, credential, privateKey, new File(getFilesDir(), "known_hosts"), this::approveHost,
                keymap, desktop.getWidth(), desktop.getHeight());
        if (sharingClipboard) offerLocalClipboard();
    }

    private boolean approveHost(String hostname, String algorithm, String fingerprint) throws InterruptedException {
        CountDownLatch wait = new CountDownLatch(1);
        AtomicBoolean approved = new AtomicBoolean();
        int token = generation;
        main.post(() -> {
            if (token != generation || isFinishing() || isDestroyed()) { wait.countDown(); return; }
            trustWait = wait;
            trustDialog = new AlertDialog.Builder(this).setTitle("Verify SSH host key")
                    .setMessage(hostname + "\n" + algorithm + "\n\n" + fingerprint
                            + "\n\nCompare this fingerprint with the host's SSH key through a separate trusted channel before accepting.")
                    .setNegativeButton("Reject", (d, w) -> wait.countDown())
                    .setPositiveButton("Trust and connect", (d, w) -> { approved.set(true); wait.countDown(); })
                    .setOnCancelListener(d -> wait.countDown()).show();
        });
        boolean completed = wait.await(90, TimeUnit.SECONDS);
        main.post(() -> { if (trustWait == wait) { if (trustDialog != null) trustDialog.dismiss(); trustWait = null; } });
        return completed && approved.get() && token == generation;
    }

    private void offerLocalClipboard() {
        TextClipboard bridge = clipboard;
        if (bridge == null || !sharingClipboard || !hasWindowFocus()) return;
        ClipboardManager manager = getSystemService(ClipboardManager.class);
        ClipData clip = manager.getPrimaryClip();
        boolean sensitive = clip != null && clip.getDescription().getExtras() != null
                && clip.getDescription().getExtras().getBoolean("android.content.extra.IS_SENSITIVE", false);
        CharSequence text = clip != null && clip.getItemCount() > 0 ? clip.getItemAt(0).getText() : null;
        bridge.localChanged(text == null ? null : text.toString(), sensitive);
    }

    private void specialKeys() {
        setGrabbed(false);
        String[] names = {"Super + Enter · terminal", "Super + Space · launcher", "Super + 1 · workspace 1",
                "Super + Shift + Q · close window", "Alt + Tab", "Ctrl + Alt + Delete", "Escape", "Toggle Caps → Super"};
        int[][] keys = {{125, 28}, {125, 57}, {125, 2}, {125, 42, 16}, {56, 15}, {29, 56, 111}, {1}};
        new AlertDialog.Builder(this).setTitle("Send keys to Omarchy").setItems(names, (d, which) -> {
            if (which == keys.length) {
                capsToSuper = !capsToSuper;
                toast(capsToSuper ? "Caps Lock sends Super" : "Caps Lock sends Caps Lock");
            } else {
                for (int key : keys[which]) input.key(key, true);
                for (int i = keys[which].length - 1; i >= 0; i--) input.key(keys[which][i], false);
            }
            setGrabbed(true);
        }).setNegativeButton("Cancel", null).show();
    }

    private void setGrabbed(boolean value) {
        if (desktop == null || session == null) return;
        releaseInput();
        grabbed = value;
        desktop.capture(value);
        String result = KeyboardCapture.set(this, value);
        grabButton.setText(value ? "Release" : "Capture");
        if (!value) {
            toolbarContainer.setVisibility(View.VISIBLE); status.setVisibility(View.VISIBLE); systemBars(false);
        } else toast(result + " · Shift+Esc releases input");
    }

    private void systemBars(boolean hide) {
        getWindow().getDecorView().setSystemUiVisibility(hide ? View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY : View.SYSTEM_UI_FLAG_VISIBLE);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE && escapeRelease) {
            if (event.getAction() == KeyEvent.ACTION_UP) escapeRelease = false;
            return true;
        }
        if (session != null && grabbed && event.getAction() != KeyEvent.ACTION_MULTIPLE) {
            int code = KeyMapper.evdev(event.getKeyCode(), event.getScanCode(), capsToSuper);
            if (event.getAction() == KeyEvent.ACTION_DOWN && code == 1
                    && (event.isShiftPressed() || input.held(42) || input.held(54))) {
                escapeRelease = true; setGrabbed(false); return true;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && !event.isFromSource(InputDevice.SOURCE_KEYBOARD)) {
                setGrabbed(false); return true;
            }
            if (!KeyEvent.isModifierKey(event.getKeyCode())) syncModifiers(event);
            else syntheticModifiers.remove(code);
            input.key(code, event.getAction() == KeyEvent.ACTION_DOWN);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void syncModifiers(KeyEvent event) {
        synthesize(event.isShiftPressed(), 42, 54);
        synthesize(event.isCtrlPressed(), 29, 97);
        synthesize(event.isAltPressed(), 56, 100);
        synthesize(event.isMetaPressed(), 125, 126);
    }

    private void synthesize(boolean pressed, int left, int right) {
        if (pressed && !input.held(left) && !input.held(right)) {
            input.key(left, true); syntheticModifiers.add(left);
        } else if (!pressed && syntheticModifiers.remove(left)) input.key(left, false);
    }

    @Override public boolean dispatchKeyShortcutEvent(KeyEvent event) {
        return session != null && grabbed ? dispatchKeyEvent(event) : super.dispatchKeyShortcutEvent(event);
    }

    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (!focus && session != null) setGrabbed(false);
        if (focus && sharingClipboard) offerLocalClipboard();
    }

    @Override protected void onPause() {
        if (session != null) setGrabbed(false);
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (grabbed) setGrabbed(false);
        else if (session != null) new AlertDialog.Builder(this).setMessage("Disconnect from Omarchy?")
                .setNegativeButton("Stay", null).setPositiveButton("Disconnect", (d, w) -> disconnect(null)).show();
        else super.onBackPressed();
    }

    @Override public void send(int type, Object... fields) {
        GliffSession active = session;
        if (active != null) active.send(type, fields);
    }
    @Override public void button(int code, boolean pressed) { input.button(code, pressed); }
    @Override public void mouseButtons(int action, int mask) { input.mouseButtons(action, mask); }
    @Override public boolean isGrabbed() { return grabbed; }
    @Override public void grabRequested() { setGrabbed(true); }
    @Override public void releaseInput() {
        input.releaseAll(); syntheticModifiers.clear(); superLatched = false;
        if (superButton != null) superButton.setText("Super");
    }
    @Override public void resized(int width, int height) {
        if (session != null && config.headless && width > 0 && height > 0) {
            double fit = Math.min(1.0, Math.min(3840.0 / width, 2160.0 / height));
            send(1, Math.max(640, (int) (width * fit)), Math.max(480, (int) (height * fit)), 1.0f);
        }
    }
    @Override public void surfaceLost() {
        surface = null;
        if (session != null) disconnected("Display surface was recreated. Tap Connect to resume.");
    }

    private void disconnected(String reason) {
        disconnect(reason);
    }

    private void disconnect(String error) {
        generation++;
        if (trustWait != null) { trustWait.countDown(); trustWait = null; }
        if (trustDialog != null) { trustDialog.dismiss(); trustDialog = null; }
        input.releaseAll(); grabbed = false; superLatched = false;
        KeyboardCapture.set(this, false);
        if (desktop != null) desktop.capture(false);
        GliffSession old = session; session = null;
        if (old != null) old.close();
        getSystemService(ClipboardManager.class).removePrimaryClipChangedListener(clipboardChanged);
        clipboard = null; credential = ""; config = null;
        desktop = null; surface = null;
        showConnectionForm(error);
    }

    private void importKey() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, 1);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 1 || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            if (stream == null) throw new java.io.IOException("Cannot read selected key");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = stream.read(buffer)) != -1) {
                if (bytes.size() + count > 64 * 1024) throw new java.io.IOException("SSH key exceeds 64 KiB");
                bytes.write(buffer, 0, count);
            }
            wipeKey(); privateKey = bytes.toByteArray();
            keyLabel.setText("SSH key imported · kept in memory only");
        } catch (Exception e) { toast("Cannot import key: " + e.getMessage()); }
    }

    private void wipeKey() {
        if (privateKey != null) java.util.Arrays.fill(privateKey, (byte) 0);
        privateKey = null;
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

    @Override protected void onDestroy() {
        generation++;
        if (trustWait != null) trustWait.countDown();
        if (trustDialog != null) trustDialog.dismiss();
        input.releaseAll(); KeyboardCapture.set(this, false);
        if (session != null) session.close();
        getSystemService(ClipboardManager.class).removePrimaryClipChangedListener(clipboardChanged);
        wipeKey(); credential = "";
        getSystemService(InputManager.class).unregisterInputDeviceListener(devices);
        super.onDestroy();
    }
}
