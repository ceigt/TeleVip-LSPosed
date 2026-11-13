package com.my.televip.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.Context;
import android.content.res.Configuration;
import android.net.Uri;
import android.view.View;
import android.view.ContextThemeWrapper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.text.Editable;
import android.text.TextWatcher;

import com.my.televip.Configs.ConfigItem;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.language.Keys;
import com.my.televip.language.Translator;
import com.my.televip.logging.Logger;
import com.my.televip.virtuals.ActionBar.Theme;

import java.util.List;

/** Settings dialog opened from Telegram's native settings list. */
public final class SettingsFallback {
    public static final int ROW_ID = 1001;

    private SettingsFallback() {}

    public static void showSettings(Activity activity) {
        try {
            boolean dark = isDark(activity);
            Context ui = new ContextThemeWrapper(activity, dark
                    ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                    : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
            LinearLayout outer = new LinearLayout(ui);
            outer.setOrientation(LinearLayout.VERTICAL);
            EditText search = new EditText(ui);
            search.setSingleLine(true);
            search.setHint(Translator.get("SearchSettings"));
            search.setContentDescription(Translator.get("SearchSettings"));
            outer.addView(search);
            java.util.ArrayList<String> sections = new java.util.ArrayList<>();
            sections.add(Translator.get("AllSettings"));
            for (ConfigItem item : ConfigManager.getItems())
                if (item.getType() == ConfigItem.HEADER) sections.add(Translator.get(item.getKey()));
            Spinner category = new Spinner(ui);
            category.setContentDescription(Translator.get("SettingsCategory"));
            category.setAdapter(new ArrayAdapter<>(ui, android.R.layout.simple_spinner_dropdown_item, sections));
            outer.addView(category);
            ScrollView scroll = new ScrollView(ui);
            LinearLayout list = new LinearLayout(ui);
            list.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(activity, 16);
            list.setPadding(pad, pad / 2, pad, pad / 2);
            scroll.addView(list);
            outer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            if (com.my.televip.diagnostics.PrivacyStatus.hasIssues()) {
                TextView warning = new TextView(ui);
                warning.setText(com.my.televip.diagnostics.PrivacyStatus.warning());
                warning.setTextColor(dark ? 0xffffb4ab : 0xffba1a1a);
                warning.setPadding(0, pad, 0, pad);
                outer.addView(warning);
            }
            Runnable render = () -> {
                list.removeAllViews();
                int section = 0;
                for (ConfigItem item : ConfigManager.getItems()) {
                    if (item.getType() == ConfigItem.HEADER) section++;
                    if (category.getSelectedItemPosition() != 0 && category.getSelectedItemPosition() != section) continue;
                    addFiltered(activity, list, item, 0, dark, search.getText().toString(), sections.get(section));
                }
            };
            search.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
                public void onTextChanged(CharSequence s,int start,int before,int count) { render.run(); }
                public void afterTextChanged(Editable e) {}
            });
            category.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parent,View view,int position,long id) { render.run(); }
                public void onNothingSelected(AdapterView<?> parent) {}
            });
            render.run();
            new AlertDialog.Builder(ui)
                    .setTitle("TeleVip")
                    .setView(outer)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } catch (Throwable t) {
            Logger.e(t);
            Toast.makeText(activity, "TeleVip settings could not open", Toast.LENGTH_SHORT).show();
        }
    }

    private static String description(ConfigItem item) {
        if (item.getKey() == null) return "";
        String key = "Desc_" + item.getKey();
        String value = Translator.get(key);
        return key.equals(value) ? "" : value;
    }
    private static void addFiltered(Activity activity, LinearLayout list, ConfigItem item, int indent, boolean dark, String query, String section) {
        if (item == null) return;
        if (item.getType() == ConfigItem.EXPANDABLE_SWITCH) {
            boolean visible=false;
            for(ConfigItem child:item.getChildren())
                if(SettingsSearch.matches(query,Translator.get(child.getKey()),description(child),section,Translator.get(item.getKey())))visible=true;
            if(!visible)return;
            addLabel(activity,list,Translator.get(item.getKey()),indent);
            for (ConfigItem child : item.getChildren())
                addFiltered(activity,list,child,indent+1,dark,query,section+" "+Translator.get(item.getKey()));
            return;
        }
        if (!query.trim().isEmpty() && (item.getType() == ConfigItem.HEADER || item.getType() == ConfigItem.DIVIDER)) return;
        if (!SettingsSearch.matches(query, item.getKey()==null?"":Translator.get(item.getKey()), description(item), section)) return;
        addItem(activity,list,item,indent,dark);
        if (!description(item).isEmpty()) {
            TextView help = new TextView(list.getContext());
            help.setText(description(item));
            help.setTextSize(12);
            help.setPadding(dp(activity,indent*12),0,0,dp(activity,8));
            list.addView(help);
        }
    }

    private static void addItem(Activity activity, LinearLayout list, ConfigItem item, int indent, boolean dark) {
        if (item == null) return;
        int type = item.getType();
        if (type == ConfigItem.EXPANDABLE_SWITCH) {
            addLabel(activity, list, Translator.get(item.getKey()), indent);
            List<ConfigItem> children = item.getChildren();
            if (children != null) for (ConfigItem child : children) addItem(activity, list, child, indent + 1, dark);
        } else if (type == ConfigItem.HEADER) {
            addLabel(activity, list, Translator.get(item.getKey()), 0);
        } else if (type == ConfigItem.SWITCH) {
            Android16Switch toggle = new Android16Switch(list.getContext(), dark);
            toggle.setText(Translator.get(item.getKey()));
            toggle.setTextSize(15);
            toggle.setChecked(item.isEnable());
            toggle.setPaddingRelative(dp(activity, indent * 12), dp(activity, 8), 0, dp(activity, 8));
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
            Button action = new Button(list.getContext());
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
            TextView info = new TextView(list.getContext());
            info.setText(Translator.get(item.getKey()));
            info.setPadding(dp(activity, indent * 12), dp(activity, 8), 0, dp(activity, 8));
            list.addView(info);
        } else if (type == ConfigItem.DIVIDER) {
            View divider = new View(list.getContext());
            divider.setBackgroundColor(0x334267a5);
            list.addView(divider, new LinearLayout.LayoutParams(-1, dp(activity, 1)));
        }
    }

    private static void runTextAction(Activity activity, ConfigItem item, Button action) {
        try {
            if ("CompatibilityStatus".equals(item.getKey())) CompatibilityStatus.show(activity);
            else if ("ArchivedMessages".equals(item.getKey())) com.my.televip.features.messages.ArchiveUi.show(activity);
            else if ("ExportArchive".equals(item.getKey())) com.my.televip.features.messages.ArchiveUi.chooseExport(activity);
            else if ("ImportArchive".equals(item.getKey())) com.my.televip.features.messages.ArchiveUi.chooseImport(activity);
            else if (Keys.Calendar.equals(item.getKey())) {
                String[] choices = {Translator.get(Keys.Gregorian), Translator.get(Keys.Hijri), Translator.get(Keys.Persian)};
                new AlertDialog.Builder(action.getContext())
                        .setTitle(Translator.get(Keys.Calendar))
                        .setSingleChoiceItems(choices, item.getCustomCalendar(), (dialog, which) -> {
                            item.setCustomCalendar(which);
                            item.run();
                            action.setText(Translator.get(Keys.Calendar) + ": " + choices[which]);
                            dialog.dismiss();
                        }).show();
            } else if (Keys.DeveloperChannel.equals(item.getKey())) {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(com.my.televip.ProjectLinks.TELEGRAM_CHANNEL_URL)));
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
        TextView heading = new TextView(list.getContext());
        heading.setText(label);
        heading.setTextSize(16);
        heading.setTextColor(isDark(activity) ? 0xffaccaf0 : 0xff204f75);
        heading.setPadding(dp(activity, indent * 12), dp(activity, 14), 0, dp(activity, 4));
        list.addView(heading);
    }

    private static int dp(Activity activity, float value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static boolean isDark(Activity activity) {
        try { return !Theme.isLight(); }
        catch (RuntimeException error) {
            return (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
        }
    }
}
