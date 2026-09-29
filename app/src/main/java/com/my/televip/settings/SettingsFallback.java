package com.my.televip.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.my.televip.Configs.ConfigItem;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.language.Keys;
import com.my.televip.language.Translator;
import com.my.televip.logging.Logger;

import java.util.List;

/** Settings dialog opened from Telegram's native settings list. */
public final class SettingsFallback {
    public static final int ROW_ID = 1001;

    private SettingsFallback() {}

    public static void showSettings(Activity activity) {
        try {
            ScrollView scroll = new ScrollView(activity);
            LinearLayout list = new LinearLayout(activity);
            list.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(activity, 16);
            list.setPadding(pad, pad / 2, pad, pad / 2);
            scroll.addView(list);
            for (ConfigItem item : ConfigManager.getItems()) addItem(activity, list, item, 0);
            new AlertDialog.Builder(activity)
                    .setTitle("TeleVip")
                    .setView(scroll)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } catch (Throwable t) {
            Logger.e(t);
            Toast.makeText(activity, "TeleVip settings could not open", Toast.LENGTH_SHORT).show();
        }
    }

    private static void addItem(Activity activity, LinearLayout list, ConfigItem item, int indent) {
        if (item == null) return;
        int type = item.getType();
        if (type == ConfigItem.EXPANDABLE_SWITCH) {
            addLabel(activity, list, Translator.get(item.getKey()), indent);
            List<ConfigItem> children = item.getChildren();
            if (children != null) for (ConfigItem child : children) addItem(activity, list, child, indent + 1);
        } else if (type == ConfigItem.HEADER) {
            addLabel(activity, list, Translator.get(item.getKey()), 0);
        } else if (type == ConfigItem.SWITCH) {
            Switch toggle = new Switch(activity);
            toggle.setText(Translator.get(item.getKey()));
            toggle.setTextSize(15);
            toggle.setChecked(item.isEnable());
            toggle.setPadding(dp(activity, indent * 12), dp(activity, 8), 0, dp(activity, 8));
            toggle.setOnCheckedChangeListener((button, checked) -> {
                item.setEnable(checked);
                if (checked) {
                    try { item.run(); } catch (Throwable t) { Logger.e(t); }
                }
                if (item.isRestartRequired())
                    Toast.makeText(activity, "Restart Telegram to apply", Toast.LENGTH_SHORT).show();
            });
            list.addView(toggle);
        } else if (type == ConfigItem.TEXT) {
            Button action = new Button(activity);
            action.setAllCaps(false);
            action.setText(Translator.get(item.getKey()));
            if (Keys.Calendar.equals(item.getKey())) {
                String[] choices = {Translator.get(Keys.Gregorian), Translator.get(Keys.Hijri), Translator.get(Keys.Persian)};
                int selected = item.getCustomCalendar();
                if (selected >= 0 && selected < choices.length)
                    action.setText(Translator.get(Keys.Calendar) + ": " + choices[selected]);
            }
            action.setOnClickListener(view -> runTextAction(activity, item, action));
            list.addView(action);
        } else if (type == ConfigItem.INFO) {
            TextView info = new TextView(activity);
            info.setText(Translator.get(item.getKey()));
            info.setPadding(dp(activity, indent * 12), dp(activity, 8), 0, dp(activity, 8));
            list.addView(info);
        } else if (type == ConfigItem.DIVIDER) {
            View divider = new View(activity);
            divider.setBackgroundColor(0x334267a5);
            list.addView(divider, new LinearLayout.LayoutParams(-1, dp(activity, 1)));
        }
    }

    private static void runTextAction(Activity activity, ConfigItem item, Button action) {
        try {
            if (Keys.Calendar.equals(item.getKey())) {
                String[] choices = {Translator.get(Keys.Gregorian), Translator.get(Keys.Hijri), Translator.get(Keys.Persian)};
                new AlertDialog.Builder(activity)
                        .setTitle(Translator.get(Keys.Calendar))
                        .setSingleChoiceItems(choices, item.getCustomCalendar(), (dialog, which) -> {
                            item.setCustomCalendar(which);
                            item.run();
                            action.setText(Translator.get(Keys.Calendar) + ": " + choices[which]);
                            dialog.dismiss();
                        }).show();
            } else if (Keys.DeveloperChannel.equals(item.getKey())) {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/t_l0_e")));
            } else if (Keys.RestartApp.equals(item.getKey())) {
                Intent launch = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    activity.startActivity(launch);
                    activity.finishAffinity();
                    android.os.Process.killProcess(android.os.Process.myPid());
                }
            }
        } catch (Throwable t) {
            Logger.e(t);
            Toast.makeText(activity, "TeleVip action failed", Toast.LENGTH_SHORT).show();
        }
    }

    private static void addLabel(Activity activity, LinearLayout list, String label, int indent) {
        TextView heading = new TextView(activity);
        heading.setText(label);
        heading.setTextSize(16);
        heading.setTextColor(0xff4267a5);
        heading.setPadding(dp(activity, indent * 12), dp(activity, 14), 0, dp(activity, 4));
        list.addView(heading);
    }

    private static int dp(Activity activity, float value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
