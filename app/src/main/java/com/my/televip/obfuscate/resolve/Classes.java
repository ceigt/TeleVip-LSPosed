package com.my.televip.obfuscate.resolve;

import com.my.televip.obfuscate.dex.DexClass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Building blocks for class fingerprints: where candidates come from, and facts they must satisfy.
 *
 * <p>The strongest sources are anchors in code the build keeps - "the type of
 * MessagesController.dialogMessage", "what LaunchActivity#onCreate constructs" - because they
 * follow the real code instead of guessing at its shape. Shape-only matching is the fallback.</p>
 */
public final class Classes {

    private Classes() {
    }

    // ---------------------------------------------------------------- sources

    /** The declared type of a field reached by a name the build keeps. */
    public static Symbol.ClassSource fieldTypeOf(final String owner, final String fieldName) {
        return r -> {
            DexClass cls = r.cls(owner);
            if (cls == null) return null;
            DexClass.Field f = cls.fieldNamed(fieldName);
            return f == null ? Collections.<DexClass>emptyList() : single(r.index.byDescriptor(f.type()));
        };
    }

    /** The return types of a method reached by a name the build keeps. */
    public static Symbol.ClassSource returnTypeOf(final String owner, final String methodName) {
        return r -> {
            DexClass cls = r.cls(owner);
            if (cls == null) return null;
            List<DexClass> out = new ArrayList<>();
            for (DexClass.Method m : cls.methodsNamed(methodName)) {
                DexClass c = r.index.byDescriptor(m.returnType());
                if (c != null) out.add(c);
            }
            return out;
        };
    }

    /** The type of one parameter of the methods reached by a name the build keeps. */
    public static Symbol.ClassSource paramTypeOf(final String owner, final String methodName, final int index) {
        return r -> {
            DexClass cls = r.cls(owner);
            if (cls == null) return null;
            // A method symbol id ("Owner#key") names the method by what it resolved to.
            List<DexClass.Method> methods;
            if (methodName.contains("#")) {
                DexClass.Method m = r.methods.get(methodName);
                if (m == null) return r.isResolvedOrPending(methodName) ? null : java.util.Collections.<DexClass>emptyList();
                methods = java.util.Collections.singletonList(m);
            } else {
                methods = cls.methodsNamed(methodName);
            }
            java.util.Set<DexClass> out = new java.util.LinkedHashSet<>();
            for (DexClass.Method m : methods) {
                String[] params = m.parameterTypes();
                if (index >= params.length) continue;
                DexClass c = r.index.byDescriptor(params[index]);
                if (c != null) out.add(c);
            }
            return out;
        };
    }

    /**
     * Parameter types of methods of {@code owner} whose other parameters are the given source
     * types; {@code null} marks the unknown position. Lets a kept or already-resolved owner name
     * a renamed type through a method only it has, e.g. {@code onClick(UItem, View, int, ...)}.
     */
    public static Symbol.ClassSource paramTypeWhere(final String owner, final String returnType,
                                                    final String... params) {
        return r -> {
            DexClass cls = r.cls(owner);
            if (cls == null) return null;
            String ret = r.descriptor(returnType);
            if (ret == null) return null;
            int unknown = -1;
            String[] want = new String[params.length];
            for (int i = 0; i < params.length; i++) {
                if (params[i] == null) {
                    unknown = i;
                    continue;
                }
                want[i] = r.descriptor(params[i]);
                if (want[i] == null) return null;
            }
            List<DexClass> out = new ArrayList<>();
            for (DexClass.Method m : cls.methods) {
                String[] actual = m.parameterTypes();
                if (actual.length != want.length || !m.returnType().equals(ret)) continue;
                boolean fits = true;
                for (int i = 0; i < want.length && fits; i++) {
                    if (i != unknown && !want[i].equals(actual[i])) fits = false;
                }
                if (!fits || unknown < 0) continue;
                DexClass c = r.index.byDescriptor(actual[unknown]);
                if (c != null) out.add(c);
            }
            return out;
        };
    }

