package com.my.televip.Clients;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.hooks.HMethod;
import com.my.televip.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.my.televip.compat.XC_MethodReplacement;

public class Telegraph {

    public static class ParameterResolver
    {
        static Map<String,Class<?>[]> objectList = new HashMap<>();

        public static void register(String name,  Class<?>[] classes){
            objectList.put(name, classes);
        }

        public static Class<?>[] resolve(String name) {
            return objectList.get(name);
        }

        public static boolean has(String name)
        {
            boolean has = false;
           Class<?>[] classes = objectList.get(name);
           if (classes != null){
               has = true;
           }
            return has;
        }
    }

    public static void loadParameter() {
        ParameterResolver.register("fillMessageMenu",new Class[]{ClassLoad.getClass(ClassNames.MESSAGE_OBJECT), ClassLoad.getClass(ClassNames.MESSAGE_OBJECT), ArrayList.class, ArrayList.class, ArrayList.class});
    }

    public static void removeAd(){
        try {
            Class<?> connectionsManager = ClassLoad.getClass(ClassNames.CONNECTIONS_MANAGER);
            if (connectionsManager != null) {
                HMethod.hookMethod(connectionsManager, "native_expireFile", long.class, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        return false;
                    }
                });
                HMethod.hookMethod(connectionsManager, "native_daysFile", long.class, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        return 999;
                    }
                });
                HMethod.hookMethod(connectionsManager, "native_checkLicense", long.class, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        return true;
                    }
                });
                HMethod.hookMethod(connectionsManager, "native_removeInstance", int.class, boolean.class, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        return true;
                    }
                });
            }
        } catch (Throwable t){
            Logger.e(t);
        }
    }

}
