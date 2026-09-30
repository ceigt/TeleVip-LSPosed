package com.my.televip.messages;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.text.TextUtils;
import com.my.televip.application.ApplicationLoaderHook;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.features.messages.ShowDeletedMessages;
import com.my.televip.logging.Logger;
import com.my.televip.virtuals.SQLite.SQLiteCursor;
import com.my.televip.virtuals.SQLite.SQLiteDatabase;
import com.my.televip.virtuals.SQLite.SQLitePreparedStatement;
import com.my.televip.virtuals.messenger.MessagesStorage;
import com.my.televip.virtuals.tgnet.NativeByteBuffer;
import java.io.File;
import java.util.ArrayList;

public final class MessageStorage {
    private static final Handler storage = createHandler();

    private static Handler createHandler() {
        HandlerThread thread = new HandlerThread("TeleVip - Storage", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        return new Handler(thread.getLooper());
    }

    public static File getStorageFile() {
        File dir = new File(ApplicationLoaderHook.getApplicationContext().getFilesDir().getParentFile(), "TeleVip");
        if (!dir.exists() && !dir.mkdir()) Logger.w("Cannot create " + dir.getAbsolutePath());
        return dir;
    }

    /** Protect the task itself, not just Handler.post(). */
    public static void post(Runnable task) {
        storage.post(() -> {
            try { task.run(); } catch (Throwable error) { Logger.e(error); }
        });
    }

    public static void markMessagesDeleted(MessagesStorage messagesStorage, long dialogId, ArrayList<Integer> ids) {
        if (ids == null || ids.isEmpty()) return;
        ArrayList<Integer> snapshot = new ArrayList<>(ids);
        try {
            long accountId = messagesStorage.getAccountUserId();
            if (accountId == 0) return;
            // Telegram owns this connection; use its account storage queue.
            messagesStorage.getStorageQueue().postRunnable(() -> {
                try {
                    if (!ConfigManager.showDeletedMessages.isEnable() || messagesStorage.getAccountUserId() != accountId) return;
                    SQLiteDatabase db = messagesStorage.getDatabase();
                    markTable(db, "messages_v2", dialogId, snapshot);
                    markTable(db, "messages_topics", dialogId, snapshot);
                } catch (Throwable error) { Logger.e(error); }
            });
        } catch (Throwable error) { Logger.e(error); }
    }

    private static void markTable(SQLiteDatabase db, String table, long dialogId, ArrayList<Integer> ids) {
        SQLiteCursor cursor = null;
        SQLitePreparedStatement statement = null;
        try {
            cursor = db.queryFinalized("SELECT data,mid,uid FROM " + table + " WHERE "
                    + (dialogId == 0 ? "is_channel" : "uid") + " = " + dialogId
                    + " AND mid IN (" + TextUtils.join(",", ids) + ")", new Object[0]);
            statement = db.executeFast("UPDATE " + table + " SET data = ? WHERE uid = ? AND mid = ?");
            while (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data == null || data.nativeByteBuffer == null) continue;
                try {
                    data.position(4);
                    int flags = data.readInt32(true);
                    data.position(4);
                    data.writeInt32(flags | ShowDeletedMessages.FLAG_DELETED);
                    data.position(0);
                    statement.requery();
                    statement.bindByteBuffer(1, data);
                    statement.bindLong(2, cursor.longValue(2));
                    statement.bindInteger(3, cursor.intValue(1));
                    statement.step();
                } finally { data.reuse(); }
            }
        } finally {
            try { if (cursor != null) cursor.dispose(); }
            finally { if (statement != null) statement.dispose(); }
        }
    }
}
