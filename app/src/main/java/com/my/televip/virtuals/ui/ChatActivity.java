package com.my.televip.virtuals.ui;

import android.view.View;

import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.Clients.ClientManager;
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

    public void scrollToMessageId(int id, int fromMessageId, boolean select, int loadIndex, boolean forceScroll, int forcePinnedMessageId){
        if (ClientManager.is(ClientManager.Client.Telegram)) {
            XposedHelpers.callMethod(chatActivity, "E", id, fromMessageId, loadIndex, forcePinnedMessageId, select, forceScroll);
            return;
        }
        XposedHelpers.callMethod(chatActivity, Obfuscate.getMethodName("ChatActivity", "scrollToMessageId"), id, fromMessageId, select, loadIndex, forceScroll, forcePinnedMessageId);
    }

}
