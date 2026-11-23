package com.my.televip.features.telegram;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.language.Translator;
import com.my.televip.logging.Logger;
import com.my.televip.ui.ThemeColors;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class MutualContacts {
    private static volatile boolean installed;
    private static final String TAG = "TeleVip.MutualContact";
    private static final Set<Object> contacts = Collections.newSetFromMap(new WeakHashMap<>());
    private MutualContacts() {}

    public static synchronized void init() {
        if (installed) return;
        try (HookInstallation attempt = HookInstallation.begin()) {
            Class<?> cell = Host.type("UserCell"), user = Host.type("TLRPC$User");
            Method current = Host.method("UserCell", "getCurrentObject", 0);
            Field mutual = Host.field("TLRPC$User", "mutual_contact");
            int constructors = 0;
            for (Constructor<?> constructor : cell.getDeclaredConstructors()) {
                if (!java.util.Arrays.equals(constructor.getParameterTypes(),
                        new Class<?>[]{Context.class, int.class, int.class, boolean.class})) continue;
                constructors++;
                HMethod.hookMethod(constructor, new BaseMethodHook() {
                    @Override protected void afterMethod(MethodHookParam call) {
                        if ((int) call.args[1] == 58 && (int) call.args[2] == 1) contacts.add(call.thisObject);
                    }
                });
            }
            if (constructors != 1) throw new NoSuchMethodException("Contact cell constructor");
            HMethod.hookMethod(Host.method("UserCell", "update", 1), new BaseMethodHook() {
                @Override protected void afterMethod(MethodHookParam call) throws Exception {
                    if (!contacts.contains(call.thisObject)) return;
                    FrameLayout view = (FrameLayout) call.thisObject;
                    TextView badge = view.findViewWithTag(TAG);
                    Object object = current.invoke(view);
                    boolean shown = Host.enabled(ConfigManager.mutualContacts) && user.isInstance(object)
                            && mutual.getBoolean(object);
                    if (badge == null && shown) {
                        badge = new TextView(view.getContext());
                        badge.setTag(TAG);
                        badge.setText("⇄");
                        badge.setTextSize(21);
                        badge.setGravity(Gravity.CENTER);
                        badge.setContentDescription(Translator.get("MutualContacts"));
                        badge.setOnClickListener(v -> Toast.makeText(v.getContext(),
                                Translator.get("Desc_MutualContacts"), Toast.LENGTH_SHORT).show());
                        int size = Math.round(40 * view.getResources().getDisplayMetrics().density);
                        FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(size, size,
                                Gravity.END | Gravity.CENTER_VERTICAL);
                        layout.setMarginEnd(size / 5);
                        view.addView(badge, layout);
                    }
                    if (badge != null) {
                        badge.setTextColor(ThemeColors.getTextColor());
                        badge.setVisibility(shown ? View.VISIBLE : View.GONE);
                    }
                }
            });
            installed = attempt.isComplete();
        } catch (Throwable error) { Logger.e(error); }
    }
}
