package com.my.televip.virtuals.ui.Cells;

import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.messenger.MessageObject;

import com.my.televip.compat.XposedHelpers;

public class ChatMessageCell {
    Object chatMessageCell;

    public ChatMessageCell(Object cell){ chatMessageCell = cell; }

    public MessageObject getMessageObject() {
        return new MessageObject(XposedHelpers.callMethod(chatMessageCell, Obfuscate.getMethodName("ChatMessageCell", "getMessageObject")));
    }


    public int getTimeTextWidth()
    {
        return XposedHelpers.getIntField(this.chatMessageCell, Obfuscate.getFieldName("ChatMessageCell", "timeTextWidth"));
    }

    public int getTimeWidth()
    {
        return XposedHelpers.getIntField(this.chatMessageCell, Obfuscate.getFieldName("ChatMessageCell", "timeWidth"));
    }

    public void setTimeTextWidth(int width)
    {
        XposedHelpers.setIntField(this.chatMessageCell, Obfuscate.getFieldName("ChatMessageCell", "timeTextWidth"), width);
    }

    public void setTimeWidth(int width)
    {
        XposedHelpers.setIntField(this.chatMessageCell, Obfuscate.getFieldName("ChatMessageCell", "timeWidth"), width);
    }

    public CharSequence getCurrentTimeString()
    {
        return (CharSequence) XposedHelpers.getObjectField(this.chatMessageCell, Obfuscate.getFieldName("ChatMessageCell", "currentTimeString"));
    }

    public void setCurrentTimeString(CharSequence currentTimeString)
    {
        XposedHelpers.setObjectField(this.chatMessageCell, Obfuscate.getFieldName("ChatMessageCell", "currentTimeString"), currentTimeString);
    }

    public Object getChatMessageCell(){
        return chatMessageCell;
    }

}
