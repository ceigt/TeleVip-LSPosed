package com.my.televip.virtuals.messenger;
public final class MessageObject {
    private final Object object;
    public MessageObject(Object object) { this.object=object; }
    public com.my.televip.virtuals.tgnet.TLRPC.Message getMessageOwner() {
        return new com.my.televip.virtuals.tgnet.TLRPC.Message(com.my.televip.compat.XposedHelpers.getObjectField(object,"messageOwner"));
    }
    public long getDialogId() { return getDialogId(getMessageOwner()); }
    public static long getDialogId(com.my.televip.virtuals.tgnet.TLRPC.Message message) {
        return ((com.my.televip.virtuals.tgnet.TLRPC.MessageData)message.get_Message()).dialog;
    }
}
