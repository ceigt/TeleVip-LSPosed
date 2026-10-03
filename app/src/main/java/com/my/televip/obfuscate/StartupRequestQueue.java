package com.my.televip.obfuscate;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded startup queue. Tokens are scoped to the exact connection instance. */
public final class StartupRequestQueue<T> {
    public enum Result { QUEUED, RELEASED, FULL }
    private final int capacity;
    private boolean released;
    private final LinkedHashMap<Key,T> entries=new LinkedHashMap<>();
    public StartupRequestQueue(int capacity) {
        if(capacity<=0) throw new IllegalArgumentException("capacity");
        this.capacity=capacity;
    }
    public synchronized Result offer(Object connection,int token,T item) {
        if(released) return Result.RELEASED;
        Key key=new Key(connection,token);
        if(!entries.containsKey(key) && entries.size()>=capacity) return Result.FULL;
        entries.put(key,item);return Result.QUEUED;
    }
    public synchronized void cancel(Object connection,int token) {entries.remove(new Key(connection,token));}
    public synchronized void release() {released=true;}
    public synchronized T poll() {
        java.util.Iterator<Map.Entry<Key,T>> iterator=entries.entrySet().iterator();
        if(!iterator.hasNext()) return null;
        T value=iterator.next().getValue();iterator.remove();return value;
    }
    public synchronized int size() {return entries.size();}
    private static final class Key {
        final Object connection;final int token;
        Key(Object connection,int token) {this.connection=connection;this.token=token;}
        @Override public int hashCode() {return System.identityHashCode(connection)*31+token;}
        @Override public boolean equals(Object other) {return other instanceof Key && ((Key)other).connection==connection && ((Key)other).token==token;}
    }
}
