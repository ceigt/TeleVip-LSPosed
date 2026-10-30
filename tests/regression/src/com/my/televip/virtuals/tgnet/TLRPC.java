package com.my.televip.virtuals.tgnet;

import java.util.ArrayList;
public final class TLRPC {
    public static final class MessageData {
        public final int id,flags; public final long dialog; public final String text;
        public MessageData(int id,long dialog,int flags,String text) { this.id=id;this.dialog=dialog;this.flags=flags;this.text=text; }
    }
    public static final class Message {
        private final MessageData data;
        public Message(Object value) { data=(MessageData)value; }
        public Object get_Message() { return data; }
        public int getId() { return data.id; }
        public int getFlags() { return data.flags; }
        public String getMessage() { return data.text; }
        public static Message TLdeserialize(NativeByteBuffer buffer,int id,boolean exception) {
            return buffer.previous==null ? null : new Message(buffer.previous);
        }
        public void readAttachPath(NativeByteBuffer buffer,long account) {}
    }
    public static final class messages_Messages {
        private final ArrayList<Object> messages;
        public messages_Messages(ArrayList<Object> messages) { this.messages=messages; }
        public messages_Messages(Object value) { messages=((messages_Messages)value).messages; }
        public ArrayList<Object> getMessages() { return messages; }
    }
    public static final class User { public User(Object value) {} }
}
