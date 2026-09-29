package com.my.televip.virtuals.SQLite;

import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;

import com.my.televip.compat.XposedHelpers;

public class SQLiteDatabase {

    Object sqLiteDatabase;

    public SQLiteDatabase(Object obj){ sqLiteDatabase = obj; }

    public SQLiteCursor queryFinalized(String s, Object[] objects){
        return new SQLiteCursor(XposedHelpers.callMethod(sqLiteDatabase, Obfuscate.getMethodName("SQLiteDatabase", "queryFinalized"), s, objects));
    }

    public SQLitePreparedStatement executeFast(String s){
        return new SQLitePreparedStatement(XposedHelpers.callMethod(sqLiteDatabase, Obfuscate.getMethodName("SQLiteDatabase", "executeFast"), s));
    }

}
