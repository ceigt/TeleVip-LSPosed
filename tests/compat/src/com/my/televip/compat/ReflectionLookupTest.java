package com.my.televip.compat;

import java.io.Serializable;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

public final class ReflectionLookupTest {
    public static class LoaderFixture { public int count; public void run(String value) {} }
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("Reflection check " + checks); }
    interface Default { default String inherited() { return "default"; } }
    static class Parent { private int secret; private void hook(int value) {} public Number value() { return 1; } }
    static class Target extends Parent implements Default {
        public String ref(Object value) { return "object"; }
        public String ref(CharSequence value) { return "chars"; }
        public String ref(String value) { return "string"; }
        public String ambiguous(Serializable value) { return "serial"; }
        public String ambiguous(CharSequence value) { return "chars"; }
        public String cross(String a, Object b) { return "a"; }
        public String cross(Object a, String b) { return "b"; }
        public String number(int value) { return "int"; }
        public String number(long value) { return "long"; }
        public String wide(long value) { return "long"; }
        public String wrapper(Integer value) { return "wrapper"; }
        public String wrapper(int value) { return "primitive"; }
        public String bool(boolean value) { return "boolean"; }
        public static String mode(String value) { return "static"; }
        public String mode(Object value) { return "instance"; }
        @Override public Integer value() { return 2; }
    }
    static class Built {
        Built(Object value) {}
        Built(String value) {}
    }
    static class AmbiguousBuilt {
        AmbiguousBuilt(Serializable value) {}
        AmbiguousBuilt(CharSequence value) {}
    }
    interface Action { void run() throws Exception; }
    static void rejects(Action action) throws Exception {
        try { action.run(); } catch (IllegalArgumentException | NoSuchMethodException expected) { check(true); return; }
        throw new AssertionError("Unsafe lookup accepted");
    }
    static Method method(String name, Object... args) { return ReflectionLookup.method(Target.class, name, args, false); }
    public static void main(String[] args) throws Exception {
        Target target = new Target();
        check(method("ref", "x").invoke(target, "x").equals("string"));
        check(method("ref", new StringBuilder()).getParameterTypes()[0] == CharSequence.class);
        check(method("ref", (Object)null).getParameterTypes()[0] == String.class);
        rejects(() -> method("ambiguous", "x"));
        rejects(() -> method("ambiguous", (Object)null));
        rejects(() -> method("cross", "a", "b"));
        check(method("number", 1).getParameterTypes()[0] == int.class);
        check(method("number", (byte)1).getParameterTypes()[0] == int.class);
        check(method("wide", 1).invoke(target, 1).equals("long"));
        rejects(() -> method("number", 1.5));
        rejects(() -> method("bool", 1));
        rejects(() -> method("number", (Object)null));
        check(method("wrapper", 1).getParameterTypes()[0] == Integer.class);
        check(method("mode", "x").getParameterTypes()[0] == Object.class);
        check(Modifier.isStatic(ReflectionLookup.method(Target.class, "mode", new Object[]{"x"}, true).getModifiers()));
        rejects(() -> ReflectionLookup.method(Target.class, "ref", new Object[]{"x"}, true));
        check(method("value").getReturnType() == Integer.class);
        check(method("inherited").invoke(target).equals("default"));
        Field field = ReflectionLookup.field(Target.class, "secret"); field.set(target, 7); check(field.getInt(target) == 7);
        check(field == ReflectionLookup.field(Target.class, "secret"));
        Method hook = ReflectionLookup.exactMethod(Target.class, "hook", int.class);
        check(hook.getDeclaringClass() == Parent.class);
        check(hook == ReflectionLookup.exactMethod(Target.class, "hook", int.class));
        rejects(() -> ReflectionLookup.exactMethod(Target.class, "hook", Integer.class));
        rejects(() -> ReflectionLookup.exactMethod(Target.class, "hook", (Class<?>)null));
        check(ReflectionLookup.constructor(Built.class, new Object[]{null}).getParameterTypes()[0] == String.class);
        rejects(() -> ReflectionLookup.constructor(AmbiguousBuilt.class, new Object[]{"x"}));
        // Concurrent lookup must remain deterministic; classes from separate loaders cannot share a cache entry.
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 100; i++) results.add(pool.submit(() -> method("number", 1).getParameterTypes()[0] == int.class));
            for (Future<Boolean> result : results) check(result.get());
        } finally { pool.shutdownNow(); }
        String resource = "/" + LoaderFixture.class.getName().replace('.', '/') + ".class";
        byte[] bytes;
        try (java.io.InputStream in = LoaderFixture.class.getResourceAsStream(resource); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n); bytes = out.toByteArray();
        }
        class Loader extends ClassLoader { Loader() { super(Target.class.getClassLoader()); } Class<?> copy() { return defineClass(LoaderFixture.class.getName(), bytes, 0, bytes.length); } }
        Class<?> first = new Loader().copy(), second = new Loader().copy();
        check(first != second && first.getName().equals(second.getName()));
        check(ReflectionLookup.field(first, "count").getDeclaringClass() == first);
        check(ReflectionLookup.field(second, "count").getDeclaringClass() == second);
        check(ReflectionLookup.method(first, "run", new Object[]{"x"}, false).getDeclaringClass() == first);
        check(ReflectionLookup.method(second, "run", new Object[]{"x"}, false).getDeclaringClass() == second);
        System.out.println("REFLECTION_TEST_PASS: " + checks + " assertions");
    }
}
