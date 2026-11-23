package com.my.televip.features.telegram;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Configs.ConfigItem;
import com.my.televip.obfuscate.RuntimeMappings;
import com.my.televip.obfuscate.resolve.TelegramFingerprints;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class Host {
    private Host() {}
    static Class<?> type(String owner) throws ClassNotFoundException {
        String canonical = TelegramFingerprints.owners().get(owner);
        Class<?> type = canonical == null ? null : ClassLoad.getClass(canonical);
        if (type == null) throw new ClassNotFoundException(owner);
        return type;
    }
    static Field field(String owner, String key) throws ReflectiveOperationException {
        String name = RuntimeMappings.active().resolveField(owner, key);
        if (name == null) throw new NoSuchFieldException(owner + "." + key);
        Class<?> type = type(owner);
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException missing) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(owner + "." + key);
    }
    static Method method(String owner, String key, int count) throws ReflectiveOperationException {
        String name = RuntimeMappings.active().resolveMethod(owner, key);
        Method found = null;
        for (Method method : type(owner).getDeclaredMethods()) {
            if (name == null || !name.equals(method.getName()) || method.getParameterCount() != count) continue;
            if (found != null) throw new NoSuchMethodException("Ambiguous " + owner + "." + key);
            found = method;
        }
        if (found == null) throw new NoSuchMethodException(owner + "." + key);
        found.setAccessible(true);
        return found;
    }
    static boolean enabled(ConfigItem item) { return item != null && item.isEnable(); }
}
