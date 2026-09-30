package com.my.televip.features.other;

import com.my.televip.Class.ClassNames;
import com.my.televip.Class.ClassLoad;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.messenger.NotificationCenter;

public class FixTLError {

    public static volatile boolean isEnable = false;

    public static synchronized void init() {
        try (HookInstallation attempt = HookInstallation.begin()) {
            if (!isEnable) {
                if (attempt.require(ClassLoad.getClass(ClassNames.LAUNCH_ACTIVITY))) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.LAUNCH_ACTIVITY), Obfuscate.getMethodName("LaunchActivity", "didReceivedNotification"), ArgsResolver.merge("didReceivedNotification", new Class[]{int.class, int.class, Object[].class}, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            int id = (int) param.args[0];
                            if (id == NotificationCenter.getTlSchemeParseException() && ConfigManager.fixTLError.isEnable())
                                param.setResult(null);
                        }
                    }));
                }
                isEnable = attempt.isComplete();
            }
        } catch (Throwable t){
            Logger.e(t);
    }
    }

}