    /** Direct subclasses of a class. */
    public static Symbol.ClassSource subclassesOf(final String superclass) {
        return r -> {
            String desc = r.descriptor(superclass);
            return desc == null ? null : r.index.subclassesOf(desc);
        };
    }

    /** Types instantiated (new-instance) by the method symbol resolved elsewhere. */
    public static Symbol.ClassSource instantiatedBySymbol(final String methodSymbolId) {
        return r -> {
            DexClass.Method m = r.methods.get(methodSymbolId);
            if (m == null) return null;
            List<DexClass> out = new ArrayList<>();
            for (String t : Body.Refs.of(r, m).newInstances) {
                DexClass c = r.index.byDescriptor(t);
                if (c != null) out.add(c);
            }
            return out;
        };
    }

    /**
     * Classes that declare a method with these parameters and return type (null = any return).
     * Method tables only, so it is cheap even across the whole APK; facts narrow it afterwards.
     */
    public static Symbol.ClassSource declaringMethod(final String returnType, final String... params) {
        return r -> {
            String ret = returnType == null ? null : r.descriptor(returnType);
            if (returnType != null && ret == null) return null;
            String[] descs = new String[params.length];
            for (int i = 0; i < params.length; i++) {
                descs[i] = r.descriptor(params[i]);
                if (descs[i] == null) return null;
            }
            return r.index.classesDeclaring(ret, descs);
        };
    }

    /** Every app-class parameter type of every method a resolved class declares. */
    public static Symbol.ClassSource paramTypesOf(final String owner) {
        return r -> {
            DexClass cls = r.cls(owner);
            if (cls == null) return null;
            java.util.Set<DexClass> out = new java.util.LinkedHashSet<>();
            for (DexClass.Method m : cls.methods) {
                for (String p : m.parameterTypes()) {
                    DexClass c = r.index.byDescriptor(p);
                    if (c != null) out.add(c);
                }
            }
            return out;
        };
    }

    /** Return types of methods of a resolved class taking exactly these parameters. */
    public static Symbol.ClassSource returnTypeWhere(final String owner, final String... params) {
        return r -> {
            DexClass cls = r.cls(owner);
            if (cls == null) return null;
            String[] descs = new String[params.length];
            for (int i = 0; i < params.length; i++) {
                descs[i] = r.descriptor(params[i]);
                if (descs[i] == null) return null;
            }
            List<DexClass> out = new ArrayList<>();
            for (DexClass.Method m : cls.methods) {
                if (!java.util.Arrays.equals(m.parameterTypes(), descs)) continue;
                DexClass c = r.index.byDescriptor(m.returnType());
                if (c != null) out.add(c);
            }
            return out;
        };
    }

    /** Classes declaring at least {@code min} static fields of a platform or primitive type. */
    public static Symbol.ClassSource withStaticFields(final String type, final int min) {
        return r -> {
            String desc = r.descriptor(type);
            List<DexClass> out = new ArrayList<>();
            for (String d : r.index.descriptors()) {
                DexClass c = r.index.byDescriptor(d);
                int n = 0;
                for (DexClass.Field f : c.fields) if (f.isStatic() && f.type().equals(desc)) n++;
                if (n >= min) out.add(c);
            }
            return out;
        };
    }

    /** The return types of every method a resolved class declares. */
    public static Symbol.ClassSource returnTypesOf(final String owner) {
        return r -> {
            DexClass cls = r.cls(owner);
            if (cls == null) return null;
            List<DexClass> out = new ArrayList<>();
            for (DexClass.Method m : cls.methods) {
                DexClass c = r.index.byDescriptor(m.returnType());
                if (c != null && !out.contains(c)) out.add(c);
            }
            return out;
        };
    }

    /** The types of every field a resolved class declares. */
    public static Symbol.ClassSource fieldTypesOf(final String owner) {
        return r -> {
            DexClass cls = r.cls(owner);
            if (cls == null) return null;
            List<DexClass> out = new ArrayList<>();
            for (DexClass.Field f : cls.fields) {
                DexClass c = r.index.byDescriptor(f.type());
                if (c != null) out.add(c);
            }
            return out;
        };
    }

