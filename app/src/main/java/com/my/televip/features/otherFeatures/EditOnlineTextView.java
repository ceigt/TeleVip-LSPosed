package com.my.televip.features.otherFeatures;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Paint;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.Toast;
import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.compat.XposedHelpers;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.language.Keys;
import com.my.televip.language.Translator;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.ui.ThemeColors;
import com.my.televip.utils.Utils;
import com.my.televip.virtuals.ActionBar.SimpleTextView;
import com.my.televip.virtuals.ui.ProfileActivity;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Adapted from Re-TeleVIP 1.5.1/1.5.2; transform before the host centres its text. */
public class EditOnlineTextView {
    public static volatile boolean isEnable;
    private static final ProfileTextScope scope = new ProfileTextScope();
    private static final class IdLabel {
        final String id;
        final boolean own;
        IdLabel(String id, boolean own) { this.id = id; this.own = own; }
    }
    private static final Map<View, IdLabel> labels = Collections.synchronizedMap(new WeakHashMap<>());

    public static synchronized void init() {
        if (isEnable) return;
        try (HookInstallation attempt = HookInstallation.begin()) {
            Class<?> textView = ClassLoad.getClass("org.telegram.ui.ActionBar.SimpleTextView");
            HMethod.hookMethod(textView, Obfuscate.getMethodName("SimpleTextView", "setText"), CharSequence.class,
                    new BaseMethodHook() {
                        @Override protected void beforeMethod(MethodHookParam param) {
                            CharSequence text = scope.status(param.thisObject, (CharSequence) param.args[0]);
                            ProfileTextScope.Frame frame = scope.current();
                            if (frame != null && param.thisObject == frame.idView && text != null && text.length() > 0)
                                text = withId(text, frame.id);
                            param.args[0] = text;
                        }
                    });
            HMethod.hookMethod(textView, Obfuscate.getMethodName("SimpleTextView", "getExactWidth"), new BaseMethodHook() {
                @Override protected void afterMethod(MethodHookParam param) {
                    IdLabel label = labels.get(param.thisObject);
                    if (label == null || !ConfigManager.isUserIdShown(label.own) || !(param.getResult() instanceof Float)) return;
                    CharSequence text = new SimpleTextView(param.thisObject).getText();
                    if (!ProfileStatusLine.hasId(text, label.id)) return;
                    Paint paint = (Paint) XposedHelpers.callMethod(param.thisObject, Obfuscate.getMethodName("SimpleTextView", "getPaint"));
                    String value = text.toString();
                    float widest = 0;
                    for (String line : value.split("\n")) widest = Math.max(widest, paint.measureText(line));
                    // Keep drawable widths and spacing; replace only the host's single-line text measure.
                    param.setResult(Math.max(0f, (Float) param.getResult() - paint.measureText(value) + widest));
                }
            });
            HMethod.hookMethod(ClassLoad.getClass(ClassNames.PROFILE_ACTIVITY),
                    Obfuscate.getMethodName("ProfileActivity", "updateProfileData"),
                    ArgsResolver.merge("updateProfileData", new Class[]{boolean.class}, new BaseMethodHook() {
                        @Override protected void beforeMethod(MethodHookParam param) {
                            Object status = null, copy = null, idView = null;
                            String id = null, offline = null;
                            try {
                                ProfileActivity profile = new ProfileActivity(param.thisObject);
                                Object[] lines = profile.getOnlineTextView();
                                if (lines == null || lines.length < 2) return;
                                boolean own = profile.getUserId() != 0
                                        && profile.getUserId() == profile.getBaseFragment().getUserConfig().getClientUserId();
                                if (own && ConfigManager.hideOnline.isEnable()) {
                                    status = lines[1]; copy = lines.length > 3 ? lines[3] : null;
                                    offline = Translator.get(Keys.UserOffline);
                                }
                                long value = profile.getUserId() > 1 ? profile.getUserId() : profile.getChatId();
                                if (ConfigManager.isUserIdShown(own) && value != 0) {
                                    Object overlay = lines.length > 3 ? lines[3] : null;
                                    int index = ProfileStatusLine.index(lines.length, lines[1] != null,
                                            overlay == null ? null : new SimpleTextView(overlay).getText(), shown(lines[1]), shown(overlay));
                                    idView = lines[index]; id = String.valueOf(value);
                                }
                                for (Object line : lines) if (line instanceof View && line != idView && labels.remove(line) != null)
                                    ((View) line).setOnClickListener(null);
                                if (idView instanceof View) {
                                    labels.put((View) idView, new IdLabel(id, own));
                                    new SimpleTextView(idView).setMaxLines(2);
                                    // The host's gradient uses a cached hardware layer for a one-line status.
                                    // With our added second line, scroll/reflow can leave that layer stale.
                                    XposedHelpers.callMethod(idView, Obfuscate.getMethodName("SimpleTextView", "setEllipsizeByGradient"), false);
                                }
                            } finally {
                                scope.enter(new ProfileTextScope.Frame(param, status, copy, idView, id, offline));
                            }
                        }
                        @Override protected void afterMethod(MethodHookParam param) {
                            try {
                                ProfileTextScope.Frame frame = scope.current();
                                if (param.hasThrowable() || frame == null || frame.token != param) return;
                                if (frame.status != null) {
                                    SimpleTextView status = new SimpleTextView(frame.status);
                                    CharSequence text = status.getText();
                                    if (!ProfileStatusLine.hasId(text, frame.id) && !frame.offline.contentEquals(text == null ? "" : text))
                                        status.setText(frame.offline);
                                }
                                if (!(frame.idView instanceof View)) return;
                                SimpleTextView line = new SimpleTextView(frame.idView);
                                if (!ProfileStatusLine.hasId(line.getText(), frame.id)) line.setText(withId(line.getText(), frame.id), true);
                                line.getSimpleTextView().requestLayout();
                                line.getSimpleTextView().invalidate();
                                String id = frame.id;
                                line.getSimpleTextView().setOnClickListener(v -> {
                                    Context activity = Utils.getCurrentActivity();
                                    if (activity == null) return;
                                    ((ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE))
                                            .setPrimaryClip(ClipData.newPlainText("clipboard", id));
                                    Toast.makeText(activity, Translator.get(Keys.Copied, id), Toast.LENGTH_SHORT).show();
                                });
                            } finally { scope.exit(param); }
                        }
                    }));
            isEnable = attempt.isComplete();
        } catch (Throwable error) { Logger.e(error); }
    }

    private static boolean shown(Object value) {
        return value instanceof View && ((View) value).getVisibility() == View.VISIBLE && ((View) value).getAlpha() > 0f;
    }
    private static CharSequence withId(CharSequence text, String id) {
        if (ProfileStatusLine.hasId(text, id)) return text;
        SpannableStringBuilder value = new SpannableStringBuilder(ProfileStatusLine.withoutId(text));
        if (value.length() > 0) value.append('\n');
        int start = value.length();
        value.append("ID: ").append(id);
        value.setSpan(new ForegroundColorSpan(ThemeColors.getTextColor()), start, value.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return value;
    }
}
