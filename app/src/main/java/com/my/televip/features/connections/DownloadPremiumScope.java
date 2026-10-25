package com.my.televip.features.connections;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Download connection limits must use the account's real subscription. */
public final class DownloadPremiumScope {
    private static final ThreadLocal<Integer> depth = new ThreadLocal<>();
    private DownloadPremiumScope() {}

    public static void enter() { Integer value = depth.get(); depth.set(value == null ? 1 : value + 1); }
    public static void exit() {
        Integer value = depth.get();
        if (value == null || value <= 1) depth.remove();
        else depth.set(value - 1);
    }
    public static boolean active() { return depth.get() != null; }

    public static Method findStart(Class<?> owner, String name) {
        if (owner == null || name == null) return null;
        Method found = null;
        for (Method method : owner.getDeclaredMethods()) {
            Class<?>[] p = method.getParameterTypes();
            if (!method.getName().equals(name) || Modifier.isStatic(method.getModifiers())
                    || method.getReturnType() != boolean.class || p.length != 3
                    || p[0].isPrimitive() || p[1] != long.class || p[2] != boolean.class) continue;
            if (found != null) return null;
            found = method;
        }
        return found;
    }
}
