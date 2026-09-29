package com.my.televip.virtuals.SQLite;

import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.tgnet.NativeByteBuffer;

import com.my.televip.compat.XposedHelpers;

public class SQLiteCursor {

    Object sqLiteCursor;

    public SQLiteCursor(Object obj){
        sqLiteCursor = obj;
    }

    public boolean next(){
        return (boolean) XposedHelpers.callMethod(sqLiteCursor, Obfuscate.getMethodName("SQLiteCursor", "next"));
    }

    public int intValue(int columnIndex) {
        return (int) XposedHelpers.callMethod(sqLiteCursor, Obfuscate.getMethodName("SQLiteCursor", "intValue"), columnIndex);
    }

    public long longValue(int columnIndex) {
        return (long) XposedHelpers.callMethod(sqLiteCursor, Obfuscate.getMethodName("SQLiteCursor", "longValue"), columnIndex);
    }

    public void dispose() {
        XposedHelpers.callMethod(sqLiteCursor, Obfuscate.getMethodName("SQLiteCursor", "dispose"));
    }

    public NativeByteBuffer byteBufferValue(int columnIndex){
        return new NativeByteBuffer(XposedHelpers.callMethod(sqLiteCursor, Obfuscate.getMethodName("SQLiteCursor", "byteBufferValue"), columnIndex));
    }

}
