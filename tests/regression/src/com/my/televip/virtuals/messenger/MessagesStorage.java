package com.my.televip.virtuals.messenger;
import android.os.Handler;
import com.my.televip.virtuals.SQLite.SQLiteDatabase;
public final class MessagesStorage {
    public static MessagesStorage instance;
    public static MessagesStorage getInstance(int slot){return instance;}
    public final SQLiteDatabase database;
    private final DispatchQueue queue;
    private final MessagesStorage owner;
    public long accountId = 101;
    public MessagesStorage(Handler handler) { queue = new DispatchQueue(handler); database=new SQLiteDatabase(); owner=null; }
    public MessagesStorage(Object object) { owner=(MessagesStorage)object; queue=owner.queue; database=owner.database; }
    public final java.util.Map<Long,java.util.List<Integer>> unpinned = new java.util.HashMap<>();
    public void clearPinnedReferences(long dialog, java.util.ArrayList<Integer> ids) { unpinned.put(dialog,new java.util.ArrayList<>(ids)); }
    public long getAccountUserId() { return owner==null ? accountId : owner.accountId; }
    public DispatchQueue getStorageQueue() { return queue; }
    public SQLiteDatabase getDatabase() { return database; }
    public void putMessages(int loadType,int maxId,int mode,long dialog,long thread,
            com.my.televip.virtuals.tgnet.TLRPC.messages_Messages messages,boolean create) {
        putMessages(messages,dialog,loadType,maxId,create,mode,thread);
    }
    public void putMessages(com.my.televip.virtuals.tgnet.TLRPC.messages_Messages messages,long dialog,
            int loadType,int maxId,boolean create,int mode,long thread) {
        queue.postRunnable(() -> {
            try {
                if(com.my.televip.obfuscate.ArgsResolver.groupedStorage)
                    com.my.televip.compat.XposedBridge.invoke(MessagesStorage.class.getMethod("putMessagesStored",int.class,int.class,int.class,long.class,long.class,com.my.televip.virtuals.tgnet.TLRPC.messages_Messages.class,boolean.class),this,loadType,maxId,mode,dialog,thread,messages,create);
                else com.my.televip.compat.XposedBridge.invoke(MessagesStorage.class.getMethod("putMessagesStored",com.my.televip.virtuals.tgnet.TLRPC.messages_Messages.class,long.class,int.class,int.class,boolean.class,int.class,long.class),this,messages,dialog,loadType,maxId,create,mode,thread);
            }catch(Throwable error){throw new AssertionError(error);}
        });
    }
    public void putMessagesStored(int loadType,int maxId,int mode,long dialog,long thread,
            com.my.televip.virtuals.tgnet.TLRPC.messages_Messages messages,boolean create){
        putMessagesStored(messages,dialog,loadType,maxId,create,mode,thread);
    }
    public void putMessagesStored(com.my.televip.virtuals.tgnet.TLRPC.messages_Messages messages,long dialog,int loadType,int maxId,boolean create,int mode,long thread){
        for(Object value:messages.getMessages()) {
            com.my.televip.virtuals.tgnet.TLRPC.MessageData data=(com.my.televip.virtuals.tgnet.TLRPC.MessageData)value;
            database.history.put(data.id+":"+data.dialog,data);
        }
    }
    public void putLocalMessagesStored(java.util.ArrayList<Object> messages,boolean transaction,boolean unchanged,int mask,boolean ifMissing,int mode,long thread){
        for(Object value:messages){com.my.televip.virtuals.tgnet.TLRPC.MessageData data=(com.my.televip.virtuals.tgnet.TLRPC.MessageData)value;database.history.put(data.id+":"+data.dialog,data);}
    }

}
