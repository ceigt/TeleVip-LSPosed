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

    public int getLastPtsValue() {
        return (int) XposedHelpers.callMethod(messagesStorage, Obfuscate.getMethodName("MessagesStorage", "getLastPtsValue"));
    }

    public static MessagesStorage getInstance(int num) {
        return new MessagesStorage(XposedHelpers.callStaticMethod(ClassLoad.getClass(ClassNames.MESSAGES_STORAGE), Obfuscate.getMethodName("MessagesStorage", "getInstance"), num));
    }

    public static MessagesStorage getMessagesStorage() {
        return getInstance(UserConfig.getSelectedAccount());
    }

}
