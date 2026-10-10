package com.my.televip.features.ghostMode;

import android.content.Context;
import android.content.SharedPreferences;
import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigItem;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.application.ApplicationLoaderHook;
import com.my.televip.compat.XC_MethodHook.MethodHookParam;
import com.my.televip.compat.XposedHelpers;
import com.my.televip.diagnostics.PrivacyStatus;
import com.my.televip.language.Keys;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.messenger.Utilities;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** The same request policy is used by normal hooks and the startup fallback. */
public final class PrivacyProtection {
    private static volatile Map<Class<?>, PrivacyRequestKind> types = Collections.emptyMap();
    private static final String[] REQUESTS = {
        ClassNames.TL_MESSAGES_READ_HISTORY, ClassNames.TL_MESSAGES_READ_DISCUSSION,
        ClassNames.TL_MESSAGES_READ_ENCRYPTED_HISTORY, ClassNames.TL_MESSAGES_READ_MESSAGE_CONTENTS,
        ClassNames.TL_CHANNELS_READ_HISTORY, ClassNames.TL_CHANNELS_READ_MESSAGE_CONTENTS,
        ClassNames.TL_MESSAGES_SET_TYPING, ClassNames.TL_MESSAGES_SET_ENCRYPTED_TYPING,
        ClassNames.TL_ACCOUNT_UPDATE_STATUS, ClassNames.TL_STORIES_READ_STORIES, ClassNames.TL_STORIES_INCREMENT_STORY_VIEWS
    };
    private PrivacyProtection() {}

    public static void prepare() {
        Map<Class<?>, PrivacyRequestKind> found = new HashMap<>();
        for (String name : REQUESTS) {
            Class<?> type = ClassLoad.getClass(name);
            PrivacyRequestKind kind = PrivacyRequestKind.named(name);
            if (type != null) found.put(type, kind);
            else if (wanted(kind)) PrivacyStatus.report("request:" + name, "Privacy request type unavailable");
        }
        types = Collections.unmodifiableMap(found);
    }

    private static boolean enabled(ConfigItem item, String key) {
        if (item != null) return item.isEnable();
        try {
            Context context = ApplicationLoaderHook.getApplicationContext();
            SharedPreferences prefs = context.getSharedPreferences("TeleVip", Context.MODE_PRIVATE);
            return prefs.getBoolean(key, false);
        } catch (Throwable failure) {
            PrivacyStatus.report("configuration", "Cannot read privacy configuration");
            // If preferences cannot be read, protect known privacy traffic until restart.
            return true;
        }
    }

    private static boolean privateRead() { return enabled(ConfigManager.hideSeenPrivateChat, Keys.HideSeenPrivateChat); }
    private static boolean groupRead() { return enabled(ConfigManager.hideSeenChannel, Keys.HideSeenChannel); }
    private static boolean wanted(PrivacyRequestKind kind) {
        switch (kind) {
            case READ_DIALOG: return privateRead() || groupRead();
            case READ_DISCUSSION: case READ_CHANNEL: return groupRead();
            case READ_PRIVATE: case READ_ENCRYPTED: return privateRead();
            case TYPING: return enabled(ConfigManager.hideTyping, Keys.HideTyping);
            case ONLINE: return enabled(ConfigManager.hideOnline, Keys.HideOnline);
            case STORY_READ: case STORY_VIEWS: return enabled(ConfigManager.hideStoryView, Keys.HideStoryView);
            default: return false;
        }
    }

