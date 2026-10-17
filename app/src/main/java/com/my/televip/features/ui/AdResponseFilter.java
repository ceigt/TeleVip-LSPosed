package com.my.televip.features.ui;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** Match the protocol reader exactly, and replace answers only after the stream was consumed. */
public final class AdResponseFilter {
    private AdResponseFilter() {}
    public static Method reader(Class<?> request, Class<?> stream, Class<?> tlObject, Class<?> empty) {
        if (request == null || stream == null || tlObject == null || empty == null) return null;
        Method found = null;
        for (Method method : request.getDeclaredMethods()) {
            if (!method.getName().equals("deserializeResponse") || Modifier.isStatic(method.getModifiers())
                    || method.isBridge() || !tlObject.isAssignableFrom(method.getReturnType())
                    || !method.getReturnType().isAssignableFrom(empty)
                    || !Arrays.equals(method.getParameterTypes(), new Class<?>[]{stream, int.class, boolean.class})) continue;
            if (found != null) return null;
            found = method;
        }
        return found;
    }
    /** Prepared once for a known empty protocol type; constructor failures are never bypassed. */
    public static final class EmptyResponse {
        private final Class<?> type;
        private final Constructor<?> constructor;
        private final Object allocator;
        private final Method allocate;
        private final List<Field> lists;

        private EmptyResponse(Class<?> type) throws ReflectiveOperationException {
            if (type == null || type.isInterface() || type.isArray() || type.isPrimitive()
                    || Modifier.isAbstract(type.getModifiers()))
                throw new IllegalArgumentException("Expected a concrete empty protocol response");
            this.type = type;
            Constructor<?> found;
            try { found = type.getDeclaredConstructor(); }
            catch (NoSuchMethodException absent) { found = null; }
            constructor = found;
            if (constructor != null) {
                constructor.setAccessible(true);
                allocator = null;
                allocate = null;
                lists = java.util.Collections.emptyList();
                return;
            }
            // R8 may retain a response type for instanceof checks but remove all constructors.
            // Restrict this escape hatch to the empty ad types selected by the caller.
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field singleton = unsafeType.getDeclaredField("theUnsafe");
            singleton.setAccessible(true);
            allocator = singleton.get(null);
            allocate = unsafeType.getMethod("allocateInstance", Class.class);
            lists = new ArrayList<>();
            for (Class<?> owner = type; owner != null && owner != Object.class; owner = owner.getSuperclass()) {
                for (Field field : owner.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    Class<?> fieldType = field.getType();
                    if (!Collection.class.isAssignableFrom(fieldType)) continue;
                    if (!fieldType.isAssignableFrom(ArrayList.class))
                        throw new IllegalArgumentException("Unsupported empty response collection: " + field);
                    field.setAccessible(true);
                    lists.add(field);
                }
            }
            create(); // Validate the fallback before installing the response hook.
        }

        public Object create() throws ReflectiveOperationException {
            if (constructor != null) return constructor.newInstance();
            Object response = allocate.invoke(allocator, type);
            for (Field field : lists) field.set(response, new ArrayList<>());
            return response;
        }
    }

    public static EmptyResponse emptyResponse(Class<?> type) throws ReflectiveOperationException {
        return new EmptyResponse(type);
    }

    public static Object answer(Object response, EmptyResponse empty, boolean enabled) throws ReflectiveOperationException {
        if (!enabled || response == null || empty.type.isInstance(response)) return response;
        return empty.create();
    }
}
