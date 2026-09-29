package com.my.televip.base;

import com.my.televip.logging.Logger;

import com.my.televip.compat.XC_MethodHook;

public abstract class BaseMethodHook extends XC_MethodHook {

    protected void beforeMethod(MethodHookParam param) throws Throwable {}

    protected void afterMethod(MethodHookParam param) throws Throwable {}


    @Override
    protected final void beforeHookedMethod(MethodHookParam param) {
        try {
            beforeMethod(param);
        } catch (Throwable e) {
            onError(e);
        }
    }


    @Override
    protected final void afterHookedMethod(MethodHookParam param) {
        try {
            afterMethod(param);
        } catch (Throwable e) {
            onError(e);
        }
    }


    protected void onError(Throwable throwable) {
        Logger.e(throwable);
    }
}