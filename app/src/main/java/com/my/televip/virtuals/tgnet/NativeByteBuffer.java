package com.my.televip.virtuals.tgnet;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;

import com.my.televip.compat.XposedHelpers;

public class NativeByteBuffer {

    public final Object nativeByteBuffer;

    public NativeByteBuffer(Object obj){
        nativeByteBuffer = obj;
    }
    public NativeByteBuffer(boolean calculate){
        nativeByteBuffer = XposedHelpers.newInstance(ClassLoad.getClass(ClassNames.NATIVE_BYTE_BUFFER), calculate);
    }

    public void reuse(){
        XposedHelpers.callMethod(nativeByteBuffer, Obfuscate.getMethodName("NativeByteBuffer", "reuse"));
    }

    public int readInt32(boolean exception) {
        return (int) XposedHelpers.callMethod(nativeByteBuffer, Obfuscate.getMethodName("NativeByteBuffer","readInt32"), exception);
    }

    public void position(int i) {
        XposedHelpers.callMethod(nativeByteBuffer, Obfuscate.getMethodName("NativeByteBuffer","position"), i);
    }

    public void writeInt32(int i) {
        XposedHelpers.callMethod(nativeByteBuffer, Obfuscate.getMethodName("NativeByteBuffer","writeInt32"), i);
    }


}
