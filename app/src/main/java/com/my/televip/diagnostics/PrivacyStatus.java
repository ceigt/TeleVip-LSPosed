package com.my.televip.diagnostics;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import com.my.televip.language.Translator;
import com.my.televip.logging.Logger;
import com.my.televip.utils.Utils;
import java.util.LinkedHashMap;
import java.util.Map;

/** Keep protection failures visible without interrupting ordinary network traffic. */
public final class PrivacyStatus {
    private static final Map<String, String> issues = new LinkedHashMap<>();
    private static long revision, shown;
    private static final Handler ui = new Handler(Looper.getMainLooper());
    private PrivacyStatus() {}

    public static void report(String key, String detail) {
        synchronized (issues) {
            if (issues.containsKey(key)) return;
            issues.put(key, detail);
            revision++;
        }
        Logger.w("Protection degraded: " + key + ": " + detail);
        ui.post(PrivacyStatus::notifyActivity);
    }

    public static void clear(String key) {
        synchronized (issues) { issues.remove(key); }
    }

    public static boolean hasIssues() { synchronized (issues) { return !issues.isEmpty(); } }

    public static String warning() {
        String text = Translator.get("ProtectionWarning");
        if (!"ProtectionWarning".equals(text)) return text;
        // A mapping/startup failure can also make the host locale wrapper unavailable.
        return java.util.Locale.getDefault().getLanguage().equals("zh")
                ? "TeleVip 部分保护未能正常启用。正常联网已恢复，已识别且开启保护的隐私请求仍会被拦截，但无法确认全部保护有效。请重启 Telegram；若提示仍出现，请更新 TeleVip。"
                : "Some TeleVip protection could not be initialized or completed. Normal networking is available. Recognized, enabled privacy requests remain suppressed, but full protection is not confirmed. Restart Telegram; if this continues, update TeleVip.";
    }

    public static void notifyActivity() {
        if (Looper.myLooper() != Looper.getMainLooper()) { ui.post(PrivacyStatus::notifyActivity); return; }
        Activity activity = Utils.getCurrentActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        long version;
        synchronized (issues) {
            if (issues.isEmpty() || shown == revision) return;
            version = revision;
        }
        try {
            new AlertDialog.Builder(activity).setTitle("TeleVip")
                    .setMessage(warning()).setPositiveButton(android.R.string.ok, null).show();
            synchronized (issues) { shown = version; }
        } catch (RuntimeException error) { Logger.e(error); }
    }
}
