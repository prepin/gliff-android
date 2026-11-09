package org.omarchy.gliff;

/** Android keycode fallback for synthetic keyboards; physical keyboards use Linux scan codes. */
public final class KeyMapper {
    private KeyMapper() {}
    private static final int[] LETTERS = {30,48,46,32,18,33,34,35,23,36,37,38,50,49,24,25,16,19,31,20,22,47,17,45,21,44};
    private static final int[] NUMPAD = {82,79,80,81,75,76,77,71,72,73};

    public static int evdev(int androidCode, int scanCode, boolean capsToSuper) {
        int code = scanCode > 0 && scanCode <= 767 ? scanCode : fallback(androidCode);
        return capsToSuper && code == 58 ? 125 : code;
    }

    public static int fallback(int code) {
        if (code >= 29 && code <= 54) return LETTERS[code - 29];
        if (code >= 8 && code <= 16) return code - 6;
        if (code >= 131 && code <= 140) return code - 72;
        if (code == 141 || code == 142) return code - 54;
        if (code >= 144 && code <= 153) return NUMPAD[code - 144];
        switch (code) {
            case 7: return 11;
            case 19: return 103; case 20: return 108; case 21: return 105; case 22: return 106;
            case 24: return 115; case 25: return 114;
            case 55: return 51; case 56: return 52;
            case 57: return 56; case 58: return 100;
            case 59: return 42; case 60: return 54;
            case 61: return 15; case 62: return 57;
            case 66: return 28; case 67: return 14;
            case 68: return 41; case 69: return 12; case 70: return 13;
            case 71: return 26; case 72: return 27; case 73: return 43;
            case 74: return 39; case 75: return 40; case 76: return 53;
            case 82: return 127; case 92: return 104; case 93: return 109;
            case 111: return 1; case 112: return 111;
            case 113: return 29; case 114: return 97; case 115: return 58;
            case 116: return 70; case 117: return 125; case 118: return 126;
            case 120: return 99; case 121: return 119;
            case 122: return 102; case 123: return 107; case 124: return 110;
            case 143: return 69;
            case 154: return 98; case 155: return 55; case 156: return 74;
            case 157: return 78; case 158: return 83; case 159: return 121;
            case 160: return 96; case 161: return 117;
            default: return 0;
        }
    }
}
