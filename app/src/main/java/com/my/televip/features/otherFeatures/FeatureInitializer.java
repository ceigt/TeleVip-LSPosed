package com.my.televip.features.otherFeatures;

import com.my.televip.logging.Logger;
public final class FeatureInitializer {
    public static synchronized void init() {
        ChatHook.init("live-chat"); ProfileHook.init("live-profile");
        Logger.l("Menu hooks: chat="+ChatHook.isInitialized()+", profile="+ProfileHook.isInitialized());
    }
}
