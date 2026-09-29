package com.my.televip.virtuals.Adapters;

import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;

import java.util.ArrayList;

import com.my.televip.compat.XposedHelpers;

public class DrawerLayoutAdapter {

    private final Object drawerLayout;

    public DrawerLayoutAdapter(Object obj){
        drawerLayout = obj;
    }

    public ArrayList<?> getItems(){
        return (ArrayList<?>) XposedHelpers.getObjectField(drawerLayout, Obfuscate.getFieldName("DrawerLayoutAdapter", "items"));
    }

}
