package com.my.televip.features.otherFeatures;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

import com.my.televip.Class.ClassNames;
import com.my.televip.Class.ClassLoad;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.language.Keys;
import com.my.televip.language.Translator;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.utils.Utils;
import com.my.televip.virtuals.ActionBar.SimpleTextView;
import com.my.televip.virtuals.ui.ProfileActivity;

public class CopyNameHook {
    private static boolean initialized;

    public static synchronized void init() {
        if (initialized) return;
        try (HookInstallation attempt = HookInstallation.begin()) {
            if (attempt.require(ClassLoad.getClass(ClassNames.PROFILE_ACTIVITY))) {

                HMethod.hookMethod(ClassLoad.getClass(ClassNames.PROFILE_ACTIVITY), Obfuscate.getMethodName("ProfileActivity", "createView"), ArgsResolver.merge("createView", new Class[]{Context.class}, new BaseMethodHook() {
                    @Override
                    protected void afterMethod(MethodHookParam param) {
                        final ProfileActivity profileActivity = new ProfileActivity(param.thisObject);

                        Object[] nameTextViewArray = profileActivity.getNameTextView();

                        if (nameTextViewArray != null && nameTextViewArray.length > 1) {

                            SimpleTextView simpleTextView = new SimpleTextView(nameTextViewArray[1]);

                            if (simpleTextView.getSimpleTextView() != null) {
                                simpleTextView.getSimpleTextView().setOnClickListener(v -> {
                                    if (simpleTextView.getText() != null) {
                                        String name = Translator.get(Keys.Copied, simpleTextView.getText());
                                        ((ClipboardManager) Utils.getCurrentActivity().getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("clipboard", simpleTextView.getText()));
                                        Toast.makeText(Utils.getCurrentActivity(), name, Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        }
                    }
                }));
            }
            initialized = attempt.isComplete();
        } catch (Throwable t) {
            Logger.e(t);
        }
    }
}