    /** The direct superclass of a resolved class. */
    public static Symbol.ClassSource superclassOf(final String cls) {
        return r -> {
            DexClass c = r.cls(cls);
            return c == null ? null : single(r.index.byDescriptor(c.superclass));
        };
    }

    /**
     * Classes with methods that load every one of these strings. String literals survive R8
     * untouched, so a few that only one class uses make the most durable fingerprint there is.
     * The strings are collected from all symbols up front and found in a single bytecode scan.
     */
    public static Symbol.ClassSource declaringStrings(final String... strings) {
        return new StringAnchored(strings);
    }

    static final class StringAnchored implements Symbol.ClassSource {
        final String[] strings;

        StringAnchored(String[] strings) {
            this.strings = strings;
        }

        @Override
        public Collection<DexClass> candidates(Resolver r) {
            java.util.Set<DexClass> result = null;
            for (String s : strings) {
                java.util.Set<DexClass> owners = new java.util.LinkedHashSet<>();
                for (DexClass.Method m : r.anchors().string(s)) owners.add(m.owner);
                if (result == null) result = owners;
                else result.retainAll(owners);
            }
            return result == null ? Collections.<DexClass>emptyList() : result;
        }
    }

    /**
     * The TL class with this constructor id: the owner of the instance {@code void(x)} method
     * that writes it, which is serializeToStream. The abstract parent only compares against the
     * id in its static TLdeserialize, so this needs nothing else resolved first.
     */
    public static Symbol.ClassSource serializingConstant(final long value) {
        return new ConstantAnchored(value);
    }

    static final class ConstantAnchored implements Symbol.ClassSource {
        final long value;

        ConstantAnchored(long value) {
            this.value = value;
        }

        @Override
        public Collection<DexClass> candidates(Resolver r) {
            java.util.Set<DexClass> owners = new java.util.LinkedHashSet<>();
            for (DexClass.Method m : r.anchors().constant(value)) {
                if (!m.isStatic() && m.returnType().equals("V") && m.parameterTypes().length == 1) owners.add(m.owner);
            }
            return owners;
        }
    }

    /**
     * Classes declaring a method of this signature whose body satisfies {@code fact} - for a
     * class known only by one distinctive method. Walks the method tables for the signature
     * first, so only bodies of that exact shape are scanned.
     */
    public static Symbol.ClassSource declaringMethodWhere(final Body fact, final String returnType,
                                                          final String... params) {
        return r -> {
            String ret = r.descriptor(returnType);
            if (ret == null) return null;
            String[] want = new String[params.length];
            for (int i = 0; i < params.length; i++) {
                want[i] = r.descriptor(params[i]);
                if (want[i] == null) return null;
            }
            java.util.Set<DexClass> out = new java.util.LinkedHashSet<>();
            for (DexClass c : r.index.classesDeclaring(ret, want)) {
                for (DexClass.Method m : c.methods) {
                    if (!m.hasCode() || !m.returnType().equals(ret)
                            || !java.util.Arrays.equals(m.parameterTypes(), want)) continue;
                    Boolean ok = fact.test(r, m);
                    if (ok == null) return null;
                    if (ok) out.add(c);
                }
            }
            return out;
        };
    }

    // ------------------------------------------------------------------ facts

    public static Symbol.ClassFact extendsType(final String superclass) {
        return (r, c) -> {
            String desc = r.descriptor(superclass);
            return desc == null ? null : desc.equals(c.superclass);
        };
    }

    /** The negation of a class fact. */
    public static Symbol.ClassFact isNot(final Symbol.ClassFact fact) {
        return (r, c) -> {
            Boolean b = fact.test(r, c);
            return b == null ? null : !b;
        };
    }

