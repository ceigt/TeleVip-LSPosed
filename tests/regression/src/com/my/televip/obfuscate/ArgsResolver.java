package com.my.televip.obfuscate;
import com.my.televip.compat.XC_MethodHook;
public final class ArgsResolver {
    public static boolean groupedStorage,groupedPush;
    public static Object[] merge(String name,Class<?>[] types,XC_MethodHook callback) {
        if(groupedPush&&name.equals("deleteMessagesByPush"))types=new Class<?>[]{long.class,long.class,java.util.ArrayList.class};
        if (groupedStorage && (name.equals("putMessages")||name.equals("putMessagesStored"))) types=new Class<?>[]{int.class,int.class,int.class,long.class,long.class,
                com.my.televip.virtuals.tgnet.TLRPC.messages_Messages.class,boolean.class};
        Object[] args = new Object[types.length+1];
        System.arraycopy(types,0,args,0,types.length);
        args[types.length] = callback;
        return args;
    }
}
