package com.my.televip.obfuscate;
import com.my.televip.compat.XC_MethodHook;
public final class ArgsResolver {
    public static Object[] merge(String name,Class<?>[] types,XC_MethodHook callback) {
        Object[] args = new Object[types.length+1];
        System.arraycopy(types,0,args,0,types.length);
        args[types.length] = callback;
        return args;
    }
}
