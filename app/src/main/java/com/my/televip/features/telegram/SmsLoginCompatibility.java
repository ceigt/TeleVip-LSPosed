package com.my.televip.features.telegram;

import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.logging.Logger;
import java.lang.reflect.Field;

public final class SmsLoginCompatibility {
    private static volatile boolean installed;
    private static final ThreadLocal<Integer> feeScope = new ThreadLocal<>();
    private SmsLoginCompatibility() {}
    public static synchronized void init() {
        if (installed) return;
        try (HookInstallation attempt = HookInstallation.begin()) {
            Field allowFirebase = Host.field("TLRPC$TL_codeSettings", "allow_firebase");
            Field verified = Host.field("TLRPC$auth_SentCodeType", "verifiedFirebase");
            HMethod.hookMethod(Host.method("TLRPC$TL_codeSettings", "serializeToStream", 1), new BaseMethodHook() {
                @Override protected void beforeMethod(MethodHookParam call) throws Exception {
                    if (installed && Host.enabled(ConfigManager.smsLoginCompatibility)) allowFirebase.setBoolean(call.thisObject, false);
                }
            });
            HMethod.hookMethod(Host.method("TLRPC$TL_auth_sentCodeTypeFirebaseSms", "readParams", 2), new BaseMethodHook() {
                @Override protected void afterMethod(MethodHookParam call) throws Exception {
                    if (!call.hasThrowable() && (installed && Host.enabled(ConfigManager.smsLoginCompatibility)))
                        verified.setBoolean(call.thisObject, true);
                }
            });
            Field safetyNet = Host.field("BuildVars", "SAFETYNET_KEY");
            Field passkeys = Host.field("BuildVars", "SUPPORTS_PASSKEYS");
            // Loaded before the first login page. Restarting restores the original profile when disabled.
            java.lang.reflect.Method feePage = Host.method("SmsFeeView", "setParams", 2);
            if (!com.my.televip.compat.XposedBridge.deoptimize(feePage))
                throw new IllegalStateException("Cannot prepare SMS invoice caller");
            HMethod.hookMethod(feePage, new BaseMethodHook() {
                @Override protected void beforeMethod(MethodHookParam call) {
                    int depth = feeScope.get() == null ? 0 : feeScope.get();
                    feeScope.set(depth + 1);
                }
                @Override protected void afterMethod(MethodHookParam call) {
                    int depth = feeScope.get() == null ? 0 : feeScope.get();
                    if (depth <= 1) feeScope.remove(); else feeScope.set(depth - 1);
                }
            });
            HMethod.hookMethod(Host.method("BuildVars", "useInvoiceBilling", 0), new BaseMethodHook() {
                @Override protected void beforeMethod(MethodHookParam call) {
                    if (feeScope.get() != null && (installed && Host.enabled(ConfigManager.smsLoginCompatibility)))
                        call.setResult(true);
                }
            });
            installed = attempt.isComplete();
            if (installed) {
                safetyNet.set(null, "");
                passkeys.setBoolean(null, false);
            }
        } catch (Throwable error) { Logger.e(error); }
    }
}
