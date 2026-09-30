package com.my.televip.Class;
import java.util.concurrent.ConcurrentHashMap;
public final class ClassLoad {
    public static final ConcurrentHashMap<String,Class<?>> classes = new ConcurrentHashMap<>();
    public static Class<?> getClass(String name) { return classes.get(name); }
}
