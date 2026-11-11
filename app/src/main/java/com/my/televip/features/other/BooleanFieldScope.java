package com.my.televip.features.other;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/** UI-only temporary permission metadata, restored in reverse order even after failures. */
public final class BooleanFieldScope {
    private static final class Change {final Object object;final Field field;Change(Object o,Field f){object=o;field=f;}}
    private static final class Frame {final Object token;final List<Change> changes=new ArrayList<>();Frame(Object t){token=t;}}
    private final ThreadLocal<ArrayDeque<Frame>> frames=ThreadLocal.withInitial(ArrayDeque::new);
    public void enter(Object token){frames.get().push(new Frame(token));}
    public void clear(Object object,String name) throws ReflectiveOperationException {
        if(object==null)return;Field field=null;
        for(Class<?> c=object.getClass();c!=null&&field==null;c=c.getSuperclass())try{field=c.getDeclaredField(name);}catch(NoSuchFieldException ignored){}
        if(field==null||field.getType()!=boolean.class||Modifier.isStatic(field.getModifiers()))throw new NoSuchFieldException(name);
        if(frames.get().isEmpty())throw new IllegalStateException("Missing UI scope");
        field.setAccessible(true);if(field.getBoolean(object)){frames.get().peek().changes.add(new Change(object,field));field.setBoolean(object,false);}
    }
    public void exit(Object token){
        ArrayDeque<Frame> stack=frames.get();if(stack.isEmpty())return;Frame frame=stack.pop();RuntimeException failure=null;
        try {
            for(int i=frame.changes.size()-1;i>=0;i--){Change c=frame.changes.get(i);try{c.field.setBoolean(c.object,true);}catch(ReflectiveOperationException error){if(failure==null)failure=new IllegalStateException(error);else failure.addSuppressed(error);}}
            if(frame.token!=token)throw new IllegalStateException("Unbalanced UI scope");
            if(failure!=null)throw failure;
        }finally{if(stack.isEmpty())frames.remove();}
    }
}
