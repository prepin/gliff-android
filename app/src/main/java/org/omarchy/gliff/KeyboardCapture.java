package org.omarchy.gliff;

import android.app.Activity;
import android.content.ComponentName;
import android.view.WindowManager;
import java.lang.reflect.Method;

public final class KeyboardCapture {
    private KeyboardCapture() {}

    public static String set(Activity activity, boolean enabled) {
        // Reflection keeps older Android devices compatible with the API 36.1 method.
        try {
            WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
            Method method = attributes.getClass().getMethod("setKeyboardCaptureEnabled", boolean.class);
            method.invoke(attributes, enabled);
            activity.getWindow().setAttributes(attributes);
            return "Android keyboard capture requested";
        } catch (ReflectiveOperationException | SecurityException ignored) { }
        try {
            Class<?> type = Class.forName("com.samsung.android.view.SemWindowManager");
            Object manager = type.getMethod("getInstance").invoke(null);
            if (manager == null) return "Samsung keyboard capture unavailable · use Caps Lock → Super";
            type.getDeclaredMethod("requestMetaKeyEvent", ComponentName.class, boolean.class)
                    .invoke(manager, activity.getComponentName(), enabled);
            return "Samsung Meta-key capture requested";
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return "System shortcuts may stay local · use Caps Lock → Super";
        }
    }
}
