package com.my.televip.obfuscate;

import com.my.televip.obfuscate.struct.ClientObfuscationData;
import com.my.televip.obfuscate.resolve.TelegramFingerprints;

public class Obfuscate {

    public static String getClassName(String className) {
        ClientObfuscationData data = RuntimeMappings.mayUseLegacyTable() ? ObfuscationManager.current() : null;
        String mapped = RuntimeMappings.active().resolveClass(className);
        if (mapped != null) return mapped;
        if (data == null) return className;
        return data.resolveClass(className);
    }

    public static String getMethodName(String className, String methodName) {
        ClientObfuscationData data = RuntimeMappings.mayUseLegacyTable() ? ObfuscationManager.current() : null;
        String mapped = RuntimeMappings.active().resolveMethod(className, TelegramFingerprints.methodKey(className, methodName));
        if (mapped != null) return mapped;
        if (data == null) return methodName.replace("storyEntitiesAllowed2", "storyEntitiesAllowed");
        return data.resolveMethod(className, methodName);
    }

    public static String getFieldName(String className, String fieldName) {
        ClientObfuscationData data = RuntimeMappings.mayUseLegacyTable() ? ObfuscationManager.current() : null;
        String mapped = RuntimeMappings.active().resolveField(className, fieldName);
        if (mapped != null) return mapped;
        if (data == null) return fieldName;
        return data.resolveField(className, fieldName);
    }
}