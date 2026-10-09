package com.my.televip.features.ui;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/** Match the protocol reader exactly, and replace answers only after the stream was consumed. */
public final class AdResponseFilter {
    private AdResponseFilter() {}
    public static Method reader(Class<?> request, Class<?> stream, Class<?> tlObject, Class<?> empty) {
        if (request == null || stream == null || tlObject == null || empty == null) return null;
        Method found = null;
        for (Method method : request.getDeclaredMethods()) {
            if (!method.getName().equals("deserializeResponse") || Modifier.isStatic(method.getModifiers())
                    || method.isBridge() || !tlObject.isAssignableFrom(method.getReturnType())
                    || !method.getReturnType().isAssignableFrom(empty)
                    || !Arrays.equals(method.getParameterTypes(), new Class<?>[]{stream, int.class, boolean.class})) continue;
            if (found != null) return null;
            found = method;
        }
        return found;
    }
    public static Object answer(Object response, Constructor<?> empty, boolean enabled) throws ReflectiveOperationException {
        if (!enabled || response == null || empty.getDeclaringClass().isInstance(response)) return response;
        return empty.newInstance();
    }
}
