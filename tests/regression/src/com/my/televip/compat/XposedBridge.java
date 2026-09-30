package com.my.televip.compat;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.my.televip.hooks.HookInstallation;
public final class XposedBridge {
    public static final AtomicInteger installations = new AtomicInteger();
    private static final Map<Executable,List<XC_MethodHook>> callbacks = new HashMap<>();
    public static synchronized void hookMethod(Executable member,XC_MethodHook callback) {
        HookInstallation.install(member,callback.getClass(),() -> {
            installations.incrementAndGet();
            callbacks.computeIfAbsent(member,k -> new ArrayList<>()).add(callback);
        });
    }
    public static synchronized Object invoke(Method member,Object object,Object... args) throws Throwable {
        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.method=member; param.thisObject=object; param.args=args;
        List<XC_MethodHook> hooks=callbacks.getOrDefault(member,Collections.emptyList());
        for (XC_MethodHook callback:hooks) callback.beforeHookedMethod(param);
        if (!param.isReturnEarly()) param.setOriginalResult(member.invoke(object,param.args));
        for (int n=hooks.size()-1;n>=0;n--) hooks.get(n).afterHookedMethod(param);
        if (param.hasThrowable()) throw param.getThrowable();
        return param.getResult();
    }
}
