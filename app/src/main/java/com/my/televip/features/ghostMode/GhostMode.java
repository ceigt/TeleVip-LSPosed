package com.my.televip.features.ghostMode;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Clients.ClientManager;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;


public class GhostMode {

    public static volatile boolean isEnable = false;

    public static synchronized void init() {
        try {
            PrivacyProtection.prepare();
            if (!isEnable) {
                if (ClassLoad.getClass(ClassNames.CONNECTIONS_MANAGER) != null && ConfigManager.isGhostMode()) {
                    isEnable = HMethod.hookMethod(ClassLoad.getClass(ClassNames.CONNECTIONS_MANAGER), Obfuscate.getMethodName("ConnectionsManager", "sendRequestInternal"), ArgsResolver.merge("sendRequestInternal", new Class[]{ClassLoad.getClass(ClassNames.TL_OBJECT), ClassLoad.getClass(ClassNames.REQUEST_DELEGATE), ClassLoad.getClass(ClassNames.REQUEST_DELEGATE_TIMESTAMP), ClassLoad.getClass(ClassNames.QUICK_ACK_DELEGATE), ClassLoad.getClass(ClassNames.WRITE_TO_SOCKET_DELEGATE), int.class, int.class, int.class, boolean.class, int.class}, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            PrivacyProtection.intercept(param);
                        }
                    }));
                    if (isEnable) {
                        com.my.televip.diagnostics.PrivacyStatus.clear("privacy-init");
                        Logger.l("Ghost mode request hook installed");
                    } else com.my.televip.diagnostics.PrivacyStatus.report("privacy-init", "Privacy dispatcher hook unavailable; fallback active");
                }
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    public static boolean isOnlineRequest(Object object) {
        if (!ClientManager.isTgnetObfuscated()) {
            return object.getClass().getName().contains("TL_account$updateStatus");
        } else {
            return object.getClass().equals(ClassLoad.getClass(ClassNames.TL_ACCOUNT_UPDATE_STATUS));
        }
    }

}
