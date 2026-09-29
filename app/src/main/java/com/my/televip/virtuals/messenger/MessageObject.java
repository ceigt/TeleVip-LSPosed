package com.my.televip.virtuals.messenger;

import com.my.televip.Class.ClassNames;
import com.my.televip.Class.ClassLoad;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.tgnet.TLRPC;

import com.my.televip.compat.XposedHelpers;

public class MessageObject {

    private final Object messageObject;

    public MessageObject(Object obj){
        messageObject = obj;
    }

    public TLRPC.Message getMessageOwner(){
        Object owner = XposedHelpers.getObjectField(messageObject, Obfuscate.getFieldName("MessageObject","messageOwner"));
        return owner == null ? null : new TLRPC.Message(owner);
    }

    public long getDialogId() {
        return (long) XposedHelpers.callMethod(messageObject, Obfuscate.getMethodName("MessageObject", "getDialogId"));
    }

    public boolean isVoice() {
        return (boolean) XposedHelpers.callMethod(messageObject, Obfuscate.getMethodName("MessageObject", "isVoice"));
    }

    public static long getDialogId(TLRPC.Message message) {
        return (long) XposedHelpers.callStaticMethod(ClassLoad.getClass(ClassNames.MESSAGE_OBJECT), Obfuscate.getMethodName("MessageObject", "getDialogId"), message.get_Message());
    }

    public Object getMessageObject(){
        return messageObject;
    }

}
