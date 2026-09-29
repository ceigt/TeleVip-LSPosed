package com.my.televip.virtuals.ActionBar;

import android.text.TextPaint;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.obfuscate.Obfuscate;

import com.my.televip.compat.XposedHelpers;

public class Theme {

    public static TextPaint getTextPaint()
    {
        return (TextPaint) XposedHelpers.getStaticObjectField(ClassLoad.getClass(ClassNames.THEME), Obfuscate.getFieldName("Theme", "chat_timePaint"));
    }

    public static boolean isLight(){
        return !((boolean)XposedHelpers.callStaticMethod(ClassLoad.getClass(ClassNames.THEME), Obfuscate.getMethodName("Theme", "isCurrentThemeDark")));
    }
}
