package com.my.televip.compat;

import java.lang.reflect.*;
import java.util.*;

/** Deterministic reflection lookup. Hook lookup is exact; invocation rejects ambiguous overloads. */
public final class ReflectionLookup {
    private ReflectionLookup() {}
    private static final int LIMIT = 1024;
    private static final Map<Key, AccessibleObject> CACHE = new LinkedHashMap<Key, AccessibleObject>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, AccessibleObject> entry) { return size() > LIMIT; }
    };
    private static final class Key {
        final Class<?> owner;
        final String name;
        final Class<?>[] types;
        final int mode;
        Key(Class<?> owner, String name, Class<?>[] types, int mode) {
            this.owner = Objects.requireNonNull(owner); this.name = Objects.requireNonNull(name);
            this.types = types.clone(); this.mode = mode;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key k = (Key) other;
            return owner == k.owner && mode == k.mode && name.equals(k.name) && Arrays.equals(types, k.types);
        }
        @Override public int hashCode() { return Objects.hash(owner, name, mode, Arrays.hashCode(types)); }
    }
    private static AccessibleObject cached(Key key) { synchronized (CACHE) { return CACHE.get(key); } }
    private static <T extends AccessibleObject> T remember(Key key, T result) {
        result.setAccessible(true);
        synchronized (CACHE) { CACHE.put(key, result); }
        return result;
    }
    public static Field field(Class<?> owner, String name) {
        Key key = new Key(owner, name, new Class<?>[0], 0);
        Field hit = (Field) cached(key); if (hit != null) return hit;
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try { return remember(key, c.getDeclaredField(name)); }
            catch (NoSuchFieldException ignored) {}
        }
        throw new IllegalArgumentException("Field not found: " + owner.getName() + "#" + name);
    }
    public static Method exactMethod(Class<?> owner, String name, Class<?>... types) throws NoSuchMethodException {
        for (Class<?> type : types) if (type == null) throw new NoSuchMethodException("Unresolved parameter: " + name);
        Key key = new Key(owner, name, types, 1);
        Method hit = (Method) cached(key); if (hit != null) return hit;
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try { return remember(key, c.getDeclaredMethod(name, types)); }
            catch (NoSuchMethodException ignored) {}
        }
        // Includes public interface defaults; getMethod also preserves exact parameter matching.
        return remember(key, owner.getMethod(name, types));
    }
    public static Method method(Class<?> owner, String name, Object[] args, boolean staticOnly) {
        Key key = new Key(owner, name, types(args), staticOnly ? 3 : 2);
        Method hit = (Method) cached(key); if (hit != null) return hit;
        List<Method> candidates = new ArrayList<>();
        Set<List<Class<?>>> seen = new HashSet<>();
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            add(c.getDeclaredMethods(), name, staticOnly, seen, candidates);
        }
        add(owner.getMethods(), name, staticOnly, seen, candidates);
        return remember(key, choose(candidates, args, owner.getName() + "#" + name));
    }
    private static void add(Method[] methods, String name, boolean staticOnly, Set<List<Class<?>>> seen, List<Method> out) {
        // Covariant bridge methods share parameters with their actual implementation.
        Arrays.sort(methods, Comparator.comparing(Method::isBridge));
        for (Method m : methods) {
            if (!m.getName().equals(name) || Modifier.isStatic(m.getModifiers()) != staticOnly) continue;
            List<Class<?>> signature = Arrays.asList(m.getParameterTypes());
            if (seen.add(signature)) out.add(m);
        }
    }
    public static Constructor<?> constructor(Class<?> owner, Object[] args) {
        Key key = new Key(owner, "<init>", types(args), 4);
        Constructor<?> hit = (Constructor<?>) cached(key); if (hit != null) return hit;
        return remember(key, choose(Arrays.asList(owner.getDeclaredConstructors()), args, owner.getName()));
    }
    private static Class<?>[] types(Object[] args) {
        Class<?>[] result = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) result[i] = args[i] == null ? null : args[i].getClass();
        return result;
    }
    private static Class<?> primitive(Class<?> type) {
        if (type == Boolean.class) return boolean.class;
        if (type == Byte.class) return byte.class;
        if (type == Short.class) return short.class;
        if (type == Character.class) return char.class;
        if (type == Integer.class) return int.class;
        if (type == Long.class) return long.class;
        if (type == Float.class) return float.class;
        if (type == Double.class) return double.class;
        return null;
    }
    private static boolean widens(Class<?> from, Class<?> to) {
        if (from == to) return true;
        if (from == byte.class && to == short.class) return true;
        if (from == byte.class || from == short.class || from == char.class) return to == int.class || to == long.class || to == float.class || to == double.class;
        if (from == int.class) return to == long.class || to == float.class || to == double.class;
        if (from == long.class) return to == float.class || to == double.class;
        return from == float.class && to == double.class;
    }
    private static int phase(Class<?>[] params, Object[] args) {
        if (params.length != args.length) return -1;
        int phase = 0;
        for (int i = 0; i < params.length; i++) {
            Class<?> p = params[i]; Object value = args[i];
            if (value == null) { if (p.isPrimitive()) return -1; }
            else if (p.isPrimitive()) {
                Class<?> from = primitive(value.getClass());
                if (from == null || !widens(from, p)) return -1;
                phase = 1;
            } else if (!p.isInstance(value)) return -1;
        }
        return phase;
    }
    private static boolean moreSpecific(Class<?>[] a, Class<?>[] b) {
        boolean strict = false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] == b[i]) continue;
            if (a[i].isPrimitive() != b[i].isPrimitive()) return false;
            if (a[i].isPrimitive() ? !widens(a[i], b[i]) : !b[i].isAssignableFrom(a[i])) return false;
            strict = true;
        }
        return strict;
    }
    private static <T extends Executable> T choose(List<T> all, Object[] args, String label) {
        List<T> applicable = new ArrayList<>(); int bestPhase = 2;
        for (T candidate : all) {
            int p = phase(candidate.getParameterTypes(), args);
            if (p < 0 || p > bestPhase) continue;
            if (p < bestPhase) { applicable.clear(); bestPhase = p; }
            applicable.add(candidate);
        }
        T winner = null;
        for (T candidate : applicable) {
            boolean dominated = false;
            for (T other : applicable) if (moreSpecific(other.getParameterTypes(), candidate.getParameterTypes())) { dominated = true; break; }
            if (dominated) continue;
            if (winner != null) throw new IllegalArgumentException("Ambiguous overload: " + label + Arrays.toString(types(args)));
            winner = candidate;
        }
        if (winner == null) throw new IllegalArgumentException("Member not found: " + label + Arrays.toString(types(args)));
        return winner;
    }
}
