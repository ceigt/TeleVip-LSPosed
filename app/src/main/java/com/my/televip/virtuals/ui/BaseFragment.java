package com.my.televip.virtuals.ui;

import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.messenger.UserConfig;

import com.my.televip.compat.XposedHelpers;

public class BaseFragment {

    Object baseFragment;

    public BaseFragment(Object obj){
        baseFragment = obj;
    }

    public UserConfig getUserConfig(){
        return new UserConfig(XposedHelpers.callMethod(baseFragment, Obfuscate.getMethodName("BaseFragment", "getUserConfig")));
    }

}
