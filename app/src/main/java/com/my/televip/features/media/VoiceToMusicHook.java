package com.my.televip.features.media;


import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.messenger.MessageObject;

public class VoiceToMusicHook {

    public static volatile boolean isEnable = false;
    public static synchronized void init() {
        try (HookInstallation attempt = HookInstallation.begin()) {
            if (!isEnable) {

                HMethod.hookMethod(ClassLoad.getClass(ClassNames.MESSAGE_OBJECT),  Obfuscate.getMethodName("MessageObject", "isMusic"), new BaseMethodHook() {
                    @Override
                    protected void beforeMethod(MethodHookParam param) {
                        if (!ConfigManager.enableVoiceMessageSaving.isEnable()) return;
                        MessageObject messageObject = new MessageObject(param.thisObject);
                        if (messageObject.isVoice()) param.setResult(true);
                    }
                });
                isEnable = attempt.isComplete();
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

}
