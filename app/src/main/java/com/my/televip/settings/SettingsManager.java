package com.my.televip.settings;

import com.my.televip.Clients.ClientManager;
import com.my.televip.Clients.Turrit;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.settings.controller.SettingsController;
import com.my.televip.settings.hook.SettingsHook;
import com.my.televip.settings.ui.SettingsActivity;
import com.my.televip.utils.Utils;

import com.my.televip.compat.XposedHelpers;

public class SettingsManager {

    public static void init(SettingsController settingsController) {

        SettingsActivity.init(settingsController);

        Class<?> SettingsActivityClass = XposedHelpers.findClassIfExists(
                Obfuscate.getClassName("org.telegram.ui.SettingsActivity"),
                Utils.classLoader
        );

        Class<?> SettingsActivity$SettingCell$FactoryClass = XposedHelpers.findClassIfExists(
                Obfuscate.getClassName("org.telegram.ui.SettingsActivity$SettingCell$Factory"),
                Utils.classLoader
        );

        SettingsHook settings = new SettingsHook();
        if (SettingsActivityClass != null && SettingsActivity$SettingCell$FactoryClass != null) {
            settings.newSettings(SettingsActivityClass, SettingsActivity$SettingCell$FactoryClass, settingsController);
        } else {
            settings.oldSettings(settingsController);
        }
        if (ClientManager.is(ClientManager.Client.ForkgramClassic)) settings.oldSettings(settingsController);
        if (ClientManager.is(ClientManager.Client.Turrit)) Turrit.showGhostModeDialog(settingsController);
    }

}
