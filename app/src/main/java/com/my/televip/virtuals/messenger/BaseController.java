package com.my.televip.virtuals.messenger;

import com.my.televip.obfuscate.Obfuscate;

import com.my.televip.compat.XposedHelpers;

public class BaseController {

    Object baseController;

    public BaseController(Object obj){baseController = obj;}

    public int getCurrentAccount() {
        return XposedHelpers.getIntField(baseController, Obfuscate.getFieldName("BaseController", "currentAccount"));
    }

    public UserConfig getUserConfig(){
        return new UserConfig(XposedHelpers.callMethod(baseController, Obfuscate.getMethodName("BaseController", "getUserConfig")));
    }

}
