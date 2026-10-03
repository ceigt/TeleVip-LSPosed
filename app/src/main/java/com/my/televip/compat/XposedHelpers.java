package com.my.televip.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import com.my.televip.diagnostics.HookHealth;

/** Reflection helpers without any legacy Xposed framework dependency. */
public final class XposedHelpers {
    private XposedHelpers() {}

    public static Class<?> findClassIfExists(String name, ClassLoader loader) {
        try { return Class.forName(name, false, loader); }
        catch (Throwable ignored) { return null; }
    }

    private static Field field(Class<?> type, String name) {
        return ReflectionLookup.field(type, name);
    }

    private static Object get(Object target, Class<?> type, String name) {
        try { return field(type, name).get(target); }
        catch (IllegalAccessException error) { throw new IllegalStateException(error); }
    }

    private static void set(Object target, Class<?> type, String name, Object value) {
        try { field(type, name).set(target, value); }
        catch (IllegalAccessException error) { throw new IllegalStateException(error); }
    }

    public static Object getObjectField(Object object, String name) { return get(object, object.getClass(), name); }
    public static Object getStaticObjectField(Class<?> type, String name) { return get(null, type, name); }
    public static int getIntField(Object object, String name) { return (int) getObjectField(object, name); }
    public static int getStaticIntField(Class<?> type, String name) { return (int) getStaticObjectField(type, name); }
    public static long getLongField(Object object, String name) { return (long) getObjectField(object, name); }
    public static boolean getStaticBooleanField(Class<?> type, String name) { return (boolean) getStaticObjectField(type, name); }
    public static void setObjectField(Object object, String name, Object value) { set(object, object.getClass(), name, value); }
    public static void setIntField(Object object, String name, int value) { setObjectField(object, name, value); }
    public static void setBooleanField(Object object, String name, boolean value) { setObjectField(object, name, value); }

    private static Method method(Class<?> type, String name, Object[] args, boolean staticOnly) {
        return ReflectionLookup.method(type, name, args, staticOnly);
    }

    private static Object invoke(Method method, Object target, Object[] args) {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException error) { throw new IllegalStateException(error.getCause()); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    public static Object callMethod(Object object, String name, Object... args) {
        return invoke(method(object.getClass(), name, args, false), object, args);
    }
    public static Object callStaticMethod(Class<?> type, String name, Object... args) {
        return invoke(method(type, name, args, true), null, args);
    }
    public static Object newInstance(Class<?> type, Object... args) {
        try { return ReflectionLookup.constructor(type, args).newInstance(args); }
        catch (InvocationTargetException error) { throw new IllegalStateException(error.getCause()); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    private static Class<?>[] signature(Object[] args) {
        Class<?>[] types = new Class<?>[args.length - 1];
        for (int i = 0; i < types.length; i++) types[i] = (Class<?>) args[i];
        return types;
    }

    public static void findAndHookMethod(Class<?> type, String name, Object... args) {
        XC_MethodHook hook = (XC_MethodHook) args[args.length - 1];
        Class<?>[] signature = signature(args);
        try {
            Method method = ReflectionLookup.exactMethod(type, name, signature);
            method.setAccessible(true);
            XposedBridge.hookMethod(method, hook);
            HookHealth.resolved();
        } catch (NoSuchMethodException error) {
            HookHealth.missingMember(type.getName() + "#" + name + Arrays.toString(signature));
            throw new IllegalArgumentException(type.getName() + "#" + name + Arrays.toString(signature), error);
        }
    }

    public static void findAndHookConstructor(Class<?> type, Object... args) {
        XC_MethodHook hook = (XC_MethodHook) args[args.length - 1];
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(signature(args));
            constructor.setAccessible(true);
            XposedBridge.hookMethod(constructor, hook);
        } catch (NoSuchMethodException error) {
            throw new IllegalArgumentException("Constructor not found: " + type.getName(), error);
        }
    }
}
