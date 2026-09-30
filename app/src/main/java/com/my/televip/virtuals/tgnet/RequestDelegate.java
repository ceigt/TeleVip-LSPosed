package com.my.televip.virtuals.tgnet;

import com.my.televip.Class.ClassNames;
import com.my.televip.utils.Utils;
import com.my.televip.Class.ClassLoad;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;

import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import com.my.televip.logging.Logger;

import com.my.televip.compat.XposedHelpers;

public class RequestDelegate {

    public Object requestDelegate;

    public RequestDelegate(Object obj){
        requestDelegate = obj;
    }

    public void run(Object response, Object error){
        XposedHelpers.callMethod(requestDelegate, Obfuscate.getMethodName("RequestDelegate", "run"), response, error);
    }


    @FunctionalInterface
    public interface requestDelegate {
        void run(Object response, Object error);
    }

    public static Object run(requestDelegate lambda) {
        Class<?> requestDelegateClass = ClassLoad.getClass(ClassNames.REQUEST_DELEGATE);
        if (requestDelegateClass != null) {
            return Proxy.newProxyInstance(
                    Utils.classLoader,
                    new Class[]{requestDelegateClass},
                    (proxy, method, args) -> {
                        if (method.getParameterCount() == 2 && method.getParameterTypes()[0] == ClassLoad.getClass(ClassNames.TL_OBJECT)) {
                            lambda.run(args[0], args[1]);
                        }
                        return null;
                    }
            );
        }
        return null;
    }

    public static Object afterSuccess(Object original, Class<?> type, Runnable action) {
        if (type == null) throw new IllegalArgumentException("Callback type missing");
        return Proxy.newProxyInstance(Utils.classLoader, new Class[]{type}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                if ("equals".equals(method.getName())) return proxy == args[0];
                return "TeleVip request callback";
            }
            Object result = null;
            if (original != null) {
                try { result = method.invoke(original, args); }
                catch (InvocationTargetException error) { throw error.getCause(); }
            }
            if (args != null && args.length >= 2 && args[0] != null && args[1] == null) {
                try { action.run(); } catch (Throwable error) { Logger.e(error); }
            }
            return result;
        });
    }
}
