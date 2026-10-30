package com.my.televip.virtuals.messenger;
import android.os.Handler;
import com.my.televip.virtuals.SQLite.SQLiteDatabase;
public final class MessagesStorage {
    public final SQLiteDatabase database;
    private final DispatchQueue queue;
    private final MessagesStorage owner;
    public long accountId = 101;
    public MessagesStorage(Handler handler) { queue = new DispatchQueue(handler); database=new SQLiteDatabase(); owner=null; }
    public MessagesStorage(Object object) { owner=(MessagesStorage)object; queue=owner.queue; database=owner.database; }
    public long getAccountUserId() { return owner==null ? accountId : owner.accountId; }
    public DispatchQueue getStorageQueue() { return queue; }
    public SQLiteDatabase getDatabase() { return database; }
    public void putMessages(com.my.televip.virtuals.tgnet.TLRPC.messages_Messages messages,long dialog,
            int loadType,int maxId,boolean create,int mode,long thread) {
        queue.postRunnable(() -> {
            for(Object value:messages.getMessages()) {
                com.my.televip.virtuals.tgnet.TLRPC.MessageData data=(com.my.televip.virtuals.tgnet.TLRPC.MessageData)value;
                database.history.put(data.id+":"+data.dialog,data);
            }
        });
    }
}
