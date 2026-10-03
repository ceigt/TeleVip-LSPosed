package com.my.televip.application;

import android.content.Context;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.obfuscate.Obfuscate;

import com.my.televip.compat.XposedHelpers;

public class ApplicationLoaderHook {

    private static volatile Context applicationContext;

    public static void setApplicationContext(Context context) {
        applicationContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
    }

    public static Context getApplicationContext() {
        if (applicationContext == null) {
            applicationContext = (Context) XposedHelpers.getStaticObjectField(
                    ClassLoad.getClass(ClassNames.APPLICATION_LOADER),
                    Obfuscate.getFieldName("ApplicationLoader", "applicationContext")
            );
        }
        return applicationContext;
    }
}