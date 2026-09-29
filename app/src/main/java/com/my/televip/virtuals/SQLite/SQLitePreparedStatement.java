package com.my.televip.virtuals.SQLite;

import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.tgnet.NativeByteBuffer;

import com.my.televip.compat.XposedHelpers;

public class SQLitePreparedStatement {

    Object sQLitePreparedStatement;

    public SQLitePreparedStatement(Object sQLitePreparedStatement){
        this.sQLitePreparedStatement = sQLitePreparedStatement;
    }

    public void requery() {
        XposedHelpers.callMethod(sQLitePreparedStatement, Obfuscate.getMethodName("SQLitePreparedStatement","requery"));
    }

    public void step() {
        XposedHelpers.callMethod(sQLitePreparedStatement, Obfuscate.getMethodName("SQLitePreparedStatement","step"));
    }

    public void dispose() {
        XposedHelpers.callMethod(sQLitePreparedStatement, Obfuscate.getMethodName("SQLitePreparedStatement","dispose"));
    }

    public void bindByteBuffer(int index, NativeByteBuffer value) {
        XposedHelpers.callMethod(sQLitePreparedStatement, Obfuscate.getMethodName("SQLitePreparedStatement","bindByteBuffer"), index, value.nativeByteBuffer);
    }

    public void bindLong(int index, long value) {
        XposedHelpers.callMethod(sQLitePreparedStatement, Obfuscate.getMethodName("SQLitePreparedStatement","bindLong"), index, value);
    }

    public void bindInteger(int index, int value) {
        XposedHelpers.callMethod(sQLitePreparedStatement, Obfuscate.getMethodName("SQLitePreparedStatement","bindInteger"), index, value);
    }

}
