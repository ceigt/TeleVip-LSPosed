package com.my.televip;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.my.televip.application.ApplicationLoaderHook;
import android.os.Bundle;
import com.my.televip.Clients.ClientManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.diagnostics.HookHealth;
import com.my.televip.hooks.HMethod;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.RuntimeMappings;
import com.my.televip.obfuscate.BootstrapRequests;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.features.ghostMode.GhostMode;
import com.my.televip.utils.Utils;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainHook {
    private static final AtomicBoolean attached = new AtomicBoolean();
    private boolean started, queued, backgroundStarted;
    private int backgroundAttempts;
    private int attempts;
    public void handleLoadPackage(String packageName,ClassLoader loader) {
        if (!ClientManager.containsPackage(packageName,loader) || !attached.compareAndSet(false,true)) return;
        Utils.classLoader=loader;Utils.pkgName=packageName;
        BootstrapRequests.install(loader);
        RuntimeMappings.prefetch(packageName,loader);
        HMethod.hookMethod(Application.class,"attach",Context.class,new BaseMethodHook() {
            @Override protected void afterMethod(MethodHookParam param) {
                Context context = (Context)param.args[0];
                if (!packageName.equals(context.getPackageName())) return;
                ApplicationLoaderHook.setApplicationContext((Application)param.thisObject);
                // Run after the host Application.onCreate has finished, even without an Activity.
                new Handler(Looper.getMainLooper()).post(() -> queue(context));
            }
        });
        HMethod.hookMethod(Activity.class,"onCreate",Bundle.class,new BaseMethodHook() {
            @Override protected void beforeMethod(MethodHookParam param) {
                Activity activity=(Activity)param.thisObject;
                if(!packageName.equals(activity.getPackageName())) return;
                Utils.setCurrentActivity(activity);queue(activity);
            }
        });
        HMethod.hookMethod(Activity.class,"onResume",new BaseMethodHook() {
            @Override protected void afterMethod(MethodHookParam param) {
                Activity activity=(Activity)param.thisObject;
                if(packageName.equals(activity.getPackageName())) {Utils.setCurrentActivity(activity);queue(activity);}
            }
        });
    }
    private void queue(Context activity) {
        if(started || queued || attempts>=3) return;
        if(RuntimeMappings.isReady()) {start();return;}
        queued=true;
        RuntimeMappings.whenReady(activity.getApplicationContext(),()->{queued=false;start();});
    }
    private void start() {
        if (started || attempts >= 3) return;
        if (Utils.getCurrentActivity() == null) {
            if (backgroundStarted || backgroundAttempts >= 3) return;
            backgroundAttempts++;
            backgroundStarted = TeleVip.startBackgroundHooks();
            if (backgroundStarted && (!ConfigManager.isGhostMode() || GhostMode.isEnable)) BootstrapRequests.release();
            else Logger.w("Background request gate retained: privacy hooks incomplete");
            Logger.l("Background hooks ready=" + backgroundStarted + "; " + RuntimeMappings.summary());
            HookHealth.logReport();
            return;
        }
        attempts++;started=TeleVip.startHook();
        if(started && (!ConfigManager.isGhostMode() || GhostMode.isEnable)) BootstrapRequests.release();
        else Logger.w("Startup request gate retained: privacy hooks incomplete");
        Logger.l("Startup ready="+started+"; "+RuntimeMappings.summary());HookHealth.logReport();
    }
}
