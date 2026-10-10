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
import com.my.televip.diagnostics.PrivacyStatus;
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
    private boolean retryScheduled;
    private final Handler ui = new Handler(Looper.getMainLooper());
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
                BootstrapRequests.startWatchdog();
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
                if(packageName.equals(activity.getPackageName())) {Utils.setCurrentActivity(activity);queue(activity);PrivacyStatus.notifyActivity();}
            }
        });
    }
    private void queue(Context activity) {
        if (queued || attempts >= 3 || started && privacyReady()) return;
        if(RuntimeMappings.isReady()) {start();return;}
        queued=true;
        RuntimeMappings.whenReady(activity.getApplicationContext(),()->{queued=false;start();});
    }
    private boolean privacyReady() {
        return ConfigManager.hideSeen != null && (!ConfigManager.isGhostMode() || GhostMode.isEnable);
    }
    private void retry() {
        if (retryScheduled) return;
        retryScheduled = true;
        ui.postDelayed(() -> {
            retryScheduled = false;
            queue(ApplicationLoaderHook.getApplicationContext());
        }, 1000);
    }
    private void start() {
        if (started && privacyReady() || attempts >= 3) return;
        boolean background = Utils.getCurrentActivity() == null;
        if (background) {
            if (backgroundStarted && privacyReady() || backgroundAttempts >= 3) return;
            backgroundAttempts++;
            backgroundStarted = TeleVip.startBackgroundHooks();
        } else {
            attempts++;
            started = TeleVip.startHook();
        }
        if (privacyReady()) PrivacyStatus.clear("privacy-init");
        else PrivacyStatus.report("privacy-init", "Privacy initialization incomplete; selective fallback active");
        // The permanent bootstrap interceptor protects known privacy requests when the feature hook fails.
        // Never retain all traffic merely because a settings/UI hook is unavailable.
        BootstrapRequests.release();
        boolean complete = background ? backgroundStarted && privacyReady() : started && privacyReady();
        if (complete) {
            PrivacyStatus.clear("startup-timeout");
            if (!background) PrivacyStatus.clear("settings-init");
        } else {
            if (!background && !started) PrivacyStatus.report("settings-init", "Settings initialization incomplete; retrying");
            if ((background ? backgroundAttempts : attempts) < 3) retry();
        }
        Logger.l((background ? "Background" : "Startup") + " ready=" + complete + "; " + RuntimeMappings.summary());
        HookHealth.logReport();
    }
}
