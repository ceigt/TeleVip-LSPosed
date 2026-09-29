package com.my.televip.features.other;

import com.my.televip.Class.ClassNames;
import com.my.televip.Clients.ClientManager;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.utils.Utils;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.Class.ClassLoad;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.logging.Logger;

import com.my.televip.compat.XC_MethodHook;
import com.my.televip.compat.XposedHelpers;

public class TelePremium {

    public static boolean isEnable = false;

    public static void init() {
        try {
            if (!isEnable) {
                isEnable = true;

                if (ClassLoad.getClass(ClassNames.USER_CONFIG) != null) {

                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.USER_CONFIG), Obfuscate.getMethodName("UserConfig", "isPremium"), new BaseMethodHook() {
                        @Override
                        public void beforeMethod(XC_MethodHook.MethodHookParam param) {
                            if (ConfigManager.telegramPremium.isEnable()) param.setResult(true);
                        }
                    });
                }
                if (ClientManager.is(ClientManager.Client.iMe) || ClientManager.is(ClientManager.Client.iMeWeb)) {
                    Class<?> ForkPremiumPreferencClass = XposedHelpers.findClassIfExists("com.iMe.storage.data.locale.prefs.impl.ForkPremiumPreference", Utils.classLoader);
                    if (ForkPremiumPreferencClass != null) {
                        HMethod.hookMethod(ForkPremiumPreferencClass, "isPremium", new BaseMethodHook() {
                            @Override
                            protected void beforeMethod(MethodHookParam param) {
                                if (ConfigManager.telegramPremium.isEnable())
                                    param.setResult(true);
                            }
                        });
                    }
                }
            }
        } catch (Throwable t){
            Logger.e(t);
        }
    }

}
