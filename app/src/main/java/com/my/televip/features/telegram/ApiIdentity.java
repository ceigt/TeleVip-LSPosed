package com.my.televip.features.telegram;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class ApiIdentity {
    private ApiIdentity() {}
    public static boolean valid(String id, String hash) {
        try {
            return id != null && id.matches("[0-9]{1,10}") && Integer.parseInt(id) > 0
                    && hash != null && hash.matches("[0-9a-fA-F]{32}");
        } catch (NumberFormatException invalid) { return false; }
    }
    public static boolean apply(Object target, Field idField, Field hashField, String id, String hash)
            throws IllegalAccessException {
        if (!valid(id, hash)) return false;
        if (idField.getType() != int.class || hashField.getType() != String.class
                || Modifier.isStatic(idField.getModifiers()) != Modifier.isStatic(hashField.getModifiers())
                || Modifier.isFinal(idField.getModifiers()) || Modifier.isFinal(hashField.getModifiers())) {
            throw new IllegalArgumentException("Unexpected API identity fields");
        }
        int originalId = idField.getInt(target);
        hashField.get(target);
        idField.setInt(target, Integer.parseInt(id));
        try {
            hashField.set(target, hash);
        } catch (IllegalAccessException | RuntimeException failure) {
            try { idField.setInt(target, originalId); }
            catch (IllegalAccessException | RuntimeException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
        return true;
    }

}
