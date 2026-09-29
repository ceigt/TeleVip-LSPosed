package com.my.televip.hooks;

import com.my.televip.Clients.ClientManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.Obfuscate;

import java.lang.reflect.Method;

import com.my.televip.compat.XposedBridge;
import com.my.televip.compat.XposedHelpers;

public class HMethod {

    public static void hookMethod(Class<?> cls, String name, Object... args) {
        try {
            if (cls != null) {
                XposedHelpers.findAndHookMethod(cls, name, args);
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    public static void hookMethod(Class<?> cls, String className, String[] names, Object... args) {
        try {
            if (cls != null) {
                for (String name : names) {
                    if (ClientManager.is(ClientManager.Client.Nagram) && name.equals("formatPmEditedDate")) continue;
                    XposedHelpers.findAndHookMethod(cls, Obfuscate.getMethodName(className, name), args);
                }
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }
    public static void hookMethod(Method method, BaseMethodHook callback) {
        try {
            if (method != null) {
                XposedBridge.hookMethod(method, callback);
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

}
