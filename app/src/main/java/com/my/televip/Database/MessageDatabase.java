package com.my.televip.Database;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.my.televip.logging.Logger;
import com.my.televip.messages.MessageStorage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public class MessageDatabase extends SQLiteOpenHelper {
    private final Object sessionLock = new Object();
    private final Map<Long, Long> generations = new HashMap<>();
    private final Set<Long> pendingCleanup = new HashSet<>();
    private final SharedPreferences cleanupPreferences;

    public static final class AccountSession {
        public final long accountId;
        private final long generation;
        private AccountSession(long accountId, long generation) {
            this.accountId = accountId;
            this.generation = generation;
        }
    }

    public static final class Edit {
        public final int number;
        public final String text;
        public final long capturedAt;
        Edit(int number, String text, long capturedAt) {
            this.number = number;
            this.text = text;
            this.capturedAt = capturedAt;
        }
    }

    public MessageDatabase(Context context) { this(context, getDataBasePath()); }

    /** Explicit path also permits regression tests in an isolated database. */
    public MessageDatabase(Context context, String path) {
        super(context.getApplicationContext(), path, null, 2);
        cleanupPreferences = context.getApplicationContext().getSharedPreferences("TeleVipHistoryCleanup", Context.MODE_PRIVATE);
    }

    public static String getDataBasePath() {
        return new File(MessageStorage.getStorageFile(), "saveMessages.db").getAbsolutePath();
    }

    // Keep one lock order, including lazy opening and onOpen cleanup.
    @Override public SQLiteDatabase getWritableDatabase() {
        synchronized (sessionLock) { return super.getWritableDatabase(); }
    }

    @Override public SQLiteDatabase getReadableDatabase() {
        synchronized (sessionLock) { return super.getReadableDatabase(); }
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("PRAGMA secure_delete=ON", null)) {
            cursor.moveToFirst();
        }
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages (account_id INTEGER NOT NULL, dialog_id INTEGER NOT NULL,"
                + "msg_id INTEGER NOT NULL, msg_count INTEGER NOT NULL, message TEXT NOT NULL,"
                + "message_date INTEGER NOT NULL, PRIMARY KEY(account_id,dialog_id,msg_id,msg_count))");
    }

    @Override public void onOpen(SQLiteDatabase db) {
        synchronized (sessionLock) {
            // A persisted tombstone completes logout cleanup after process death.
            for (String key : cleanupPreferences.getAll().keySet()) {
                if (!key.startsWith("pending_")) continue;
                long accountId = Long.parseLong(key.substring("pending_".length()));
                deleteAccountLocked(db, accountId);
            }
        }
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1 && newVersion == 2) {
            // Old rows contain sender IDs, not reliable account/dialog identities.
            // Preserve them without making them visible to any logged-in account.
            db.execSQL("ALTER TABLE messages RENAME TO legacy_messages_unattributed");
            onCreate(db);
            Logger.l("Legacy edit history retained in an isolated table");
        } else {
            throw new IllegalStateException("Unsupported history migration " + oldVersion + " -> " + newVersion);
        }
    }

    public AccountSession session(long accountId) {
        synchronized (sessionLock) {
            return new AccountSession(accountId, generations.getOrDefault(accountId, 0L));
        }
    }

    public boolean isCurrent(AccountSession session) {
        synchronized (sessionLock) { return currentLocked(session); }
    }

    private boolean currentLocked(AccountSession session) {
        return session != null && session.accountId > 0
                && session.generation == generations.getOrDefault(session.accountId, 0L)
                && !pendingCleanup.contains(session.accountId)
                && !cleanupPreferences.contains("pending_" + session.accountId);
    }

    public void addMessage(AccountSession session, long dialogId, int messageId, String text, long capturedAt) {
        if (dialogId == 0 || messageId <= 0 || text == null) return;
        MessageStorage.post(() -> {
            synchronized (sessionLock) {
                if (!currentLocked(session)) return;
                SQLiteDatabase db = getWritableDatabase();
                db.beginTransaction();
                try {
                    int number;
                    try (Cursor cursor = db.rawQuery("SELECT COALESCE(MAX(msg_count),0)+1 FROM messages"
                            + " WHERE account_id=? AND dialog_id=? AND msg_id=?",
                            key(session, dialogId, messageId))) {
                        cursor.moveToFirst();
                        number = cursor.getInt(0);
                    }
                    ContentValues values = new ContentValues();
                    values.put("account_id", session.accountId);
                    values.put("dialog_id", dialogId);
                    values.put("msg_id", messageId);
                    values.put("msg_count", number);
                    values.put("message", text);
                    values.put("message_date", capturedAt);
                    db.insertOrThrow("messages", null, values);
                    db.setTransactionSuccessful();
                } finally { db.endTransaction(); }
            }
        });
    }

    public boolean hasHistory(AccountSession session, long dialogId, int messageId) {
        synchronized (sessionLock) {
            if (!currentLocked(session)) return false;
            try (Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT 1 FROM messages WHERE account_id=? AND dialog_id=? AND msg_id=? LIMIT 1",
                    key(session, dialogId, messageId))) {
                return cursor.moveToFirst();
            } catch (Throwable error) { Logger.e(error); return false; }
        }
    }

    public List<Edit> getHistory(AccountSession session, long dialogId, int messageId) {
        List<Edit> result = new ArrayList<>();
        synchronized (sessionLock) {
            if (!currentLocked(session)) return result;
            try (Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT msg_count,message,message_date FROM messages"
                            + " WHERE account_id=? AND dialog_id=? AND msg_id=? ORDER BY msg_count",
                    key(session, dialogId, messageId))) {
                while (cursor.moveToNext()) result.add(new Edit(cursor.getInt(0), cursor.getString(1), cursor.getLong(2)));
            }
        }
        return result;
    }

    /** Revoke queued work immediately; delete only this account's attributed rows. */
    public void clearAccount(long accountId) {
        if (accountId <= 0) return;
        synchronized (sessionLock) {
            generations.put(accountId, generations.getOrDefault(accountId, 0L) + 1);
            pendingCleanup.add(accountId);
            if (!cleanupPreferences.edit().putBoolean("pending_" + accountId, true).commit())
                Logger.w("Could not persist history logout cleanup");
            MessageStorage.post(() -> {
                synchronized (sessionLock) { deleteAccountLocked(getWritableDatabase(), accountId); }
            });
        }
    }

    private void deleteAccountLocked(SQLiteDatabase db, long accountId) {
        db.delete("messages", "account_id=?", new String[]{Long.toString(accountId)});
        if (cleanupPreferences.edit().remove("pending_" + accountId).commit())
            pendingCleanup.remove(accountId);
        else Logger.w("History logout cleanup will be retried");
    }

    private static String[] key(AccountSession session, long dialogId, int messageId) {
        return new String[]{Long.toString(session.accountId), Long.toString(dialogId), Integer.toString(messageId)};
    }
}
