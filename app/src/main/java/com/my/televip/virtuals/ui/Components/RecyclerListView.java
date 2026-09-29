package com.my.televip.virtuals.ui.Components;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.dex.DexInjector;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.androidx.Adapter;

import com.my.televip.compat.XposedHelpers;

public class RecyclerListView {

    public final Object recyclerListView;

    public RecyclerListView(Context context){
        if (DexInjector.classLoader != null) {
            recyclerListView = XposedHelpers.newInstance(ClassLoad.getClass(ClassNames.SETTINGS_ADAPTER_RECYCLER_LIST_VIEW, DexInjector.classLoader), context);
        } else {
            recyclerListView = new RecyclerView(context);
        }
    }

    public void setAdapter(Object adapter) {
        XposedHelpers.callMethod(recyclerListView, Obfuscate.getMethodName("RecyclerListView", "setAdapter"), adapter);
    }

    public Adapter getAdapter() {
        return new Adapter(XposedHelpers.callMethod(recyclerListView, Obfuscate.getMethodName("RecyclerListView", "getAdapter")));
    }

    public void setBackgroundColor(int color) {
        ((View)recyclerListView).setBackgroundColor(color);
    }

    public void setVerticalScrollBarEnabled(boolean b) {
        ((View)recyclerListView).setVerticalScrollBarEnabled(b);
    }

    public void setLayoutManager(Object layout) {
        XposedHelpers.callMethod(recyclerListView, Obfuscate.getMethodName("RecyclerListView", "setLayoutManager"), layout);
    }

    public View getRecyclerListView() {
       return (View) recyclerListView;
    }

}
