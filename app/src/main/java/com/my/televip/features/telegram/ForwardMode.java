package com.my.televip.features.telegram;

import android.app.Activity;
import android.app.AlertDialog;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.Configs.ConfigPreferences;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.language.Translator;
import com.my.televip.logging.Logger;
import com.my.televip.utils.Utils;
import com.my.televip.virtuals.messenger.BaseController;
import java.lang.reflect.Method;
import java.util.ArrayList;

public final class ForwardMode {
    private static volatile boolean installed;
    private static final ThreadLocal<Boolean> sending = new ThreadLocal<>();
    private ForwardMode() {}
    public static synchronized void init() {
        if (installed) return;
        try (HookInstallation attempt = HookInstallation.begin()) {
            Method send = Host.method("SendMessagesHelper", "sendForward", 12);
            HMethod.hookMethod(send, new BaseMethodHook() {
                @Override protected void beforeMethod(MethodHookParam call) {
                    if (!Host.enabled(ConfigManager.forwardMode) || Boolean.TRUE.equals(sending.get())
                            || Boolean.TRUE.equals(call.args[2]) || !(call.args[0] instanceof ArrayList)
                            || ((ArrayList<?>) call.args[0]).isEmpty()) return;
                    Activity activity = Utils.getCurrentActivity();
                    if (activity == null || activity.isFinishing()) return;
                    Object sender = call.thisObject;
                    long account = new BaseController(sender).getUserConfig().getClientUserId();
                    if (account == 0) return;
                    Object[] args = call.args.clone();
                    args[0] = new ArrayList<>((ArrayList<?>) args[0]);
                    String preference = "ForwardFromName_" + account;
                    int[] selected = {ConfigPreferences.getBoolean(preference) ? 1 : 0};
                    call.setResult(0);
                    new AlertDialog.Builder(activity)
                            .setTitle(Translator.get("ForwardMode"))
                            .setSingleChoiceItems(new String[]{Translator.get("NormalForward"),
                                    Translator.get("SourceFreeForward")}, selected[0],
                                    (dialog, which) -> selected[0] = which)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(Translator.get("ContinueForward"), (dialog, which) -> {
                                if (new BaseController(sender).getUserConfig().getClientUserId() != account) return;
                                ConfigPreferences.putBoolean(preference, selected[0] == 1);
                                args[2] = selected[0] == 1;
                                sending.set(true);
                                try { send.invoke(sender, args); }
                                catch (Throwable error) { Logger.e(error); }
                                finally { sending.remove(); }
                            }).show();
                }
            });
            installed = attempt.isComplete();
        } catch (Throwable error) { Logger.e(error); }
    }
}
