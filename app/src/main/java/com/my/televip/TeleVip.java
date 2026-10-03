package com.my.televip;

import static com.my.televip.obfuscate.ArgsResolver.resolverRegistry;

import com.my.televip.Configs.ConfigManager;
import com.my.televip.Clients.ClientManager;
import com.my.televip.application.AndroidUtilities;
import com.my.televip.dex.DexInjector;
import com.my.televip.language.Translator;
import com.my.televip.logging.Logger;
import com.my.televip.settings.SettingsManager;
import com.my.televip.settings.NativeSettingsEntry;
import com.my.televip.settings.controller.SettingsController;
import com.my.televip.utils.Utils;
import com.my.televip.virtuals.TeleVip.Bridge.Bridge;

public class TeleVip {
    
    /** Privacy and storage hooks must also start in a service-only process. */
    public static boolean startBackgroundHooks() {
        try {
            resolverRegistry.loadParameter();
            Translator.init();
            AndroidUtilities.init();
            ConfigManager.loadAndRead();
            return true;
        } catch (Throwable error) {
            Logger.e(error);
            return false;
        }
    }

    public static boolean startHook() {
        try {
            resolverRegistry.loadParameter();
            Translator.init();
            AndroidUtilities.init();
            if (ClientManager.is(ClientManager.Client.Telegram)) {
                ConfigManager.loadAndRead();
                NativeSettingsEntry.init();
                return true;
            }
            DexInjector.injectDex(Utils.classLoader);

            SettingsController settingsController = new SettingsController();

            Bridge.init(settingsController);
            ConfigManager.loadAndRead();
            if (!NativeSettingsEntry.init()) SettingsManager.init(settingsController);
            return true;

        } catch (Throwable e){
            Logger.e(e);
            return false;
        }

    }

}
