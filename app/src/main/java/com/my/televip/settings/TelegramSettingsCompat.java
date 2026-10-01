package com.my.televip.settings;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

/** Resolve renamed UI types from the host's own signatures, without guessing R8 names. */
public final class TelegramSettingsCompat {
    private TelegramSettingsCompat() {}

    public static Method settingsClick(Class<?> settings, Class<?> factory) throws ReflectiveOperationException {
        Method create = factory.getDeclaredMethod("a", int.class, int.class, int.class, int.class,
                CharSequence.class, CharSequence.class, CharSequence.class);
        Class<?> row = create.getReturnType();
        if (!Modifier.isStatic(create.getModifiers()) || !isComponent(row)
                || row.getDeclaredField("d").getType() != int.class)
            throw new NoSuchMethodException("Unsupported Telegram settings row factory");
        Method click = settings.getDeclaredMethod("f0", settings, row);
        if (!Modifier.isStatic(click.getModifiers()) || click.getReturnType() != void.class)
            throw new NoSuchMethodException("Unsupported Telegram settings click signature");
        return click;
    }

    public static Method userInfoRows(Class<?> userInfo) throws NoSuchMethodException {
        Method found = null;
        for (Method method : userInfo.getDeclaredMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (!method.getName().equals("U") || Modifier.isStatic(method.getModifiers())
                    || method.getReturnType() != void.class || params.length != 2
                    || params[0] != ArrayList.class || !isComponent(params[1])) continue;
            if (found != null) throw new NoSuchMethodException("Ambiguous Telegram user info row builder");
            found = method;
        }
        if (found == null) throw new NoSuchMethodException("Telegram user info row builder unavailable");
        return found;
    }

    private static boolean isComponent(Class<?> type) {
        return type.getName().startsWith("org.telegram.ui.Components.");
    }
}
