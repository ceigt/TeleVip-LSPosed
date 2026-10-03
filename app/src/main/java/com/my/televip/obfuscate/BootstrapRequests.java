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
    private static final StartupRequestQueue<Pending> pending=new StartupRequestQueue<>(1024);
    private BootstrapRequests() {}
    private static final class Pending {
        final Object connection; final Method method; final Object[] args;
        Pending(Object connection,Method method,Object[] args) {this.connection=connection;this.method=method;this.args=args;}
    }
    public static void install(ClassLoader loader) {
        Class<?> manager=XposedHelpers.findClassIfExists("org.telegram.tgnet.ConnectionsManager",loader);
        Class<?> request=XposedHelpers.findClassIfExists("org.telegram.tgnet.TLObject",loader);
        if(manager==null || request==null) {Logger.w("Bootstrap request gate unavailable on this client");return;}
        Method target = StartupDispatchSelector.find(manager, request);
        if (target == null) {Logger.w("Bootstrap request gate unavailable or ambiguous"); return;}
        final Method dispatch=target;
        dispatch.setAccessible(true);
        boolean installed = HMethod.hookMethod(dispatch,new BaseMethodHook() {
            @Override protected void beforeMethod(MethodHookParam param) {
                StartupRequestQueue.Result result=pending.offer(param.thisObject,(int)param.args[9],
                        new Pending(param.thisObject,dispatch,param.args.clone()));
                if(result!=StartupRequestQueue.Result.RELEASED) {
                    if(result==StartupRequestQueue.Result.FULL) Logger.w("Bootstrap request capacity reached; request suppressed");
                    param.setResult(null);
                }
            }
        });
        if (!installed) {Logger.w("Bootstrap request gate installation failed"); return;}
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
        Utilities.getStageQueue().postRunnable(()-> {
            Pending call;
            while((call=pending.poll())!=null) try {call.method.invoke(call.connection,call.args);}
            catch(Throwable error) {Logger.e(error instanceof InvocationTargetException ? error.getCause():error);}
        });
        Logger.l("Bootstrap released "+count+" requests through installed privacy hooks");
    }
}
