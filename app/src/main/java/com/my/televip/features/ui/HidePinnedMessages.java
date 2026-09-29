package com.my.televip.features.ui;

import android.view.View;

import com.my.televip.Class.ClassNames;
import com.my.televip.Clients.ClientManager;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.Class.ClassLoad;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.logging.Logger;
import com.my.televip.virtuals.ui.ChatActivity;

public class HidePinnedMessages {
    public static boolean isEnable = false;

    public static void init() {
        try {
            if (!isEnable) {
                isEnable = true;
                if (ClassLoad.getClass(ClassNames.CHAT_ACTIVITY) != null) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_ACTIVITY),
                            Obfuscate.getMethodName("ChatActivity", "createPinnedMessageView"), new BaseMethodHook() {
                                @Override
                                protected void afterMethod(MethodHookParam param) {
                                    if (ConfigManager.hidePinnedMessages.isEnable()) {
                                        View button = new ChatActivity(param.thisObject).getPinnedMessageView();
                                        if (button != null && button.getVisibility() != View.GONE)
                                            button.setVisibility(View.GONE);
                                    }
                                }
                            });
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_ACTIVITY), Obfuscate.getMethodName("ChatActivity", "updatePinnedMessageView"), ClientManager.is(ClientManager.Client.Telegram) ? int.class : boolean.class, ClientManager.is(ClientManager.Client.Telegram) ? boolean.class : int.class, new BaseMethodHook() {
                        @Override
                        protected void afterMethod(MethodHookParam param) {
                            if (ConfigManager.hidePinnedMessages.isEnable()) {
                                View button = new ChatActivity(param.thisObject).getPinnedMessageView();
                                if (button != null && button.getVisibility() != View.GONE)
                                    button.setVisibility(View.GONE);
                            }
                        }
                    });
                }
            }
        } catch (Throwable e){
            Logger.e(e);
        }
    }

}
