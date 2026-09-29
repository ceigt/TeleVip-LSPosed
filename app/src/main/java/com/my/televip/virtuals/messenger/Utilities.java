package com.my.televip.virtuals.messenger;

import com.my.televip.Class.ClassNames;
import com.my.televip.Class.ClassLoad;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;

import com.my.televip.compat.XposedHelpers;

public class Utilities {

    public static DispatchQueue getStageQueue(){
        return new DispatchQueue(XposedHelpers.getStaticObjectField(ClassLoad.getClass(ClassNames.UTILITIES), Obfuscate.getFieldName("Utilities", "stageQueue")));
    }
}
