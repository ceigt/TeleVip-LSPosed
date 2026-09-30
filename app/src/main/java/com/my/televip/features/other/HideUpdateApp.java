package com.my.televip.features.other;

import android.app.Activity;
import android.content.SharedPreferences;

import com.my.televip.Class.ClassNames;
import com.my.televip.Class.ClassLoad;
import com.my.televip.application.ApplicationLoaderHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;

import com.my.televip.compat.XC_MethodReplacement;

public class HideUpdateApp {

    public static volatile boolean isEnable = false;

    public static synchronized void init() {
        try (HookInstallation attempt = HookInstallation.begin()) {
            if (!isEnable) {

                SharedPreferences preferences = ApplicationLoaderHook.getApplicationContext().getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                preferences.edit().remove("appUpdate").apply();
                preferences.edit().remove("appUpdateCheckTime").apply();
                preferences.edit().remove("appUpdateBuild").apply();

                if (attempt.require(ClassLoad.getClass(ClassNames.SHARED_CONFIG))) {

                    HMethod.hookMethod(
                            ClassLoad.getClass(ClassNames.SHARED_CONFIG),
                            Obfuscate.getMethodName("SharedConfig", "setNewAppVersionAvailable"),
                            ArgsResolver.merge("setNewAppVersionAvailable", new Class[]{ClassLoad.getClass(ClassNames.TL_HELP_APP_UPDATE)}, new XC_MethodReplacement() {
                                @Override
                                protected Object replaceHookedMethod(MethodHookParam param) {
                                    return false;
                                }
                            }));

                    HMethod.hookMethod(
                            ClassLoad.getClass(ClassNames.SHARED_CONFIG),
                            Obfuscate.getMethodName("SharedConfig", "isAppUpdateAvailable"), new XC_MethodReplacement() {
                                @Override
                                protected Object replaceHookedMethod(MethodHookParam param) {
                                    return false;
                                }
                            });
                }
                isEnable = attempt.isComplete();
            }
        } catch (Throwable t){
            Logger.e(t);
        }
    }
}
