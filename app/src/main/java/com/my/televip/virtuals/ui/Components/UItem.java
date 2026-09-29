package com.my.televip.virtuals.ui.Components;

import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.utils.Utils;

import com.my.televip.compat.XposedHelpers;

public class UItem {

    Object uItem;

    public UItem(Object uItem){
        this.uItem = uItem;
    }

    public int getID(){
        return XposedHelpers.getIntField(uItem, Obfuscate.getFieldName("UItem", "id"));
    }

    public String getText(){
        return Utils.getFieldAsString(XposedHelpers.getObjectField(uItem, Obfuscate.getFieldName("UItem", "text")));
    }

    public String getSubtext(){
        return Utils.getFieldAsString(XposedHelpers.getObjectField(uItem, Obfuscate.getFieldName("UItem", "subtext")));
    }

    public Object getUItem(){
        return uItem;
    }
}
