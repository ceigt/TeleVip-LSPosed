package com.my.televip.features.ui;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.application.AndroidUtilities;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.messenger.MessagesController;

public class HideProxySponsor {

    public static volatile boolean isEnable = false;

    public static synchronized void init() {
        try (HookInstallation attempt = HookInstallation.begin()) {
            if (!isEnable) {
                if (attempt.require(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER))) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER),
                            Obfuscate.getMethodName("MessagesController", "checkPromoInfoInternal"),ArgsResolver.merge("checkPromoInfoInternal", new Class[]{boolean.class}, new BaseMethodHook() {
                                @Override
                                protected void afterMethod(MethodHookParam param) {
                                    if (ConfigManager.hideProxySponsor.isEnable()) {
                                        MessagesController messagesController = new MessagesController(param.thisObject);
                                        AndroidUtilities.runOnUIThread(messagesController::removePromoDialog);
                                        removePromoDialog();
                                    }
                                }
                            }));
                }
                isEnable = attempt.isComplete();
            }
        } catch (Throwable e) {
            Logger.e(e);
        }
    }

    public static void removePromoDialog() {
        if (MessagesController.getGlobalMainSettings() == null) return;
        MessagesController.getGlobalMainSettings().edit().remove("proxy_dialog").remove("proxyDialogAddress").remove("nextPromoInfoCheckTime").apply();
    }

}
