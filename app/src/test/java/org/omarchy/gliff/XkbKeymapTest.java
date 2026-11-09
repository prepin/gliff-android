package org.omarchy.gliff;

import org.junit.Test;
import static org.junit.Assert.*;

public class XkbKeymapTest {
    @Test public void createsServerResolvableLayoutAndVariant() {
        assertTrue(XkbKeymap.create("us", "").contains("pc+us+inet(evdev)"));
        assertTrue(XkbKeymap.create("cz", "qwerty").contains("pc+cz(qwerty)+inet(evdev)"));
        assertTrue(XkbKeymap.create("de", "nodeadkeys").contains("pc+de(nodeadkeys)+inet(evdev)"));
    }

    @Test public void rejectsIncludeInjectionAndPaths() {
        for (String layout : new String[]{"", "../us", "us+ru", "us\" };", "us,gb"})
            assertThrows(IllegalArgumentException.class, () -> XkbKeymap.create(layout, ""));
        assertThrows(IllegalArgumentException.class, () -> XkbKeymap.create("us", "dvorak)+foo"));
    }
}
