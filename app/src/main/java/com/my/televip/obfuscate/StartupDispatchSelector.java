package com.my.televip.obfuscate;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Select the internal dispatcher without trusting an arbitrary same-signature helper. */
public final class StartupDispatchSelector {
    private StartupDispatchSelector() {}
    public static Method find(Class<?> manager, Class<?> request) {
        Method named = null, unique = null;
        int matches = 0, namedMatches = 0;
        for (Method method : manager.getDeclaredMethods()) {
            Class<?>[] p = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class
                    || p.length != 10 || p[0] != request
                    || !p[1].isInterface() || !p[2].isInterface() || !p[3].isInterface() || !p[4].isInterface()
                    || p[5] != int.class || p[6] != int.class || p[7] != int.class
                    || p[8] != boolean.class || p[9] != int.class) continue;
            matches++;
            unique = method;
            if (method.getName().equals("sendRequestInternal")) {named = method; namedMatches++;}
        }
        // R8 may retain the private dispatcher and also generate a public lambda with its signature.
        return namedMatches == 1 ? named : namedMatches == 0 && matches == 1 ? unique : null;
    }
}
