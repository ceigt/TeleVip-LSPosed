package com.my.televip.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageInfo;
import com.my.televip.diagnostics.HookHealth;
import com.my.televip.diagnostics.PrivacyStatus;
import com.my.televip.language.Translator;
import com.my.televip.messages.MessageStorage;
import com.my.televip.application.AndroidUtilities;
import java.io.FileInputStream;
import java.security.MessageDigest;

/** Local evidence only. No upload, network request or claim of server-side testing. */
public final class CompatibilityStatus {
    private CompatibilityStatus() {}
    public static void show(Activity activity) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(Translator.get("CompatibilityStatus"))
                .setMessage(Translator.get("CheckingCompatibility"))
                .setPositiveButton(android.R.string.ok, null).show();
        String hooks = HookHealth.report();
        String privacy = PrivacyStatus.hasIssues() ? PrivacyStatus.warning() : Translator.get("NoPrivacyHookIssue");
        MessageStorage.post(() -> {
            String text;
            try {
                PackageInfo host = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (FileInputStream in = new FileInputStream(activity.getApplicationInfo().sourceDir)) {
                    byte[] buffer = new byte[65536]; int n;
                    while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
                }
                StringBuilder hash = new StringBuilder();
                for (byte b : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
                boolean verified = activity.getPackageName().equals("org.telegram.messenger")
                        && hash.toString().equals("faadd72f465182732edee2026a2148f63875e8ba4d1d5a0c0d7b782c4f0bc762");
                text = "Telegram " + host.versionName + " (" + (android.os.Build.VERSION.SDK_INT>=28?host.getLongVersionCode():host.versionCode) + ")\nTeleVip " + com.my.televip.BuildConfig.VERSION_NAME
                        + "\n\n" + Translator.get(verified ? "VerifiedApkChecks" : "UnknownApkChecks")
                        + "\n\n" + hooks + "\n\n" + privacy
                        + (com.my.televip.features.messages.LocalData.lastIssue==null?"":"\n\n"+Translator.get(com.my.televip.features.messages.LocalData.lastIssue))
                        + "\n\n" + Translator.get("CompatibilityLimits");
            } catch (Exception error) { text = Translator.get("UnknownApkChecks") + "\n\n" + hooks; }
            String result = text;
            AndroidUtilities.runOnUIThread(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed() && dialog.isShowing()) dialog.setMessage(result);
            });
        });
    }
}
