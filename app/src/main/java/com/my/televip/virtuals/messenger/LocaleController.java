package com.my.televip.virtuals.messenger;

import com.my.televip.Class.ClassNames;
import com.my.televip.Class.ClassLoad;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;

import java.util.Locale;

import com.my.televip.compat.XposedHelpers;

public class LocaleController {

    Object localeController;

    public LocaleController(){
        localeController = XposedHelpers.callStaticMethod(ClassLoad.getClass(ClassNames.LOCALE_CONTROLLER), Obfuscate.getMethodName("LocaleController", "getInstance"));
    }

    public Locale getCurrentLocale() {
        return (Locale) XposedHelpers.getObjectField(localeController, Obfuscate.getFieldName("LocaleController", "currentLocale"));
    }

    public static boolean isRTL() {
        return (boolean) XposedHelpers.getStaticBooleanField(ClassLoad.getClass(ClassNames.LOCALE_CONTROLLER), Obfuscate.getFieldName("LocaleController", "isRTL"));
    }

}
