package com.my.televip.features.otherFeatures;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Clients.ClientManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.utils.Utils;

import com.my.televip.compat.XposedHelpers;

public class FeatureInitializer {

    public static void init() {

        try {
            if (ClientManager.is(ClientManager.Client.Telegram)) {
                ChatHook.init("org.telegram.ui.vj");
                ProfileHook.init("org.telegram.ui.o01");
                return;
            }
            if (!FeatureStateManager.isChatEnabled() || !FeatureStateManager.isProfileEnabled()) {

                Class<?> actionBarClass = XposedHelpers.findClassIfExists(
                        Obfuscate.getClassName("org.telegram.ui.ActionBar.ActionBar"),
                        Utils.classLoader
                );

                HMethod.hookMethod(
                        actionBarClass,
                        Obfuscate.getMethodName("ActionBar", "setActionBarMenuOnItemClick"), ClassLoad.getClass(ClassNames.ACTION_BAR_MENU_ON_ITEM_CLICK),
                        new BaseMethodHook() {
                            @Override
                            protected void beforeMethod(MethodHookParam param) {

                                Object clazz = param.args[0];

                                if (clazz == null) return;

                                String name = clazz.getClass().getName();

                                if (name.contains("ChatActivity") && !FeatureStateManager.isChatEnabled()) {
                                    FeatureStateManager.saveChat(name);
                                    ChatHook.init(name);
                                }

                                if (name.contains("ProfileActivity") && !FeatureStateManager.isProfileEnabled()) {
                                    FeatureStateManager.saveProfile(name);
                                    ProfileHook.init(name);
                                }
                            }
                        });

            } else {
                ChatHook.init(FeatureStateManager.getChatClass());
                ProfileHook.init(FeatureStateManager.getProfileClass());
            }

        } catch (Throwable t) {
            Logger.e(t);
        }
    }
}
