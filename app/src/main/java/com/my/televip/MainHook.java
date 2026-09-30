package com.my.televip;


import android.app.Activity;
import android.os.Bundle;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Clients.ClientManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.settings.SettingsFallback;
import com.my.televip.utils.Utils;
import com.my.televip.compat.XposedHelpers;

import java.util.ArrayList;

public class MainHook {

    private boolean isStart;
    private int startAttempts;

    public void handleLoadPackage(final String packageName, final ClassLoader classLoader) {
        if (!ClientManager.containsPackage(packageName, classLoader)) return;

        Utils.classLoader = classLoader;
        Utils.pkgName = packageName;

        HMethod.hookMethod(ClassLoad.getClass(ClassNames.LAUNCH_ACTIVITY), "onCreate", Bundle.class, new BaseMethodHook() {
            @Override
            protected void beforeMethod(MethodHookParam param) {
                Utils.setCurrentActivity((Activity) param.thisObject);
                if (!isStart && startAttempts < 3) {
                    startAttempts++;
                    isStart = TeleVip.startHook();
                }
            }
        });

        if (ClientManager.is(ClientManager.Client.Telegram)) {
            Class<?> settings = ClassLoad.getClass("org.telegram.ui.SettingsActivity", classLoader, false);
            if (settings != null) {
                Class<?> row = ClassLoad.getClass("org.telegram.ui.Components.k61", classLoader, false);
                HMethod.hookMethod(settings, "b0", settings, ArrayList.class, new BaseMethodHook() {
                    @Override
                    protected void afterMethod(MethodHookParam param) {
                        ArrayList<Object> items = (ArrayList<Object>) param.args[1];
                        for (Object item : items)
                            if (XposedHelpers.getIntField(item, "d") == SettingsFallback.ROW_ID) return;
                        Class<?> nativeRow = ClassLoad.getClass("org.telegram.ui.l91", classLoader, false);
                        int icon = ((Activity) Utils.getCurrentActivity()).getResources()
                                .getIdentifier("settings_features", "drawable", packageName);
                        Object entry = XposedHelpers.callStaticMethod(nativeRow, "a",
                                SettingsFallback.ROW_ID, -1007845, -1996271, icon,
                                "TeleVip", "TeleVip settings", null);
                        int index = -1;
                        for (int i = 0; i < items.size(); i++) {
                            if (XposedHelpers.getIntField(items.get(i), "d") == 10) {
                                index = i + 1;
                                break;
                            }
                        }
                        if (index >= 0) items.add(index, entry);
                    }
                });
                if (row != null) HMethod.hookMethod(settings, "f0", settings, row, new BaseMethodHook() {
                    @Override
                    protected void beforeMethod(MethodHookParam param) {
                        if (XposedHelpers.getIntField(param.args[1], "d") != SettingsFallback.ROW_ID) return;
                        Activity activity = Utils.getCurrentActivity();
                        if (activity != null) SettingsFallback.showSettings(activity);
                        param.setResult(null);
                    }
                });
            }
        }
    }


}

