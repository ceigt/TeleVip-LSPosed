package com.my.televip.features.ui;

import java.lang.reflect.*;

/** Resolve the delegate interface first; reflection order must not pick an unrelated overload. */
public final class AdResponseCallbacks {
    private AdResponseCallbacks() {}
    public static Method find(Object callback,Object response,boolean timestamp) throws NoSuchMethodException {
        Method found=null;
        for(Class<?> cls=callback.getClass();cls!=null;cls=cls.getSuperclass()) {
            for(Class<?> iface:cls.getInterfaces()) for(Method method:iface.getMethods()) {
                Class<?>[] p=method.getParameterTypes();
                if(method.getReturnType()!=void.class || p.length!=(timestamp?3:2) || !p[0].isInstance(response)
                        || p[1].isPrimitive() || timestamp && p[2]!=long.class) continue;
                if(found!=null && !found.equals(method)) throw new NoSuchMethodException("Ambiguous ad completion delegate");
                found=method;
            }
        }
        if(found==null) throw new NoSuchMethodException("Ad completion delegate unavailable");
        found.setAccessible(true);return found;
    }
    public static void invoke(Method method,Object callback,Object response) throws Throwable {
        try {method.invoke(callback,method.getParameterCount()==3 ? new Object[]{response,null,0L}:new Object[]{response,null});}
        catch(InvocationTargetException error) {throw error.getCause();}
    }
}
