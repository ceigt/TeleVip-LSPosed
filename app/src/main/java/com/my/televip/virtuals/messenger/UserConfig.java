package com.my.televip.virtuals.messenger;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.tgnet.TLRPC;

import com.my.televip.compat.XposedHelpers;

public class UserConfig {

    Object userConfig;

    public UserConfig(Object obl){
        userConfig = obl;
    }

    public static int getSelectedAccount() {
        String selectedAccountField = Obfuscate.getFieldName("UserConfig", "selectedAccount");
        return XposedHelpers.getStaticIntField(ClassLoad.getClass(ClassNames.USER_CONFIG), selectedAccountField);
    }

    public long getClientUserId(){
        return XposedHelpers.getLongField(userConfig, Obfuscate.getFieldName("UserConfig" , "clientUserId"));
    }

    public TLRPC.User getCurrentUser(){
        return new TLRPC.User(XposedHelpers.callMethod(userConfig, Obfuscate.getMethodName("UserConfig" , "getCurrentUser")));
    }

    public static UserConfig getInstance(int num) {
        return new UserConfig(XposedHelpers.callStaticMethod(ClassLoad.getClass(ClassNames.USER_CONFIG), Obfuscate.getMethodName("UserConfig", "getInstance"), num));
    }

    public static UserConfig getUserConfig() {
        return getInstance(getSelectedAccount());
    }

    public Object get_UserConfig() {
        return userConfig;
    }

}
