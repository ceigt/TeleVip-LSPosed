package com.my.televip.virtuals.messenger;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.SQLite.SQLiteDatabase;

import com.my.televip.compat.XposedHelpers;

public class MessagesStorage {

    Object messagesStorage;

    public MessagesStorage(Object obj) {
        messagesStorage = obj;
    }

    public long getAccountUserId() {
        return new BaseController(messagesStorage).getUserConfig().getClientUserId();
    }

    public SQLiteDatabase getDatabase() {

        return new SQLiteDatabase(XposedHelpers.callMethod(messagesStorage, Obfuscate.getMethodName("MessagesStorage", "getDatabase")));
    }

    public DispatchQueue getStorageQueue() {

        return new DispatchQueue(XposedHelpers.callMethod(messagesStorage, Obfuscate.getMethodName("MessagesStorage", "getStorageQueue")));
    }

    public static MessagesStorage getInstance(int num) {
        return new MessagesStorage(XposedHelpers.callStaticMethod(ClassLoad.getClass(ClassNames.MESSAGES_STORAGE), Obfuscate.getMethodName("MessagesStorage", "getInstance"), num));
    }

    public void deletePushMessages(long dialog, java.util.ArrayList<Integer> ids) {
        Class<?>[] types = com.my.televip.obfuscate.ArgsResolver.resolveObject("deletePushMessages", new Class[]{long.class, java.util.ArrayList.class});
        if (types[0] == long.class) XposedHelpers.callMethod(messagesStorage, Obfuscate.getMethodName("MessagesStorage", "deletePushMessages"), dialog, ids);
        else XposedHelpers.callMethod(messagesStorage, Obfuscate.getMethodName("MessagesStorage", "deletePushMessages"), ids, dialog);
    }

    public void clearPinnedReferences(long dialog, java.util.ArrayList<Integer> ids) {
        if (dialog == 0 || ids == null || ids.isEmpty()) return;
        java.util.ArrayList<Integer> snapshot = new java.util.ArrayList<>(ids);
        long accountId = getAccountUserId();
        int slot = new BaseController(messagesStorage).getCurrentAccount();
        // This native method updates local SQL and notifies the view; it sends no unpin request.
        XposedHelpers.callMethod(messagesStorage, Obfuscate.getMethodName("MessagesStorage", "updatePinnedMessages"),
                dialog, snapshot, false, -1, 0, false, null);
        com.my.televip.application.AndroidUtilities.runOnUIThread(() -> {
            try {
                if (accountId == 0 || getAccountUserId() != accountId) return;
                MessagesController controller = MessagesController.getInstance(slot);
                Object full = dialog > 0 ? controller.getUserFull(dialog) : controller.getChatFull(-dialog);
                com.my.televip.messages.PinnedReferences.clearCached(full,
                        Obfuscate.getFieldName(dialog > 0 ? "TLRPC$UserFull" : "TLRPC$ChatFull", "pinned_msg_id"), snapshot);
            } catch (Throwable error) { com.my.televip.logging.Logger.e(error); }
        });
    }

    public static MessagesStorage getMessagesStorage() {
        return getInstance(UserConfig.getSelectedAccount());
    }

}
