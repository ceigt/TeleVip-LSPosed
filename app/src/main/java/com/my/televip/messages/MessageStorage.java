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
import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.compat.XposedHelpers;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.tgnet.TLRPC;

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
            com.my.televip.Database.LocalStateStore.Session session=com.my.televip.features.messages.LocalData.captureSession(messagesStorage);
            long accountId = messagesStorage.getAccountUserId();
            if (accountId == 0) return;
            // Telegram owns this connection; use its account storage queue.
            messagesStorage.getStorageQueue().postRunnable(() -> {
                try {
                    if (!ConfigManager.showDeletedMessages.isEnable() || messagesStorage.getAccountUserId() != accountId) return;
                    SQLiteDatabase db = messagesStorage.getDatabase();
                    markTable(messagesStorage, db, "messages_v2", dialogId, snapshot,session);
                    markTable(messagesStorage, db, "messages_topics", dialogId, snapshot,session);
                } catch (Throwable error) { Logger.e(error); }
            });
        } catch (Throwable error) { Logger.e(error); }
    }

    /** Repair pins retained by an earlier module build, only for locally flagged cached rows. */
    public static void reconcileDeletedPins(MessagesStorage storage, long dialog) {
        if (dialog == 0 || (dialog & 0xffffffffL) == 0) return;
        long account = storage.getAccountUserId();
        if (account == 0) return;
        storage.getStorageQueue().postRunnable(() -> {
            if (!ConfigManager.showDeletedMessages.isEnable() || storage.getAccountUserId() != account) return;
            SQLiteCursor cursor = null;
            ArrayList<Integer> ids = new ArrayList<>();
            try {
                cursor = storage.getDatabase().queryFinalized("SELECT m.data,m.mid FROM chat_pinned_v2 p INNER JOIN messages_v2 m ON m.uid=p.uid AND m.mid=p.mid WHERE p.uid=" + dialog + " LIMIT 1000", new Object[0]);
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data == null || data.nativeByteBuffer == null) continue;
                    try {
                        data.position(0);
                        int constructor=data.readInt32(true);
                        if(constructor==0x22eb6aba||constructor==0x9f8d60bb||constructor==0x05f46804||constructor==0x83e5de54)continue;
                        data.position(4);
                        if ((data.readInt32(true) & ShowDeletedMessages.FLAG_DELETED) != 0) ids.add(cursor.intValue(1));
                    } finally { data.reuse(); }
                }
            } catch (Throwable error) { Logger.e(error); }
            finally { if (cursor != null) cursor.dispose(); }
            if (!ids.isEmpty() && storage.getAccountUserId() == account) storage.clearPinnedReferences(dialog, ids);
        });
    }

    private static NativeByteBuffer normalizeLegacy(NativeByteBuffer data,int constructor,long account,int id) throws Exception {
        Object old=TLRPC.Message.TLdeserialize(data,constructor,true).get_Message();
        XposedHelpers.setIntField(old,Obfuscate.getFieldName("TLRPC$Message","id"),id);
        boolean service=constructor==0x9f8d60bb,empty=constructor==0x83e5de54;
        String target=empty?"TLRPC$TL_messageEmpty":service?"TLRPC$TL_messageService":"TLRPC$TL_message";
        if(!service&&!empty)XposedHelpers.callMethod(old,Obfuscate.getMethodName("TLRPC$Message","readAttachPath"),data.nativeByteBuffer,account);
        Object replacement=XposedHelpers.newInstance(ClassLoad.getClass("org.telegram.tgnet."+target));
        for(Class<?> type=old.getClass();type!=null&&type!=Object.class;type=type.getSuperclass())
            for(java.lang.reflect.Field field:type.getDeclaredFields())
                if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())&&field.getDeclaringClass().isInstance(replacement)){
                    field.setAccessible(true);field.set(replacement,field.get(old));
                }
        int size=((Number)XposedHelpers.callMethod(replacement,Obfuscate.getMethodName("TLObject","getObjectSize"))).intValue();
        NativeByteBuffer result=new NativeByteBuffer(XposedHelpers.newInstance(ClassLoad.getClass(ClassNames.NATIVE_BYTE_BUFFER),size));
        try{XposedHelpers.callMethod(replacement,Obfuscate.getMethodName(target,"serializeToStream"),result.nativeByteBuffer);result.position(0);return result;}
        catch(Throwable error){result.reuse();throw new IllegalStateException("Cannot normalize legacy message",error);}
    }
    private static void markTable(MessagesStorage storage, SQLiteDatabase db, String table, long dialogId, ArrayList<Integer> ids,com.my.televip.Database.LocalStateStore.Session session) throws Exception {
        SQLiteCursor cursor = null;
        SQLitePreparedStatement statement = null;
        java.util.Map<Long,ArrayList<Integer>> pins = new java.util.HashMap<>();
        if ("messages_v2".equals(table) && dialogId != 0) pins.put(dialogId, new ArrayList<>(ids));
        try {
            cursor = db.queryFinalized("SELECT data,mid,uid FROM " + table + " WHERE "
                    + (dialogId == 0 ? "is_channel" : "uid") + " = " + dialogId
                    + " AND mid IN (" + TextUtils.join(",", ids) + ")", new Object[0]);
            statement = db.executeFast("UPDATE " + table + " SET data = ? WHERE uid = ? AND mid = ?");
            while (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data == null || data.nativeByteBuffer == null) continue;
                try {
                    data.position(0);
                    int constructor=data.readInt32(true);
                    if(constructor==0x22eb6aba||constructor==0x9f8d60bb||constructor==0x05f46804||constructor==0x83e5de54){
                        NativeByteBuffer normalized=normalizeLegacy(data,constructor,storage.getAccountUserId(),cursor.intValue(1));
                        data.reuse();data=normalized;
                    }
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
                    if ("messages_v2".equals(table)) {
                        ArrayList<Integer> group = pins.computeIfAbsent(cursor.longValue(2), key -> new ArrayList<>());
                        if (!group.contains(cursor.intValue(1))) group.add(cursor.intValue(1));
                    }
                    com.my.televip.features.messages.LocalData.capture(storage, cursor.longValue(2), cursor.intValue(1), data,session);
                } finally { data.reuse(); }
            }
        } finally {
            try { if (cursor != null) cursor.dispose(); }
            finally { if (statement != null) statement.dispose(); }
            for (java.util.Map.Entry<Long,ArrayList<Integer>> pin : pins.entrySet())
                storage.clearPinnedReferences(pin.getKey(), pin.getValue());
        }
    }
}
