package com.my.televip.features.ui;

import android.view.View;
import com.my.televip.Class.ClassLoad;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.Obfuscate;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Adapted from Re-TeleVIP 1.5.2. Let the host manage search/selection/proxy transitions. */
public final class DownloadsShortcut {
    public static volatile boolean isEnable;
    private static volatile Field itemField;

    public static synchronized void init() {
        if (isEnable) return;
        try (HookInstallation attempt = HookInstallation.begin()) {
            Class<?> itemType = ClassLoad.getClass("org.telegram.ui.ActionBar.ActionBarMenuItem");
            Class<?> barType = ClassLoad.getClass("org.telegram.ui.ActionBar.ActionBar");
            if (attempt.require(itemType) && attempt.require(barType))
                HMethod.hookMethod(ClassLoad.getClass("org.telegram.ui.DialogsActivity"),
                        Obfuscate.getMethodName("DialogsActivity", "updateProxyButton"), boolean.class, boolean.class,
                        new BaseMethodHook() {
                            @Override protected void beforeMethod(MethodHookParam param) throws Throwable {
                                if (!ConfigManager.alwaysShowDownloads.isEnable()) return;
                                View item = findItem(param.thisObject, itemType, barType);
                                if (item == null) return; // View not built yet; retry on the next update.
                                item.setVisibility(View.VISIBLE);
                                item.setAlpha(1f);
                                param.args[1] = false;
                            }
                        });
            isEnable = attempt.isComplete();
        } catch (Throwable error) { Logger.e(error); }
    }

    private static View findItem(Object fragment, Class<?> itemType, Class<?> barType) throws IllegalAccessException {
        Field cached = itemField;
        if (cached != null && cached.getDeclaringClass().isInstance(fragment)) {
            Object value = cached.get(fragment);
            if (isDownloadItem(value, itemType, barType)) return (View) value;
        }
        Field found = null;
        View item = null;
        for (Class<?> type = fragment.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !itemType.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Object value = field.get(fragment);
                if (!isDownloadItem(value, itemType, barType)) continue;
                if (found != null) throw new IllegalStateException("Ambiguous Downloads menu item");
                found = field;
                item = (View) value;
            }
        }
        if (found != null) itemField = found;
        return item;
    }

    private static boolean isDownloadItem(Object value, Class<?> itemType, Class<?> barType) {
        if (!itemType.isInstance(value)) return false;
        View view = (View) value;
        return Integer.valueOf(3).equals(view.getTag()) && view.getParent() instanceof View
                && barType.isInstance(((View) view.getParent()).getParent());
    }
}
