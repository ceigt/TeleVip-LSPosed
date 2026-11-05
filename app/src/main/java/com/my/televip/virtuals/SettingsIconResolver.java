package com.my.televip.virtuals;

import android.content.Context;
import com.my.televip.application.ApplicationLoaderHook;
import com.my.televip.utils.Utils;

public final class SettingsIconResolver {
    private static Integer cachedIcon;
    public static int getIconSettings() {
        if (cachedIcon != null) return cachedIcon;
        Context context = ApplicationLoaderHook.getApplicationContext();
        if (context == null) return 0;
        for (String name : new String[]{"msg_settings", "msg_settings_old", "msg_settings_ny", "msg_settings_14", "msg_settings_hw"}) {
            int icon = context.getResources().getIdentifier(name, "drawable", Utils.pkgName);
            if (icon != 0) { cachedIcon = icon; return icon; }
        }
        return 0;
    }
}
