package com.my.televip.virtuals.androidx;

import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;

import java.util.ArrayList;

import com.my.televip.compat.XposedHelpers;

public class LongSparseArray {

    Object longSparseArray;

    public LongSparseArray(Object longSparseArray) {
        this.longSparseArray = longSparseArray;
    }

    public ArrayList<Object> get(long id){
        return (ArrayList<Object>) XposedHelpers.callMethod(longSparseArray, Obfuscate.getMethodName("LongSparseArray", "get"), id);
    }
}
