package com.my.televip.features.media;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/** Resolve only the fingerprinted scheduler; tolerate R8 moving its receiver into argument zero. */
public final class MediaHookSelector {
    private MediaHookSelector() {}
    public static Method find(Class<?> owner, String name, boolean staticized, Class<?>... parameters) {
        if (owner == null || name == null) return null;
        Method found = null;
        for (Method method : owner.getDeclaredMethods()) {
            if (!method.getName().equals(name) || !Runnable.class.isAssignableFrom(method.getReturnType())) continue;
            boolean isStatic = Modifier.isStatic(method.getModifiers());
            Class<?>[] actual = method.getParameterTypes();
            if (staticized && isStatic && actual.length == parameters.length + 1 && actual[0] == owner)
                actual = Arrays.copyOfRange(actual, 1, actual.length);
            else if (isStatic) continue;
            if (!Arrays.equals(actual, parameters)) continue;
            if (found != null) return null;
            found = method;
        }
        return found;
    }
}
