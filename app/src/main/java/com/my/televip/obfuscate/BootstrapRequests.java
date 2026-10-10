package com.my.televip.obfuscate;

import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.logging.Logger;
import com.my.televip.compat.XposedHelpers;
import com.my.televip.virtuals.messenger.Utilities;
import java.lang.reflect.*;
import java.util.*;

/** Hold outgoing requests until privacy hooks exist; scanning must not create a read-receipt gap. */
public final class BootstrapRequests {
    private static boolean released;
    private static boolean watchdogStarted;

    public static synchronized void startWatchdog() {
        if (watchdogStarted) return;
        watchdogStarted = true;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            synchronized (pending) { if (released) return; }
            com.my.televip.diagnostics.PrivacyStatus.report("startup-timeout", "Startup timed out; selective privacy fallback active");
            release();
        }, 20000);
    }
    private static final StartupRequestQueue<Pending> pending=new StartupRequestQueue<>(1024);
    private BootstrapRequests() {}
    private static final class Pending {
        final Object connection; final Method method; final Object[] args;
        Pending(Object connection,Method method,Object[] args) {this.connection=connection;this.method=method;this.args=args;}
    }
    public static void install(ClassLoader loader) {
        Class<?> manager=XposedHelpers.findClassIfExists("org.telegram.tgnet.ConnectionsManager",loader);
        Class<?> request=XposedHelpers.findClassIfExists("org.telegram.tgnet.TLObject",loader);
        if(manager==null || request==null) {
            com.my.televip.diagnostics.PrivacyStatus.report("bootstrap", "Startup request gate unavailable on this client");return;
        }
        Method target = StartupDispatchSelector.find(manager, request);
        if (target == null) {
            com.my.televip.diagnostics.PrivacyStatus.report("bootstrap", "Startup request gate unavailable or ambiguous");return;
        }
        final Method dispatch=target;
        dispatch.setAccessible(true);
        boolean installed = HMethod.hookMethod(dispatch,new BaseMethodHook() {
            @Override protected void beforeMethod(MethodHookParam param) {
                StartupRequestQueue.Result result=pending.offer(param.thisObject,(int)param.args[9],
                        new Pending(param.thisObject,dispatch,param.args.clone()));
                if (result == StartupRequestQueue.Result.QUEUED) {
                    param.setResult(null);
                } else {
                    if (result == StartupRequestQueue.Result.FULL) {
                        com.my.televip.diagnostics.PrivacyStatus.report("startup-capacity", "Startup queue full; selective privacy fallback active");
                        release();
                    }
                    if (!com.my.televip.features.ghostMode.GhostMode.isEnable)
                        com.my.televip.features.ghostMode.PrivacyProtection.intercept(param);
                }
            }
        });
        if (!installed) {
            com.my.televip.diagnostics.PrivacyStatus.report("bootstrap", "Startup request gate installation failed");return;
        }
        com.my.televip.diagnostics.PrivacyStatus.clear("bootstrap");
        // The final int of the queued internal send is its public sendRequest token.
        for(Method method:manager.getDeclaredMethods()) {
            if(!method.getName().equals("cancelRequest") || !Arrays.equals(method.getParameterTypes(),new Class<?>[]{int.class,boolean.class})) continue;
            HMethod.hookMethod(method,new BaseMethodHook() {
                @Override protected void beforeMethod(MethodHookParam param) {
                    pending.cancel(param.thisObject,(int)param.args[0]);
                }
            });
        }
        Logger.l("Bootstrap network gate installed: " + dispatch.getName());
    }
    public static void release() {
        synchronized(pending) {if(released)return;released=true;pending.release();}
        int count=pending.size();
        if(count==0) return;
        com.my.televip.features.ghostMode.PrivacyProtection.post(()-> {
            Pending call;
            while((call=pending.poll())!=null) try {call.method.invoke(call.connection,call.args);}
            catch(Throwable error) {Logger.e(error instanceof InvocationTargetException ? error.getCause():error);}
        });
        Logger.l("Bootstrap released "+count+" requests through privacy protection");
    }
}
