package com.my.televip.features.telegram;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.Configs.ConfigPreferences;
import com.my.televip.application.ApplicationLoaderHook;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.language.Translator;
import com.my.televip.logging.Logger;
import java.lang.reflect.Method;

public final class TelegramIdentity {
    private static volatile boolean installed;
    private static boolean earlyInstalled;
    private TelegramIdentity() {}

    public static synchronized void installEarly(ClassLoader loader) {
        if (earlyInstalled) return;
        try {
            Class<?> manager = Class.forName("org.telegram.tgnet.ConnectionsManager", false, loader);
            Method nativeInit = null;
            for (Method method : manager.getDeclaredMethods()) {
                if (!method.getName().equals("native_init") || method.getReturnType() != void.class
                        || !java.lang.reflect.Modifier.isStatic(method.getModifiers())) continue;
                Class<?>[] signature = {int.class, int.class, int.class, int.class, String.class,
                        String.class, String.class, String.class, String.class, String.class, String.class,
                        String.class, String.class, String.class, String.class, int.class, long.class,
                        boolean.class, boolean.class, boolean.class, int.class, int.class};
                if (java.util.Arrays.equals(method.getParameterTypes(), signature)) nativeInit = method;
            }
            if (nativeInit == null) throw new NoSuchMethodException("native_init");
            earlyInstalled = HMethod.hookMethod(nativeInit, new BaseMethodHook() {
                @Override protected void beforeMethod(MethodHookParam call) {
                    android.content.Context context = ApplicationLoaderHook.getApplicationContext();
                    if (context == null) return;
                    android.content.SharedPreferences prefs = context.getSharedPreferences("TeleVip", 0);
                    if (!prefs.getBoolean("CustomApiIdentity", false)) return;
                    String id = prefs.getString("TelegramApiId", ""), hash = prefs.getString("TelegramApiHash", "");
                    if (ApiIdentity.valid(id, hash)) call.args[3] = Integer.parseInt(id);
                }
            });
        } catch (Throwable error) { Logger.e(error); }
    }
    public static synchronized void init() {
        if (installed) return;
        try (HookInstallation attempt = HookInstallation.begin()) {
            java.lang.reflect.Field id = Host.field("TLRPC$TL_auth_sendCode", "api_id");
            java.lang.reflect.Field hash = Host.field("TLRPC$TL_auth_sendCode", "api_hash");
            HMethod.hookMethod(Host.method("TLRPC$TL_auth_sendCode", "serializeToStream", 1), new BaseMethodHook() {
                @Override protected void beforeMethod(MethodHookParam call) throws Exception {
                    if (!Host.enabled(ConfigManager.customApiIdentity)) return;
                    String value = ConfigPreferences.getString("TelegramApiId");
                    String secret = ConfigPreferences.getString("TelegramApiHash");
                    if (!ApiIdentity.valid(value, secret)) return;
                    id.setInt(call.thisObject, Integer.parseInt(value));
                    hash.set(call.thisObject, secret);
                }
            });
            installed = attempt.isComplete();
        } catch (Throwable error) { Logger.e(error); }
    }
    public static void configure(Activity activity) {
        LinearLayout view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        EditText id = new EditText(activity), hash = new EditText(activity);
        id.setHint("API ID");
        id.setInputType(InputType.TYPE_CLASS_NUMBER);
        id.setSingleLine(true);
        hash.setHint("API Hash");
        hash.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        hash.setSingleLine(true);
        id.setText(ConfigPreferences.getString("TelegramApiId"));
        hash.setText(ConfigPreferences.getString("TelegramApiHash"));
        view.addView(id); view.addView(hash);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(Translator.get("ConfigureApiIdentity")).setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String apiId = id.getText().toString().trim(), apiHash = hash.getText().toString().trim();
            if (!ApiIdentity.valid(apiId, apiHash)) {
                id.setError(Translator.get("InvalidApiIdentity"));
                return;
            }
            ConfigPreferences.putString("TelegramApiId", apiId);
            ConfigPreferences.putString("TelegramApiHash", apiHash);
            Toast.makeText(activity, Translator.get("RestartForIdentity"), Toast.LENGTH_LONG).show();
            dialog.dismiss();
        }));
        dialog.show();
    }
}
