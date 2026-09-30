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

import com.my.televip.compat.XposedHelpers;

public class GhostMode {

    public static volatile boolean isEnable = false;

    public static synchronized void init() {
        try {
            if (!isEnable) {
                if (ClassLoad.getClass(ClassNames.CONNECTIONS_MANAGER) != null && ConfigManager.isGhostMode()) {
                    isEnable = HMethod.hookMethod(ClassLoad.getClass(ClassNames.CONNECTIONS_MANAGER), Obfuscate.getMethodName("ConnectionsManager", "sendRequestInternal"), ArgsResolver.merge("sendRequestInternal", new Class[]{ClassLoad.getClass(ClassNames.TL_OBJECT), ClassLoad.getClass(ClassNames.REQUEST_DELEGATE), ClassLoad.getClass(ClassNames.REQUEST_DELEGATE_TIMESTAMP), ClassLoad.getClass(ClassNames.QUICK_ACK_DELEGATE), ClassLoad.getClass(ClassNames.WRITE_TO_SOCKET_DELEGATE), int.class, int.class, int.class, boolean.class, int.class}, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            try {
                                Object request = param.args[0];
                                if (HideSeen.consumeReadPermit(request, param.thisObject)) return;
                                if (request != null && ConfigManager.isGhostMode()) {
                                    Object object = param.args[0];

                                    if (ClientManager.is(ClientManager.Client.Nagram)) {
                                        HideSeen.saveReadHistory(object);
                                    }
                                    if (ConfigManager.hideOnline.isEnable()) {
                                        if (isOnlineRequest(object))
                                            XposedHelpers.setBooleanField(object, Obfuscate.getFieldName("TL_account$updateStatus", "offline"), true);
                                    }

                                    if (ConfigManager.hideSeen.isEnable() && HideSeen.isReadMessageRequest(object)) {
                                        HideSeen.sendFakeReadResponse(object, param.args[1], param.args[2]);
                                        param.setResult(null);
                                        return;
                                    }

                                    if (ConfigManager.hideTyping.isEnable() && HideTyping.isTypingRequest(object)) {
                                        param.setResult(null);
                                        return;
                                    }

                                    if (ConfigManager.hideStoryView.isEnable() && HideStoryRead.isReadStoriesRequest(object)) {
                                        param.setResult(null);
                                        return;
                                    }

                                    HideSeen.attachReadAfterSend(param);
                                }
                            } catch (Throwable e) {
                                Logger.e(e);
                            }
                        }
                    }));
                    if (isEnable) Logger.l("Ghost mode request hook installed");
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
