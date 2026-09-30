package com.my.televip.features.ui;

import android.view.MotionEvent;

import com.my.televip.Class.ClassNames;
import com.my.televip.Class.ClassLoad;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.ui.ChatActivity;

public class DisableChannelSwipeBack {
    public static volatile boolean isEnable = false;

    public static synchronized void init() {
        try (HookInstallation attempt = HookInstallation.begin()) {
            if (!isEnable) {
                if (attempt.require(ClassLoad.getClass(ClassNames.CHAT_ACTIVITY))) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_ACTIVITY), Obfuscate.getMethodName("ChatActivity", "isSwipeBackEnabled"), ArgsResolver.merge("isSwipeBackEnabled", new Class[]{MotionEvent.class}, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (ConfigManager.disableChannelSwipeBack.isEnable()
                                    && new ChatActivity(param.thisObject).isBroadcastChannel()) {
                                param.setResult(false);
                            }
                        }
                    }));
                }
                isEnable = attempt.isComplete();
                if (isEnable) Logger.l("Channel-only swipe hook installed");
            }
        } catch (Throwable e){
            Logger.e(e);
        }
    }

}
