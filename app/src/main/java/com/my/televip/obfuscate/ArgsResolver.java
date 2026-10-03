package com.my.televip.obfuscate;


import com.my.televip.obfuscate.struct.ResolverRegistry;

import com.my.televip.compat.XC_MethodHook;

public class ArgsResolver {

    public static ResolverRegistry resolverRegistry = new ResolverRegistry(ResolverRegistry.getResolverClass());

    public static Class<?>[] resolveObject(String name, Class<?>[] classes) {
        if (RuntimeMappings.mayUseLegacyTable() && resolverRegistry != null) {
            if (resolverRegistry.hasParameter(name)) {
                return resolverRegistry.resolveParameter(name);
            }
        }

        return classes;
    }

    public static Object[] merge(String name, Class<?>[] classes, XC_MethodHook hook)
    {
        Class<?>[] c = resolveObject(name, classes);
        if (c != null) {
            Object[] result = new Object[c.length + 1];
            System.arraycopy(c, 0, result, 0, c.length);
            result[c.length] = hook;
            return result;
        }
        return null;
    }
}
