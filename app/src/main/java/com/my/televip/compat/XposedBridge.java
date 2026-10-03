package com.my.televip.compat;

import android.util.Log;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.diagnostics.HookHealth;

/** Adapts existing feature callbacks to the API 102 interceptor chain. */
public final class XposedBridge {
    private static XposedModule module;

    private XposedBridge() {}

    public static void setModule(XposedModule value) { module = value; }

    public static void log(String message) {
        if (module != null) module.log(Log.INFO, "TeleVip", message);
        else Log.i("TeleVip", message);
    }

    public static void hookMethod(Executable executable, XC_MethodHook callback) {
        if (module == null) throw new IllegalStateException("API 102 module not ready");
        HookInstallation.install(executable, callback.getClass(), () -> module.hook(executable).intercept(chain -> {
            XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
            param.method = chain.getExecutable();
            param.thisObject = chain.getThisObject();
            param.args = chain.getArgs().toArray();
            callback.beforeHookedMethod(param);
            if (!param.isReturnEarly()) {
                try { param.setOriginalResult(chain.proceed(param.args)); }
                catch (Throwable error) { param.setOriginalThrowable(error); }
            }
            callback.afterHookedMethod(param);
            if (param.hasThrowable()) throw param.getThrowable();
            return param.getResult();
        }));
    }

    public static Set<Executable> hookAllMethods(Class<?> type, String name, XC_MethodHook callback) {
        Set<Executable> hooked = new HashSet<>();
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                hookMethod(method, callback);
                hooked.add(method);
                HookHealth.resolved();
            }
        }
        if (hooked.isEmpty()) {HookInstallation.failure();HookHealth.missingMember(type.getName()+"#"+name); }
        return hooked;
    }
}
