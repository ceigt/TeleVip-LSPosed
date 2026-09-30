package com.my.televip.features.media;

import com.my.televip.Class.ClassNames;
import com.my.televip.Clients.ClientManager;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.Class.ClassLoad;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.logging.Logger;
import com.my.televip.virtuals.ui.SecretMediaViewer;

import com.my.televip.compat.XposedHelpers;

public class PreventMedia {

    public static volatile boolean isEnable = false;

    public static synchronized void init() {
        try (HookInstallation attempt = HookInstallation.begin()) {
            if (!isEnable) {

                if (attempt.require(ClassLoad.getClass(ClassNames.CHAT_ACTIVITY))) {
                    if (ClientManager.is(ClientManager.Client.Telegram)) {
                        // Telegram 12.10.5 moved secret read scheduling into ChatActivity.db.
                        HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_ACTIVITY), "db",
                                ClassLoad.getClass(ClassNames.MESSAGE_OBJECT), boolean.class, new BaseMethodHook() {
                                    @Override
                                    protected void beforeMethod(MethodHookParam param) {
                                        if (ConfigManager.preventMedia.isEnable()) param.setResult(null);
                                    }
                                });
                        // View-once deletion is now created by the static N4 helper.
                        HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_ACTIVITY), "N4",
                                ClassLoad.getClass(ClassNames.CHAT_ACTIVITY), ClassLoad.getClass(ClassNames.MESSAGE_OBJECT), new BaseMethodHook() {
                                    @Override
                                    protected void beforeMethod(MethodHookParam param) {
                                        if (ConfigManager.preventMedia.isEnable()) param.setResult(null);
                                    }
                                });
                    } else {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_ACTIVITY), Obfuscate.getMethodName("ChatActivity", "sendSecretMessageRead"), ArgsResolver.merge("sendSecretMessageRead", new Class[]{ClassLoad.getClass(ClassNames.MESSAGE_OBJECT), boolean.class}, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (ConfigManager.preventMedia.isEnable()) param.setResult(null);
                        }
                    }));
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_ACTIVITY), Obfuscate.getMethodName("ChatActivity", "sendSecretMediaDelete"), ArgsResolver.merge("sendSecretMediaDelete", new Class[]{ClassLoad.getClass(ClassNames.MESSAGE_OBJECT)}, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (ConfigManager.preventMedia.isEnable()) param.setResult(null);
                        }
                    }));
                    }
                }

                if (attempt.require(ClassLoad.getClass(ClassNames.SECRET_MEDIA_VIEWER))) {
                    if (!ClientManager.is(ClientManager.Client.Telegram)) SecretMediaViewer.openMedia();
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.SECRET_MEDIA_VIEWER), Obfuscate.getMethodName("SecretMediaViewer", "closePhoto"), ArgsResolver.merge("closePhoto", new Class[]{boolean.class, boolean.class}, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (ConfigManager.preventMedia.isEnable()) {
                                Object thisObject = param.thisObject;
                                XposedHelpers.setObjectField(thisObject, Obfuscate.getFieldName("SecretMediaViewer", "onClose"), null);
                            }
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
