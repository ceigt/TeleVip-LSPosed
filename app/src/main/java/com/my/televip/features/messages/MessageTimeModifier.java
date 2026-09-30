package com.my.televip.features.messages;

import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.language.Keys;
import com.my.televip.language.Translator;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.ActionBar.Theme;
import com.my.televip.virtuals.messenger.MessageObject;
import com.my.televip.virtuals.tgnet.TLRPC;
import com.my.televip.virtuals.ui.Cells.ChatMessageCell;

public class MessageTimeModifier {

    public static volatile boolean loaded;

    public static synchronized void init() {

        if (loaded)
            return;

        try {
            loaded = HMethod.hookMethod(
                    ClassLoad.getClass(ClassNames.CHAT_MESSAGE_CELL),
                    Obfuscate.getMethodName("ChatMessageCell", "measureTime"),
                    ArgsResolver.merge("measureTime", new Class[]{ClassLoad.getClass(ClassNames.MESSAGE_OBJECT)},
                            new BaseMethodHook() {
                                @Override
                                protected void afterMethod(MethodHookParam param) {

                                    try {
                                        apply(param.thisObject, param.args[0]);
                                    } catch (Throwable e) {
                                        Logger.e(e);
                                    }

                                }
                            }));
        } catch (Throwable e) {
            Logger.e(e);
        }

    }

    private static void apply(Object cellObject, Object messageObject)
    {
        boolean id =
                ConfigManager.showMessageId != null &&
                        ConfigManager.showMessageId.isEnable();

        boolean deleted =
                ConfigManager.showDeletedMessages != null &&
                        ConfigManager.showDeletedMessages.isEnable();

        if (!id && !deleted)
            return;

        try {
            MessageObject msg = new MessageObject(messageObject);

            TLRPC.Message message = msg.getMessageOwner();

            if (message.get_Message() == null)
                return;

            String prefix = null;
            boolean redColor = false;

            if (id && message.getId() != 0) {
                prefix = "ID " + message.getId();

            }

            if (deleted && (message.getFlags() & ShowDeletedMessages.FLAG_DELETED) != 0) {
                prefix = Translator.get(Keys.Deleted);
                redColor = true;
            }

            if (prefix != null)
                prependTime(cellObject, prefix, redColor);

        } catch (Throwable e) {
            Logger.e(e);
        }

    }

    public static SpannableStringBuilder convertToStringBuilder(CharSequence charSequence) {
        if (charSequence != null)
            return charSequence instanceof SpannableStringBuilder ? (SpannableStringBuilder) charSequence : new SpannableStringBuilder(charSequence);
        else
            return null;
    }

    private static void prependTime(Object object, String value, boolean red) {
        ChatMessageCell cell = new ChatMessageCell(object);
        SpannableStringBuilder time = convertToStringBuilder(cell.getCurrentTimeString());

        if (time == null)
            return;

        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(value);

        if (red)
            spannableStringBuilder.setSpan(new ForegroundColorSpan(Color.rgb(255, 0, 0)), 0, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableStringBuilder.append(" ");

        time.insert(0, spannableStringBuilder);

        cell.setCurrentTimeString(time);
        TextPaint paint = Theme.getTextPaint();

        if (paint != null) {
            int ceil = (int) Math.ceil(paint.measureText(spannableStringBuilder, 0, spannableStringBuilder.length()));
            cell.setTimeTextWidth(ceil + cell.getTimeTextWidth());
            cell.setTimeWidth(ceil + cell.getTimeWidth());
        }

    }

}