    /** Has {@code ancestor} somewhere up its superclass chain (as far as the APK defines it). */
    public static Symbol.ClassFact inherits(final String ancestor) {
        return (r, c) -> {
            String desc = r.descriptor(ancestor);
            return desc == null ? null : r.index.extendsClass(c.descriptor, desc);
        };
    }

    public static Symbol.ClassFact implementsType(final String iface) {
        return (r, c) -> {
            String desc = r.descriptor(iface);
            if (desc == null) return null;
            for (String i : c.interfaces) if (i.equals(desc)) return true;
            return false;
        };
    }

    public static Symbol.ClassFact isInterface() {
        return (r, c) -> c.isInterface();
    }

    /** Declares a method with this signature; {@code isStatic} null means either. */
    public static Symbol.ClassFact hasMethod(final Boolean isStatic, final String returnType,
                                             final String... params) {
        return (r, c) -> {
            String proto = r.proto(returnType, params);
            if (proto == null) return null;
            for (DexClass.Method m : c.methods) {
                if (!m.isConstructor() && proto.equals(Resolver.protoOf(m))
                        && (isStatic == null || isStatic == m.isStatic())) return true;
            }
            return false;
        };
    }

    /** Declares a constructor taking these parameters. */
    /** Has a constructor of this arity whose first parameter is {@code firstParam}. */
    public static Symbol.ClassFact hasConstructorFrom(final String firstParam, final int arity) {
        return (r, c) -> {
            String first = r.descriptor(firstParam);
            if (first == null) return null;
            for (DexClass.Method m : c.methods) {
                String[] p = m.parameterTypes();
                if (m.isConstructor() && !m.isStatic() && p.length == arity && p[0].equals(first)) return true;
            }
            return false;
        };
    }

    public static Symbol.ClassFact hasConstructor(final String... params) {
        return (r, c) -> {
            String proto = r.proto("void", params);
            if (proto == null) return null;
            for (DexClass.Method m : c.methods) {
                if (m.isConstructor() && !m.isStatic() && proto.equals(Resolver.protoOf(m))) return true;
            }
            return false;
        };
    }

    /** Declares a field of this type; {@code isStatic} null means either. */
    public static Symbol.ClassFact hasField(final Boolean isStatic, final String type) {
        return (r, c) -> {
            String desc = r.descriptor(type);
            if (desc == null) return null;
            for (DexClass.Field f : c.fields) {
                if (desc.equals(f.type()) && (isStatic == null || isStatic == f.isStatic())) return true;
            }
            return false;
        };
    }

    /** No more than {@code max} methods besides constructors. */
    public static Symbol.ClassFact methodCountAtMost(final int max) {
        return (r, c) -> {
            int n = 0;
            for (DexClass.Method m : c.methods) if (!m.isConstructor()) n++;
            return n <= max;
        };
    }

    /** At least {@code min} static methods that return the class itself - a factory-style API. */
    public static Symbol.ClassFact staticFactories(final int min) {
        return (r, c) -> {
            int n = 0;
            for (DexClass.Method m : c.methods) {
                if (m.isStatic() && c.descriptor.equals(m.returnType())) n++;
            }
            return n >= min;
        };
    }

    /** Some method of the class loads this string. */
    public static Symbol.ClassFact loadsString(final String value) {
        return (r, c) -> {
            for (DexClass.Method m : c.methods) {
                if (Body.Refs.of(r, m).strings.contains(value)) return true;
            }
            return false;
        };
    }

    /** Some method of the class that has code satisfies this body fact. */
    public static Symbol.ClassFact someMethod(final Body fact) {
        return (r, c) -> {
            boolean undecided = false;
            for (DexClass.Method m : c.methods) {
                if (!m.hasCode()) continue;
                Boolean b = fact.test(r, m);
                if (b == null) undecided = true;
                else if (b) return true;
            }
            return undecided ? null : false;
        };
    }

    private static List<DexClass> single(DexClass c) {
        return c == null ? Collections.<DexClass>emptyList() : Collections.singletonList(c);
    }

    static Collection<DexClass> none() {
        return Collections.emptyList();
    }
}
