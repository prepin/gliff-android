package org.omarchy.gliff;

/** Resolve a standard XKB layout on the server, without an Android libxkbcommon dependency. */
public final class XkbKeymap {
    private XkbKeymap() {}

    public static String create(String layout, String variant) {
        if (!layout.matches("[a-z0-9_]{1,32}") || !variant.matches("[a-zA-Z0-9_-]{0,64}"))
            throw new IllegalArgumentException("Use an XKB layout such as us, gb, de, fr, or cz, and an optional variant.");
        String symbols = layout + (variant.isEmpty() ? "" : "(" + variant + ")");
        return "xkb_keymap {\n"
                + "xkb_keycodes { include \"evdev+aliases(qwerty)\" };\n"
                + "xkb_types { include \"complete\" };\n"
                + "xkb_compatibility { include \"complete\" };\n"
                + "xkb_symbols { include \"pc+" + symbols + "+inet(evdev)\" };\n"
                + "};\n";
    }
}
