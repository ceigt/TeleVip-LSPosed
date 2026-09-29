package com.my.televip.Class;

import com.my.televip.Clients.ClientManager;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.utils.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import com.my.televip.compat.XposedHelpers;

public class ClassLoad {

    private static final Map<ClassLoader, Map<String, Class<?>>> cache = new WeakHashMap<>();

    public static Class<?> getClass(String name) {
        return getClass(name, Utils.classLoader, true);
    }

    public static Class<?> getClass(String name, ClassLoader classLoader) {
        return getClass(name, classLoader, true);
    }

    public static Class<?> getClass(String name, ClassLoader classLoader, boolean log) {

        String resolved = Obfuscate.getClassName(name);

        Map<String, Class<?>> loaderCache =
                cache.computeIfAbsent(classLoader, k -> new HashMap<>());

        if (loaderCache.containsKey(resolved)) {
            return loaderCache.get(resolved);
        }

        try {
            Class<?> cls = XposedHelpers.findClassIfExists(
                    resolved,
                    classLoader
            );

            if (cls != null) {
                loaderCache.put(resolved, cls);
            } else {
                if (log) {
                    if ((ClientManager.is(ClientManager.Client.Nagram) || ClientManager.is(ClientManager.Client.Momogram) || ClientManager.is(ClientManager.Client.Nekogram)) && name.equals(ClassNames.DRAWABLE))
                        return null;

                    if (name.equals(resolved)) {
                        Logger.w("Not found " + name + " " + Utils.issue);
                    } else {
                        Logger.w("Not found " + name + ", " + resolved + " " + Utils.issue);
                    }
                }
            }

            return cls;

        } catch (Throwable e) {
            Logger.e(e);
            return null;
        }
    }

}
