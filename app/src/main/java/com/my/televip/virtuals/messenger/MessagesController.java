package com.my.televip.virtuals.messenger;

import android.content.SharedPreferences;
import android.util.SparseArray;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.androidx.LongSparseArray;
import com.my.televip.virtuals.tgnet.TLRPC;

import com.my.televip.compat.XposedHelpers;

public class MessagesController {
    final Object messagesController;

    public MessagesController(Object instance)
    {
        this.messagesController = instance;
    }

    public void processNewDifferenceParams(int seq, int pts, int date, int ptsCount) {
        String method = com.my.televip.obfuscate.RuntimeMappings.active().resolveMethod("MessagesController", "processNewDifferenceParamsIII");
        if (method != null) XposedHelpers.callMethod(messagesController, method, pts, date, ptsCount);
        else XposedHelpers.callMethod(messagesController, Obfuscate.getMethodName("MessagesController", "processNewDifferenceParams"), seq, pts, date, ptsCount);
    }

    public void processNewDifferenceParams(int pts, int date, int ptsCount) {
        processNewDifferenceParams(-1, pts, date, ptsCount);
    }

    public void removePromoDialog() {
        XposedHelpers.callMethod(messagesController, Obfuscate.getMethodName("MessagesController", "removePromoDialog"));
    }

    public SparseArray<Object> getDialogMessagesByIds() {
        return (SparseArray<Object>) XposedHelpers.getObjectField(messagesController, Obfuscate.getFieldName("MessagesController", "dialogMessagesByIds"));
    }

    public LongSparseArray getDialogMessage() {
        return  new LongSparseArray(XposedHelpers.getObjectField(messagesController, Obfuscate.getFieldName("MessagesController", "dialogMessage")));
    }

    public static SharedPreferences getGlobalMainSettings() {
        return (SharedPreferences) XposedHelpers.callStaticMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER), Obfuscate.getMethodName("MessagesController", "getGlobalMainSettings"));
    }

    public Object getInputChannelForAccount(long id) {
        return XposedHelpers.callMethod(messagesController, Obfuscate.getMethodName("MessagesController", "getInputChannelJ"), id);
    }

    public MessagesStorage getMessagesStorage() {
        return new MessagesStorage(XposedHelpers.callMethod(messagesController, Obfuscate.getMethodName("MessagesController", "getMessagesStorage")));
    }

    public static MessagesController getInstance(int num){
        return new MessagesController(XposedHelpers.callStaticMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER), Obfuscate.getMethodName("MessagesController", "getInstance"), num));
    }
    public Object getInstanceObject() { return messagesController; }
    public Object getUserFull(long id) {
        return XposedHelpers.callMethod(messagesController, Obfuscate.getMethodName("MessagesController", "getUserFull"), id);
    }

    public Object getChatFull(long id) {
        return XposedHelpers.callMethod(messagesController, Obfuscate.getMethodName("MessagesController", "getChatFull"), id);
    }

    public static MessagesController getMessagesController() {
        return getInstance(UserConfig.getSelectedAccount());
    }

}
