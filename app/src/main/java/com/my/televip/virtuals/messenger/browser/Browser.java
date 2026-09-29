package com.my.televip.virtuals.messenger.browser;

import android.content.Context;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;

import com.my.televip.compat.XposedHelpers;

public class Browser {

    public static void openUrl(Context context, String url){
        XposedHelpers.callStaticMethod(
                ClassLoad.getClass(ClassNames.BROWSER),
                Obfuscate.getMethodName("Browser", "openUrl"), context, url
        );
    }

}
