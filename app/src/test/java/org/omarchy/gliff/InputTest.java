package org.omarchy.gliff;

import org.junit.Test;
import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class InputTest {
    @Test public void mapsOmarchyShortcutKeysAndPhysicalLayout() {
        assertEquals(125, KeyMapper.evdev(117, 0, false)); // Super
        assertEquals(28, KeyMapper.evdev(66, 0, false)); // Enter
        assertEquals(57, KeyMapper.evdev(62, 0, false)); // Space
        assertEquals(2, KeyMapper.evdev(8, 0, false)); // workspace 1
        assertEquals(16, KeyMapper.evdev(45, 0, false)); // Q
        assertEquals(125, KeyMapper.evdev(115, 58, true)); // Caps -> Super
        assertEquals(58, KeyMapper.evdev(115, 58, false));
        assertEquals(44, KeyMapper.evdev(29, 44, false)); // physical code wins over Android layout
        assertEquals(1, KeyMapper.evdev(111, 0, false));
        assertEquals(87, KeyMapper.evdev(141, 0, false)); // F11
        assertEquals(88, KeyMapper.evdev(142, 0, false)); // F12
        assertEquals(96, KeyMapper.evdev(160, 0, false)); // numpad enter
        assertEquals(0, KeyMapper.evdev(9999, 9999, false));
    }

    @Test public void forwardsShortcutChordsWithoutDuplicateRepeatsAndReleasesOnFocusLoss() {
        List<String> events = new ArrayList<>();
        InputState state = new InputState((type, f) -> events.add(type + ":" + f[0] + ":" + f[1]));
        state.key(125, true); state.key(42, true); state.key(16, true); state.key(16, true);
        state.button(273, true);
        state.releaseAll(); state.releaseAll();
        assertEquals(java.util.Arrays.asList("2:125:true", "2:42:true", "2:16:true", "4:273:true",
                "2:125:false", "2:42:false", "2:16:false", "4:273:false"), events);
        assertFalse(state.held(125));
    }

    @Test public void mouseGestureWithoutButtonMaskKeepsDragPressedUntilUp() {
        List<String> events = new ArrayList<>();
        InputState state = new InputState((type, f) -> events.add(type + ":" + f[0] + ":" + f[1]));
        state.mouseButtons(MotionEvent.ACTION_DOWN, 0);
        state.mouseButtons(MotionEvent.ACTION_MOVE, 0);
        state.mouseButtons(MotionEvent.ACTION_UP, 0);
        assertEquals(java.util.Arrays.asList("4:272:true", "4:272:false"), events);
    }

    @Test public void explicitMouseButtonsAndFocusLossNeverLeaveAnImplicitClickHeld() {
        List<String> events = new ArrayList<>();
        InputState state = new InputState((type, f) -> events.add(type + ":" + f[0] + ":" + f[1]));
        state.mouseButtons(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_SECONDARY);
        state.mouseButtons(MotionEvent.ACTION_UP, 0);
        state.mouseButtons(MotionEvent.ACTION_DOWN, 0);
        state.releaseAll();
        state.mouseButtons(MotionEvent.ACTION_MOVE, 0);
        state.mouseButtons(MotionEvent.ACTION_DOWN, 0);
        state.mouseButtons(MotionEvent.ACTION_CANCEL, 0);
        state.mouseButtons(MotionEvent.ACTION_MOVE, 0);
        assertEquals(java.util.Arrays.asList("4:273:true", "4:273:false", "4:272:true", "4:272:false",
                "4:272:true", "4:272:false"), events);
    }

    @Test public void pointerUsesSameLetterboxAndFractionalScaleAsPicture() {
        Viewport v = new Viewport(1200, 1000, 1920, 1080, 1920, 1080, 1250);
        assertEquals(1200, v.width, 0.001);
        assertEquals(162.5, v.top, 0.001);
        assertFalse(v.contains(500, 100));
        assertTrue(v.contains(600, 500));
        assertEquals(768, v.remoteX(600), 0.001);
        assertEquals(432, v.remoteY(500), 0.001);
        assertEquals(600, v.localX(768), 0.001);
        assertEquals(500, v.localY(432), 0.001);
        assertEquals(0, v.remoteX(-50), 0.001);
    }

    @Test public void downscaledStreamKeepsFullSizeFitButLogicalPointerCoordinates() {
        Viewport v = new Viewport(1920, 1080, 960, 540, 1920, 1080, 500);
        assertEquals(960, v.remoteX(960), 0.001);
        assertEquals(540, v.remoteY(540), 0.001);
    }
}
