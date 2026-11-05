package com.my.televip.features.ui;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;

public class DisableNumberRounding {

    public static volatile boolean isEnable = false;

    public static synchronized void init() {
        try (HookInstallation attempt = HookInstallation.begin()) {
            if (!isEnable) {

                if (attempt.require(ClassLoad.getClass(ClassNames.LOCALE_CONTROLLER))) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.LOCALE_CONTROLLER), Obfuscate.getMethodName("LocaleController", "formatShortNumber"), ArgsResolver.merge("formatShortNumber", new Class[]{int.class, int[].class},
                            new BaseMethodHook() {
                                @Override
                                protected void beforeMethod(MethodHookParam param) {
                                    if (ConfigManager.disableNumberRounding.isEnable()) {
                                        int[] rounded = null;
                                        int number = 0;
                                        for (Object arg : param.args) {
                                            if (arg instanceof int[]) rounded = (int[]) arg;
                                            else if (arg instanceof Integer) number = (Integer) arg;
                                        }
                                        if (rounded != null && rounded.length > 0) {
                                            rounded[0] = number;
                                        }
                                        param.setResult(String.valueOf(number));
                                    }
                                }
                            }));
                }
                isEnable = attempt.isComplete();
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }
}
