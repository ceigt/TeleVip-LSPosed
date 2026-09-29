package com.my.televip.virtuals.androidx;

import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;

import com.my.televip.compat.XposedHelpers;

public class Adapter {

    private final Object adapter;

    public Adapter(Object adapter){
        this.adapter = adapter;
    }

    public void notifyItemChanged(int position) {
        XposedHelpers.callMethod(adapter, Obfuscate.getMethodName("RecyclerListView", "notifyItemChanged"), position);
    }

}
