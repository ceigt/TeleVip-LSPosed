package com.my.televip.virtuals.messenger;
import android.os.Handler;
import com.my.televip.virtuals.SQLite.SQLiteDatabase;
public final class MessagesStorage {
    public final SQLiteDatabase database = new SQLiteDatabase();
    private final DispatchQueue queue;
    public long accountId = 101;
    public MessagesStorage(Handler handler) { queue = new DispatchQueue(handler); }
    public long getAccountUserId() { return accountId; }
    public DispatchQueue getStorageQueue() { return queue; }
    public SQLiteDatabase getDatabase() { return database; }
}
