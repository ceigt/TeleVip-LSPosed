package com.my.televip.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/** Reflection helpers without any legacy Xposed framework dependency. */
public final class XposedHelpers {
    private XposedHelpers() {}

    public static Class<?> findClassIfExists(String name, ClassLoader loader) {
        try { return Class.forName(name, false, loader); }
        catch (Throwable ignored) { return null; }
    }

    private static Field field(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field result = current.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new IllegalArgumentException("Field not found: " + type.getName() + "#" + name);
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

    private static Class<?> boxed(Class<?> type) {
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        return type;
    }

    private static boolean matches(Class<?>[] parameterTypes, Object[] args) {
        if (parameterTypes.length != args.length) return false;
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) {
                if (parameterTypes[i].isPrimitive()) return false;
            } else if (!boxed(parameterTypes[i]).isInstance(args[i])) return false;
        }
        return true;
    }

    private static Method method(Class<?> type, String name, Object[] args, boolean staticOnly) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method candidate : current.getDeclaredMethods()) {
                if (!candidate.getName().equals(name) || (staticOnly && !Modifier.isStatic(candidate.getModifiers()))) continue;
                if (matches(candidate.getParameterTypes(), args)) {
                    candidate.setAccessible(true);
                    return candidate;
                }
            }
        }
        throw new IllegalArgumentException("Method not found: " + type.getName() + "#" + name + "/" + args.length);
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
        for (Constructor<?> candidate : type.getDeclaredConstructors()) {
            if (!matches(candidate.getParameterTypes(), args)) continue;
            try { candidate.setAccessible(true); return candidate.newInstance(args); }
            catch (InvocationTargetException error) { throw new IllegalStateException(error.getCause()); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
        throw new IllegalArgumentException("Constructor not found: " + type.getName() + "/" + args.length);
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
            Method method = type.getDeclaredMethod(name, signature);
            method.setAccessible(true);
            XposedBridge.hookMethod(method, hook);
        } catch (NoSuchMethodException error) {
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
