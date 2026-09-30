package com.my.televip.features.otherFeatures;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Clients.ClientManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.language.Keys;
import com.my.televip.language.Translator;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.utils.Utils;
import com.my.televip.utils.MessageIdParser;
import com.my.televip.virtuals.ActionBar.ActionBarMenuItem;
import com.my.televip.virtuals.ActionBar.AlertDialog;
import com.my.televip.virtuals.ActionBar.Theme;
import com.my.televip.virtuals.ui.ChatActivity;

import com.my.televip.compat.XposedHelpers;

public class ChatHook {

    private static boolean initialized = false;
    private static int attempts;
    private static String lastClass;

    public static synchronized boolean isInitialized() { return initialized; }

    public static synchronized void init(String className) {
        if (initialized || ClientManager.is(ClientManager.Client.Nagram) || ClientManager.is(ClientManager.Client.TelegramPlus)) return;
        if (className == null) return;
        if (!className.equals(lastClass)) { lastClass = className; attempts = 0; }
        if (attempts >= 3) return;
        attempts++;

        Class<?> clazz = ClassLoad.getClass(className);
        if (clazz == null) {
            FeatureStateManager.resetChat();
            Logger.e(new IllegalStateException("Chat menu listener missing: " + className));
            return;
        }
        try (HookInstallation attempt = HookInstallation.begin()) {
            HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_ACTIVITY), Obfuscate.getMethodName("ChatActivity", "createView"), ArgsResolver.merge("createView", new Class[]{Context.class}, new BaseMethodHook() {
                @Override
                protected void afterMethod(MethodHookParam param) {
                    try {
                        ChatActivity chatActivity = new ChatActivity(param.thisObject);

                        ActionBarMenuItem headerItem = chatActivity.getHeaderItem();
                        if (headerItem.getActionBarMenuItem() != null) {

                            int drawableResource = XposedHelpers.getStaticIntField(ClassLoad.getClass(ClassNames.DRAWABLE), "msg_go_up");

                            if (!ClientManager.is(ClientManager.Client.iMe) && !ClientManager.is(ClientManager.Client.iMeWeb) && !ClientManager.is(ClientManager.Client.TelegramPlus) && !ClientManager.is(ClientManager.Client.XPlus) && !ClientManager.is(ClientManager.Client.forkgram) && !ClientManager.is(ClientManager.Client.forkgramBeta)) {
                                headerItem.lazilyAddSubItem(8353847, drawableResource, Translator.get(Keys.ToTheBeginning));
                            }
                            drawableResource = XposedHelpers.getStaticIntField(ClassLoad.getClass(ClassNames.DRAWABLE), "player_new_order");

                            headerItem.lazilyAddSubItem(8353848, drawableResource, Translator.get(Keys.ToTheMessage));

                        }
                    } catch (Throwable t){
                        Logger.e(t);
                    }

                }
            }));

            HMethod.hookMethod(clazz, ClientManager.is(ClientManager.Client.Telegram) ? "b" : "onItemClick", int.class, new BaseMethodHook() {
                @Override
                protected void afterMethod(MethodHookParam param) {
                    try {
                        int id = (int) param.args[0];

                        final Object thisClass = XposedHelpers.getObjectField(param.thisObject, Obfuscate.getFieldName("ChatActivity", "this$0"));
                        ChatActivity chat = new ChatActivity(thisClass);

                        if (id == 8353847) {
                            chat.scrollToMessageId(1, 0, true, 0, true, 0);
                        } else if (id == 8353848) {

                            AlertDialog dialog = new AlertDialog(Utils.getCurrentActivity());
                            dialog.setTitle(Translator.get(Keys.InputMessageId));

                            EditText input = new EditText(Utils.getCurrentActivity());
                            input.setInputType(InputType.TYPE_CLASS_NUMBER);
                            if (Theme.isLight()) {
                                input.setTextColor(0xFF000000);
                                input.setHintTextColor(0xFF424242);
                            } else {
                                input.setTextColor(0xFFFFFFFF);
                                input.setHintTextColor(0xFFBDBDBD);
                            }
                            input.setTextSize(18);
                            input.setPadding(20, 20, 20, 20);

                            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                            );
                            params.setMargins(20, 20, 20, 20);
                            input.setLayoutParams(params);

                            LinearLayout layout = new LinearLayout(Utils.getCurrentActivity());
                            layout.setOrientation(LinearLayout.VERTICAL);
                            layout.addView(input);

                            dialog.setView(layout);

                            dialog.setPositiveButton(Translator.get(Keys.Done), AlertDialog.click(() -> {
                                Integer msgId = MessageIdParser.parse(input.getText().toString());
                                if (msgId == null) {
                                    Toast.makeText(input.getContext(), "Message ID must be 1–2147483647", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                try { chat.scrollToMessageId(msgId, 0, true, 0, true, 0); }
                                catch (Throwable t) { Logger.e(t); }
                            }));

                            dialog.show();
                        }
                    } catch (Throwable t) {
                        Logger.e(t);
                    }
                }
            });
            initialized = attempt.isComplete();
            if (initialized) FeatureStateManager.saveChat(className);
            else FeatureStateManager.resetChat();
        } catch (Throwable t){
            FeatureStateManager.resetChat();
            Logger.e(t);
        }
    }
}
