package com.my.televip.virtuals.ui;

import android.view.View;

import com.my.televip.obfuscate.Obfuscate;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import com.my.televip.virtuals.ActionBar.ActionBarMenuItem;
import com.my.televip.virtuals.messenger.MessageObject;

import com.my.televip.compat.XposedHelpers;

public class ChatActivity {

    final Object chatActivity;

    public ChatActivity(Object obj){
        chatActivity = obj;
    }

    public MessageObject getSelectedObject(){
        Object selected = XposedHelpers.getObjectField(chatActivity, Obfuscate.getFieldName("ChatActivity","selectedObject"));
        return selected == null ? null : new MessageObject(selected);
    }

    public ActionBarMenuItem getHeaderItem(){
        return new ActionBarMenuItem(XposedHelpers.getObjectField(chatActivity, Obfuscate.getFieldName("ChatActivity", "headerItem")));
    }
    public View getPinnedMessageView(){
        return (View) XposedHelpers.getObjectField(chatActivity, Obfuscate.getFieldName("ChatActivity", "pinnedMessageView"));
    }

    public boolean isBroadcastChannel() {
        Object chat = XposedHelpers.getObjectField(chatActivity, Obfuscate.getFieldName("ChatActivity", "currentChat"));
        return chat != null && (boolean) XposedHelpers.getObjectField(chat, "broadcast")
                && !(boolean) XposedHelpers.getObjectField(chat, "megagroup");
    }

    public void scrollToMessageId(int id, int fromMessageId, boolean select, int loadIndex, boolean forceScroll, int forcePinnedMessageId){
        String name = Obfuscate.getMethodName("ChatActivity", "scrollToMessageId");
        Class<?>[] standard = {int.class, int.class, boolean.class, int.class, boolean.class, int.class};
        Class<?>[] reordered = {int.class, int.class, int.class, int.class, boolean.class, boolean.class};
        Method target = null;
        for (Class<?> owner = chatActivity.getClass(); owner != null && target == null; owner = owner.getSuperclass()) {
            for (Method method : owner.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getReturnType() != void.class) continue;
                Class<?>[] types = method.getParameterTypes();
                if (!Arrays.equals(types, standard) && !Arrays.equals(types, reordered)) continue;
                if (target != null) throw new IllegalStateException("Ambiguous message navigation signature");
                target = method;
            }
        }
        if (target == null) throw new IllegalStateException("Message navigation signature unavailable");
        Object[] args = Arrays.equals(target.getParameterTypes(), standard)
                ? new Object[]{id, fromMessageId, select, loadIndex, forceScroll, forcePinnedMessageId}
                : new Object[]{id, fromMessageId, loadIndex, forcePinnedMessageId, select, forceScroll};
        try {
            target.setAccessible(true);
            target.invoke(chatActivity, args);
        } catch (InvocationTargetException error) {
            throw new IllegalStateException("Message navigation failed", error.getCause());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Message navigation failed", error);
        }
    }

}