    public static void intercept(MethodHookParam param) {
        Object request = param.args[0];
        if (request == null) return;
        if (com.my.televip.Clients.ClientManager.is(com.my.televip.Clients.ClientManager.Client.Nagram)) {
            try { HideSeen.saveReadHistory(request); }
            catch (Throwable failure) { PrivacyStatus.report("read-history-type", "Read request template unavailable"); Logger.e(failure); }
        }
        if (HideSeen.consumeReadPermit(request, param.thisObject)) return;
        PrivacyRequestKind kind = PrivacyRequestKind.named(request.getClass().getName());
        if (kind == PrivacyRequestKind.OTHER) kind = types.getOrDefault(request.getClass(), kind);
        if (wanted(kind)) {
            if (kind == PrivacyRequestKind.READ_DIALOG && privateRead() != groupRead()) {
                try {
                    Object peer = XposedHelpers.getObjectField(request, Obfuscate.getFieldName("TLRPC$TL_messages_readHistory", "peer"));
                    boolean group = XposedHelpers.getLongField(peer, Obfuscate.getFieldName("TLRPC$InputPeer", "channel_id")) != 0
                            || XposedHelpers.getLongField(peer, Obfuscate.getFieldName("TLRPC$InputPeer", "chat_id")) != 0;
                    if (group ? !groupRead() : !privateRead()) return;
                } catch (Throwable failure) {
                    // A known read request with an unreadable peer must not slip through.
                    PrivacyStatus.report("read-peer", "Read request peer unavailable; receipt suppressed");
                }
            }
            if (kind == PrivacyRequestKind.ONLINE) {
                try {
                    XposedHelpers.setBooleanField(request, Obfuscate.getFieldName("TL_account$updateStatus", "offline"), true);
                    return;
                } catch (Throwable failure) {
                    PrivacyStatus.report("online-field", "Online status field unavailable; update suppressed");
                }
            }
            // Suppress before constructing or dispatching the callback: callback failure cannot leak a receipt.
            param.setResult(null);
            complete(request, kind, param.args[1], param.args[2]);
            return;
        }
        if (ConfigManager.hideSeen == null) return;
        try { HideSeen.attachReadAfterSend(param); }
        catch (Throwable failure) { PrivacyStatus.report("read-after-send", "Read after sending unavailable"); Logger.e(failure); }
    }

    public static void post(Runnable task) {
        try { Utilities.getStageQueue().postRunnable(task); }
        catch (Throwable failure) {
            PrivacyStatus.report("stage-queue", "Host callback queue unavailable; using main queue");
            new android.os.Handler(android.os.Looper.getMainLooper()).post(task);
        }
    }

    public static void complete(Object request, PrivacyRequestKind kind, Object normal, Object timestamp) {
        Object callback = normal != null ? normal : timestamp;
        if (callback == null) return;
        try {
            Object response;
            if (kind == PrivacyRequestKind.STORY_READ) {
                response = XposedHelpers.newInstance(ClassLoad.getClass("org.telegram.tgnet.Vector"), new Object[]{null});
            } else if (kind == PrivacyRequestKind.READ_DIALOG || kind == PrivacyRequestKind.READ_PRIVATE) {
                response = XposedHelpers.newInstance(ClassLoad.getClass(ClassNames.TL_MESSAGES_AFFECTED));
                XposedHelpers.setIntField(response, Obfuscate.getFieldName("TLRPC$TL_messages_affectedMessages", "pts"), -1);
                XposedHelpers.setIntField(response, Obfuscate.getFieldName("TLRPC$TL_messages_affectedMessages", "pts_count"), 0);
            } else response = XposedHelpers.newInstance(ClassLoad.getClass("org.telegram.tgnet.TLRPC$TL_boolTrue"));
            dispatch(callback, response, null, normal == null);
        } catch (Throwable failure) {
            PrivacyStatus.report("completion", "Local privacy response unavailable; request remains suppressed");
            Logger.e(failure);
            try {
                Object error = XposedHelpers.newInstance(ClassLoad.getClass("org.telegram.tgnet.TLRPC$TL_error"));
                XposedHelpers.setIntField(error, "code", 503);
                XposedHelpers.setObjectField(error, "text", "TELEVIP_PRIVACY_UNAVAILABLE");
                dispatch(callback, null, error, normal == null);
            } catch (Throwable second) { Logger.e(second); }
        }
    }

    private static void dispatch(Object callback, Object response, Object error, boolean timestamp) throws Exception {
        Method method = PrivacyCallbacks.find(callback, response, error, timestamp);
        post(() -> {
            try { PrivacyCallbacks.invoke(method, callback, response, error); }
            catch (Throwable failure) { PrivacyStatus.report("callback", "Privacy callback failed"); Logger.e(failure); }
        });
    }
}
