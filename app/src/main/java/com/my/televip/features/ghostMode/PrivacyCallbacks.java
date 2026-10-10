package com.my.televip.features.ghostMode;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Normal and timestamp delegates are completed exactly once, on Telegram's stage queue. */
public final class PrivacyCallbacks {
    private PrivacyCallbacks() {}
    public static Method find(Object callback, Object response, Object error, boolean timestamp) throws NoSuchMethodException {
        Method found = null;
        for (Class<?> cls = callback.getClass(); cls != null; cls = cls.getSuperclass()) {
            for (Class<?> iface : cls.getInterfaces()) for (Method method : iface.getMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if (method.getReturnType() != void.class || p.length != (timestamp ? 3 : 2)
                        || p[0].isPrimitive() || p[1].isPrimitive() || timestamp && p[2] != long.class
                        || response != null && !p[0].isInstance(response)
                        || error != null && !p[1].isInstance(error)) continue;
                if (found != null && !found.equals(method)) throw new NoSuchMethodException("Ambiguous privacy delegate");
                found = method;
            }
        }
        if (found == null) throw new NoSuchMethodException("Privacy delegate unavailable");
        found.setAccessible(true);
        return found;
    }
    public static void invoke(Method method, Object callback, Object response, Object error) throws Throwable {
        try {
            method.invoke(callback, method.getParameterCount() == 3
                    ? new Object[]{response, error, 0L} : new Object[]{response, error});
        } catch (InvocationTargetException failure) { throw failure.getCause(); }
    }
}
