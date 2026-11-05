package com.my.televip.compat;

import java.lang.reflect.Array;

/** Resolve the actual parameter descriptors emitted by the APK resolver without initializing host classes. */
public final class HookParameterTypes {
    private HookParameterTypes() {}
    public static Class<?>[] resolve(String[] names, ClassLoader loader) {
        Class<?>[] types = new Class<?>[names.length];
        for (int i = 0; i < names.length; i++) {
            try { types[i] = type(names[i], loader); }
            catch (ClassNotFoundException error) { throw new IllegalStateException("Resolved hook parameter unavailable", error); }
        }
        return types;
    }
    private static Class<?> type(String name, ClassLoader loader) throws ClassNotFoundException {
        switch (name) {
            case "int": case "I": return int.class;
            case "long": case "J": return long.class;
            case "boolean": case "Z": return boolean.class;
            case "float": case "F": return float.class;
            case "double": case "D": return double.class;
            case "byte": case "B": return byte.class;
            case "short": case "S": return short.class;
            case "char": case "C": return char.class;
            default:
                if (name.endsWith("[]")) return Array.newInstance(type(name.substring(0,name.length()-2), loader),0).getClass();
                if (name.startsWith("L") && name.endsWith(";")) name = name.substring(1,name.length()-1);
                return Class.forName(name.replace('/','.'), false, loader);
        }
    }
}
