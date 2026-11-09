package org.omarchy.gliff;

import android.view.MotionEvent;
import java.util.LinkedHashSet;
import java.util.Set;

/** Track remote state, not Android repeats; the remote compositor repeats held keys. */
public final class InputState {
    public interface Sink { void send(int type, Object... fields); }
    private final Sink sink;
    private final Set<Integer> keys = new LinkedHashSet<>();
    private final Set<Integer> buttons = new LinkedHashSet<>();
    private static final int[] BUTTON_FLAGS = {1, 2, 4, 8, 16};
    private static final int[] BUTTON_CODES = {272, 273, 274, 275, 276};
    private boolean implicitPrimary;

    public InputState(Sink sink) { this.sink = sink; }

    public void key(int code, boolean pressed) {
        if (code == 0) return;
        if (pressed ? keys.add(code) : keys.remove(code)) sink.send(2, code, pressed);
    }

    public void button(int code, boolean pressed) {
        if (pressed ? buttons.add(code) : buttons.remove(code)) sink.send(4, code, pressed);
    }

    public void mouseButtons(int action, int mask) {
        if (action == MotionEvent.ACTION_CANCEL) { releaseAll(); return; }
        // Some Android mouse/touchpad gestures carry DOWN/UP but no button mask.
        if (action == MotionEvent.ACTION_DOWN) implicitPrimary = mask == 0;
        if (action == MotionEvent.ACTION_UP) implicitPrimary = false;
        if (implicitPrimary) mask |= MotionEvent.BUTTON_PRIMARY;
        for (int i = 0; i < BUTTON_FLAGS.length; i++) button(BUTTON_CODES[i], (mask & BUTTON_FLAGS[i]) != 0);
    }

    public boolean held(int code) { return keys.contains(code); }

    public void releaseAll() {
        implicitPrimary = false;
        for (int code : keys) sink.send(2, code, false);
        for (int code : buttons) sink.send(4, code, false);
        keys.clear();
        buttons.clear();
    }
}
