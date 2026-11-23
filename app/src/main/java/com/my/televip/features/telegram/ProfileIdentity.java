package com.my.televip.features.telegram;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.compat.XposedHelpers;
import com.my.televip.language.Translator;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.ui.ProfileActivity;

public final class ProfileIdentity {
    private ProfileIdentity() {}
    public static int dc(Object fragment) throws ReflectiveOperationException {
        ProfileActivity profile = new ProfileActivity(fragment);
        long id = profile.getUserId();
        if (id <= 0) return 0;
        Object controller = XposedHelpers.callMethod(fragment,
                Obfuscate.getMethodName("BaseFragment", "getMessagesController"));
        Object user = Host.method("MessagesController", "getUser", 1).invoke(controller, Long.valueOf(id));
        if (user == null) return 0;
        Object photo = Host.field("TLRPC$User", "photo").get(user);
        if (photo != null) {
            int dc = Host.field("TLRPC$UserProfilePhoto", "dc_id").getInt(photo);
            if (dc > 0) return dc;
        }
        if (id != profile.getBaseFragment().getUserConfig().getClientUserId()) return 0;
        Object manager = XposedHelpers.callMethod(fragment,
                Obfuscate.getMethodName("BaseFragment", "getConnectionsManager"));
        return (int) Host.method("ConnectionsManager", "getCurrentDatacenterId", 0).invoke(manager);
    }
    public static boolean enabled() { return Host.enabled(ConfigManager.profileDc); }
    public static void copyMenu(Activity activity, String id, int dc) {
        if (activity == null || activity.isFinishing()) return;
        String[] values = dc > 0 ? new String[]{id, String.valueOf(dc), "ID: " + id + ", DC: " + dc}
                : new String[]{id};
        String[] labels = dc > 0 ? new String[]{Translator.get("CopyUserId"),
                Translator.get("CopyDc") + " " + dc, Translator.get("CopyIdDc")}
                : new String[]{Translator.get("CopyUserId")};
        new AlertDialog.Builder(activity).setTitle("ID / DC").setItems(labels, (dialog, which) ->
                ((ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE))
                        .setPrimaryClip(ClipData.newPlainText("ID / DC", values[which]))).show();
    }
}
