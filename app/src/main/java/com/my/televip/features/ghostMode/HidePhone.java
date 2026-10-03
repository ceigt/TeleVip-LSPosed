package com.my.televip.features.ghostMode;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Clients.ClientManager;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.settings.TelegramSettingsCompat;
import com.my.televip.virtuals.tgnet.TLRPC;
import com.my.televip.virtuals.ui.BaseFragment;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

public final class HidePhone {
    public static volatile boolean isEnable;
    private static final PhoneDisplayMask displayMask = new PhoneDisplayMask();

    public static synchronized void init() {
        if (isEnable) return;
        try (HookInstallation attempt = HookInstallation.begin()) {
            boolean telegram = ClientManager.is(ClientManager.Client.Telegram);
            Class<?> formatter = ClassLoad.getClass("org.telegram.PhoneFormat.PhoneFormat");
            HMethod.hookMethod(formatter, Obfuscate.getMethodName("PhoneFormat", "format"), String.class, new BaseMethodHook() {
                @Override protected void beforeMethod(MethodHookParam param) {
                    if (!ConfigManager.hidePhone.isEnable()) return;
                    String replacement = displayMask.replacement((String) param.args[0]);
                    if (replacement != null) param.setResult(replacement);
                }
            });
            Class<?> settings = ClassLoad.getClass("org.telegram.ui.SettingsActivity");
            if (telegram) {
                HMethod.hookMethod(settings, Obfuscate.getMethodName("SettingsActivity", "updateUserData"), ClassLoad.getClass(ClassNames.TLRPC_USER), displayScope());
                HMethod.hookMethod(com.my.televip.settings.NativeSettingsEntry.uniqueMethod(settings, Obfuscate.getMethodName("SettingsActivity", "fillItems"), ArrayList.class), displayScope());
                HMethod.hookMethod(com.my.televip.settings.NativeSettingsEntry.uniqueMethod(
                        ClassLoad.getClass("org.telegram.ui.UserInfoActivity"), Obfuscate.getMethodName("UserInfoActivity", "fillItems"), ArrayList.class), displayScope());
            } else if (attempt.require(settings)) {
                // Keep old clients' scopes on UI rendering methods only.
                boolean found = false;
                for (Method method : settings.getDeclaredMethods()) {
                    if (method.getName().equals("createView") || method.getName().equals("updateUserData")) {
                        HMethod.hookMethod(method, displayScope());
                        found = true;
                    }
                }
                if (!found) HookInstallation.failure();
            }
            HMethod.hookMethod(ClassLoad.getClass(ClassNames.PROFILE_ACTIVITY),
                    Obfuscate.getMethodName("ProfileActivity", "updateProfileData"), boolean.class, displayScope());
            isEnable = attempt.isComplete();
            if (isEnable) Logger.l("Display-only phone hooks installed");
        } catch (Throwable error) { Logger.e(error); }
    }

    private static BaseMethodHook displayScope() {
        return new BaseMethodHook() {
            @Override protected void beforeMethod(MethodHookParam param) {
                String phone = null;
                try {
                    if (ConfigManager.hidePhone.isEnable()) {
                        Object fragment = Modifier.isStatic(param.method.getModifiers()) ? param.args[0] : param.thisObject;
                        TLRPC.User user = new BaseFragment(fragment).getUserConfig().getCurrentUser();
                        if (user.getUser() != null) phone = user.getPhone();
                    }
                } finally { displayMask.enter(param, phone); }
            }
            @Override protected void afterMethod(MethodHookParam param) { displayMask.exit(param); }
        };
    }
}
