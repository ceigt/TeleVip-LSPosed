package com.my.televip.virtuals.ui;

import android.widget.FrameLayout;

import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;

import com.my.televip.compat.XposedHelpers;

public class LaunchActivity {

    Object launchActivity;
    public FrameLayout frameLayout;

    public LaunchActivity(Object obj){
       launchActivity = obj;
       frameLayout = (FrameLayout) XposedHelpers.getObjectField(obj, Obfuscate.getFieldName("LaunchActivity","frameLayout"));
    }

}
