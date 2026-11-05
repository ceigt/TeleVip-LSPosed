package com.my.televip.compat;

import com.my.televip.obfuscate.dex.DexNames;
import java.lang.reflect.Method;
import java.util.Arrays;

public final class HookParameterTypesTest {
    static boolean initialized;
    public static class Host { static { initialized=true; } }
    public static class Reordered {
        public void format(int[] rounded,int count) {}
        public void storage(int mode,int maxId,int source,long dialog,long thread,Host response,boolean create) {}
    }
    public static void main(String[] args)throws Exception {
        ClassLoader loader=HookParameterTypesTest.class.getClassLoader();
        String host=Host.class.getName();
        Class<?>[] format=HookParameterTypes.resolve(new String[]{"[I","I"},loader);
        Method f=Reordered.class.getMethod("format",format);
        if(f.getParameterTypes()[0]!=int[].class)throw new AssertionError("Reordered formatter lost array argument");
        Class<?>[] storage=HookParameterTypes.resolve(new String[]{"I","I","I","J","J",host,"Z"},loader);
        Reordered.class.getMethod("storage",storage);
        if(initialized)throw new AssertionError("Resolving a host parameter initialized it");
        if(HookParameterTypes.resolve(new String[]{"[L"+host.replace('.','/')+";"},loader)[0]!=Host[].class)throw new AssertionError("Host array descriptor");
        if(HookParameterTypes.resolve(new String[]{"int[][]"},loader)[0]!=int[][].class)throw new AssertionError("Java nested array");
        if(!Arrays.equals(HookParameterTypes.resolve(new String[]{"Z","B","S","C","I","J","F","D"},loader),
                new Class<?>[]{boolean.class,byte.class,short.class,char.class,int.class,long.class,float.class,double.class}))throw new AssertionError("Primitive descriptors");
        for(String d:new String[]{"I","Z","J","[I","[Lorg/telegram/Host;"})
            if(!DexNames.toDescriptor(DexNames.toJavaName(d)).equals(d))throw new AssertionError("Descriptor roundtrip "+d);
        try {HookParameterTypes.resolve(new String[]{"absent.host.Parameter"},loader);throw new AssertionError("Missing host parameter accepted");}
        catch(IllegalStateException expected) {}
        System.out.println("HOOK_PARAMETER_TYPES_PASS: reordered formatter/storage, arrays, primitives, no initialization, missing-type failure");
    }
}
