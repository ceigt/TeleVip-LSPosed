package com.my.televip.obfuscate.resolve;

import com.my.televip.obfuscate.dex.CodeScanner;
import com.my.televip.obfuscate.dex.DexClass;
import com.my.televip.obfuscate.dex.DexFile;
import com.my.televip.obfuscate.dex.DexNames;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * One thing a call site needs from the client - a class, a field or a method - described by its
 * original name and, for when the build renamed it, by a fingerprint.
 */
public abstract class Symbol {

    abstract String id();

    abstract Resolver.Attempt attempt(Resolver r);

    abstract void store(Resolver r, Resolver.Attempt attempt, Mapping mapping);

    /** Whether the symbol can be found without its real name. */
    abstract boolean hasFingerprint();

    /** Whether to look the real name up: always, except when testing the fingerprint alone. */
    boolean tryName(Resolver r) {
        return !(r.fingerprintsOnly && hasFingerprint());
    }

    // =================================================================== class

    /** Something true or false about a candidate class. Null means "not decidable yet". */
    public interface ClassFact {
        Boolean test(Resolver r, DexClass c);
    }

    /** Where to look for a renamed class. Null means "depends on something not resolved yet". */
    public interface ClassSource {
        Collection<DexClass> candidates(Resolver r);
    }

    /** A method parameter of any type: one no symbol names (e.g. a renamed helper class). */
    public static final String ANY = "*";

    public static ClassSymbol cls(String originalName) {
        return new ClassSymbol(originalName);
    }

    public static final class ClassSymbol extends Symbol {
        final String original;
        final List<ClassSource> sources = new ArrayList<>();
        final List<ClassFact> facts = new ArrayList<>();

        ClassSymbol(String original) {
            this.original = original;
        }

        /**
         * Where the candidates come from. Several sources are tried in order, and the first to
         * single out one class wins - so a precise anchor can come first and a broader shape match
         * after it, for builds where the anchor is gone.
         */
        public ClassSymbol from(ClassSource... sources) {
            for (ClassSource source : sources) this.sources.add(source);
            return this;
        }

        /** Every fact must hold for a candidate to count. */
        public ClassSymbol where(ClassFact... facts) {
            for (ClassFact f : facts) this.facts.add(f);
            return this;
        }

        @Override
        String id() {
            return original;
        }

        @Override
        boolean hasFingerprint() {
            return !sources.isEmpty();
        }

        @Override
        Resolver.Attempt attempt(Resolver r) {
            DexClass kept = tryName(r) ? r.index.findClass(original) : null;
            if (kept != null) return Resolver.Attempt.of(kept, true);
            Resolver.Attempt ambiguous = null;
            boolean waiting = false;
            for (ClassSource source : sources) {
                Collection<DexClass> candidates = source.candidates(r);
                if (candidates == null) {
                    waiting = true;
                    continue;
                }
                List<DexClass> matching = new ArrayList<>();
                boolean undecided = false;
                for (DexClass c : candidates) {
                    if (c == null) continue;
                    Boolean ok = holds(r, c);
                    if (ok == null) {
                        undecided = true;
                        break;
                    }
                    if (ok) matching.add(c);
                }
                if (undecided) {
                    waiting = true;
                    continue;
                }
                Resolver.Attempt attempt = Resolver.Attempt.single(matching);
                if (attempt.kind == Resolver.Attempt.Kind.FOUND) return attempt;
                if (attempt.kind == Resolver.Attempt.Kind.AMBIGUOUS && ambiguous == null) ambiguous = attempt;
            }
            if (waiting) return Resolver.Attempt.waiting();
            return ambiguous != null ? ambiguous : Resolver.Attempt.notFound();
        }

        private Boolean holds(Resolver r, DexClass c) {
            for (ClassFact f : facts) {
                Boolean b = f.test(r, c);
                if (b == null) return null;
                if (!b) return false;
            }
            return true;
        }

        @Override
        void store(Resolver r, Resolver.Attempt attempt, Mapping mapping) {
            DexClass c = (DexClass) attempt.found;
            r.classes.put(original, c);
            mapping.classes.put(original, c.javaName());
        }
    }

    // ================================================================== method

    public static MethodSymbol method(String owner, String key) {
        return new MethodSymbol(owner, key);
    }

    public static final class MethodSymbol extends Symbol {
        final String owner, key;
        String name;
        String returnType;
        String[] params;
        Boolean isStatic;
        int[] readPositions;
        boolean voidable, narrowedStrings, narrowedReturn, anyOrder, staticized;
        final List<Body> facts = new ArrayList<>();

        MethodSymbol(String owner, String key) {
            this.owner = owner;
            this.key = key;
            this.name = key;
        }

        /** The method's real name, when the table key adds an overload suffix to it. */
        public MethodSymbol named(String realName) {
            this.name = realName;
            return this;
        }

        /** Signature in source types. Original app class names are mapped through the resolution. */
        public MethodSymbol sig(String returnType, String... params) {
            this.returnType = returnType;
            this.params = params;
            return this;
        }

        public MethodSymbol isStatic(boolean value) {
            this.isStatic = value;
            return this;
        }

        /**
         * The source parameter positions the call site actually reads. R8 deletes parameters a
         * method never uses, so the build may have fewer than the source; that is accepted only if
         * each of these positions is still there at the same index - otherwise a hook reading
         * {@code args[i]} would get a different argument. Without this, the signature must be
         * exactly the source one.
         */
        public MethodSymbol reads(int... sourcePositions) {
            this.readPositions = sourcePositions;
            return this;
        }

        /**
         * R8 turns a return type into void when no caller uses the result - typically a builder's
         * {@code return this}. Accept that, for call sites that ignore what the method returns.
         */
        public MethodSymbol voidable() {
            this.voidable = true;
            return this;
        }

        /**
         * R8 narrows a parameter type when every caller passes something more specific, and in
         * practice that means {@code CharSequence} becoming {@code String}. Accept that, for call
         * sites that only ever pass strings.
         */
        public MethodSymbol narrowedStrings() {
            this.narrowedStrings = true;
            return this;
        }

        /**
         * R8 narrows a return type to the one class a method actually returns - a Runnable becomes
         * the lambda class implementing it. Accept a return type that is a subtype of the source
         * one, for call sites that do not depend on the declared type.
         */
        public MethodSymbol narrowedReturn() {
            this.narrowedReturn = true;
            return this;
        }

        /**
         * R8 can reorder a private method's parameters. Accept the source parameters in any
         * order; the build's real order is published for the call site, which must then look its
         * parameter types up (AutomationResolver.resolveObject) rather than hard-code them.
         */
        public MethodSymbol anyOrder() {
            this.anyOrder = true;
            return this;
        }

        /**
         * R8 can turn a private instance method into a static one that takes the instance as its
         * first parameter. Accept that shape too; the call site gets the real parameter list.
         */
        public MethodSymbol staticized() {
            this.staticized = true;
            return this;
        }

        public MethodSymbol where(Body... facts) {
            for (Body f : facts) this.facts.add(f);
            return this;
        }

        @Override
        String id() {
            return owner + "#" + key;
        }

        @Override
        boolean hasFingerprint() {
            return returnType != null && !name.startsWith("<");   // constructors are never renamed
        }

        @Override
        Resolver.Attempt attempt(Resolver r) {
            DexClass cls = r.cls(r.fullName(owner));
            if (cls == null) {
                // The owner itself is unresolved - maybe for good, maybe not yet.
                return r.pendingOrResolvable(r.fullName(owner))
                        ? Resolver.Attempt.waiting() : Resolver.Attempt.notFound();
            }
            String[] want = null;
            String ret = null;
            if (returnType != null) {
                ret = r.descriptor(returnType);
                want = new String[params.length];
                for (int i = 0; i < params.length; i++) {
                    want[i] = ANY.equals(params[i]) ? ANY : r.descriptor(params[i]);
                    if (want[i] == null) return Resolver.Attempt.waiting();
                }
                if (ret == null) return Resolver.Attempt.waiting();
            }

            // Kept name: conclusive for a descriptive name, but a short one could equally be
            // something R8 generated, so for those the signature has to agree as well.
            List<DexClass.Method> named = tryName(r) ? cls.methodsNamed(name)
                    : java.util.Collections.<DexClass.Method>emptyList();
            if (!named.isEmpty()) {
                if (want == null && !facts.isEmpty()) {
                    // Overloads of a kept name (e.g. constructors): the facts pick one.
                    List<DexClass.Method> fitting = new ArrayList<>();
                    for (DexClass.Method m : named) {
                        Boolean ok = holds(r, m);
                        if (ok == null) return Resolver.Attempt.waiting();
                        if (ok) fitting.add(m);
                    }
                    if (fitting.size() == 1) return Resolver.Attempt.of(fitting.get(0), true);
                    if (name.length() > 3) return Resolver.Attempt.of(named.get(0), true);
                } else if (want == null) {
                    if (name.length() > 3) return Resolver.Attempt.of(named.get(0), true);
                } else {
                    for (DexClass.Method m : named) {
                        if (signatureFits(r, m, ret, want) && staticMatches(m)) {
                            return Resolver.Attempt.of(m, true);
                        }
                    }
                }
            }
            if (want == null) return Resolver.Attempt.notFound();

            List<DexClass.Method> matching = new ArrayList<>();
            for (DexClass.Method m : cls.methods) {
                if (m.isConstructor() || !staticMatches(m) || !signatureFits(r, m, ret, want)) continue;
                Boolean ok = holds(r, m);
                if (ok == null) return Resolver.Attempt.waiting();
                if (ok) matching.add(m);
            }
            return Resolver.Attempt.single(matching);
        }

        /**
         * Exact signature, or - when the call site declared what it reads - the source signature
         * with unused parameters deleted, as long as every read position keeps its index.
         */
        boolean signatureFits(Resolver r, DexClass.Method m, String ret, String[] want) {
            if (!m.returnType().equals(ret) && !(voidable && m.returnType().equals("V"))
                    && !(narrowedReturn && isSubtype(r, m.returnType(), ret))) return false;
            String[] actual = m.parameterTypes();
            // The instance becomes the first parameter; R8 may delete unused ones on top of that.
            if (staticized && m.isStatic() && actual.length > 0 && actual[0].equals(m.owner.descriptor)
                    && (actual.length == want.length + 1 || readPositions != null)) {
                actual = java.util.Arrays.copyOfRange(actual, 1, actual.length);
            }
            if (actual.length == want.length) {
                boolean inOrder = true;
                for (int i = 0; i < want.length && inOrder; i++) {
                    if (!paramFits(want[i], actual[i])) inOrder = false;
                }
                return inOrder || (anyOrder && isPermutation(want, actual));
            }
            if (readPositions == null || actual.length > want.length) return false;
            // Greedy subsequence alignment: actual[j] is source parameter kept[j].
            int[] kept = new int[actual.length];
            int j = 0;
            for (int i = 0; i < want.length && j < actual.length; i++) {
                if (paramFits(want[i], actual[j])) kept[j++] = i;
            }
            if (j != actual.length) return false;
            for (int p : readPositions) {
                if (p >= actual.length || kept[p] != p) return false;
            }
            return true;
        }

        private boolean isPermutation(String[] want, String[] actual) {
            boolean[] used = new boolean[actual.length];
            for (String w : want) {
                boolean matched = false;
                for (int j = 0; j < actual.length && !matched; j++) {
                    if (!used[j] && paramFits(w, actual[j])) used[j] = matched = true;
                }
                if (!matched) return false;
            }
            return true;
        }

        private static boolean isSubtype(Resolver r, String actual, String declared) {
            DexClass c = r.index.byDescriptor(actual);
            if (c == null) return false;
            if (r.index.extendsClass(actual, declared)) return true;
            for (String i : c.interfaces) if (i.equals(declared)) return true;
            return false;
        }

        private boolean paramFits(String declared, String actual) {
            return declared.equals(ANY) || declared.equals(actual) || (narrowedStrings
                    && declared.equals("Ljava/lang/CharSequence;") && actual.equals("Ljava/lang/String;"));
        }

        private boolean staticMatches(DexClass.Method m) {
            return isStatic == null || isStatic == m.isStatic();
        }

        private Boolean holds(Resolver r, DexClass.Method m) {
            for (Body f : facts) {
                Boolean b = f.test(r, m);
                if (b == null) return null;
                if (!b) return false;
            }
            return true;
        }

        private boolean reordered(Resolver r, DexClass.Method m) {
            if (!anyOrder) return false;
            String[] actual = m.parameterTypes();
            for (int i = 0; i < actual.length; i++) {
                if (!paramFits(ANY.equals(params[i]) ? ANY : r.descriptor(params[i]), actual[i])) return true;
            }
            return false;
        }

        @Override
        void store(Resolver r, Resolver.Attempt attempt, Mapping mapping) {
            DexClass.Method m = (DexClass.Method) attempt.found;
            r.methods.put(id(), m);
            mapping.methods.put(Mapping.memberKey(owner, key), m.name());
            // Deleted, reordered or prepended (staticized) parameters: the call site asks for the
            // source list, so hand it the real one.
            if (params != null && (m.parameterTypes().length != params.length || reordered(r, m)
                    || (staticized && m.isStatic()))) {
                String[] actual = m.parameterTypes();
                String[] names = new String[actual.length];
                for (int i = 0; i < actual.length; i++) names[i] = javaName(actual[i]);
                mapping.putParameters(name, names);
            }
        }
    }

    // =================================================================== field

    public static FieldSymbol field(String owner, String name) {
        return new FieldSymbol(owner, name);
    }

    public static final class FieldSymbol extends Symbol {
        final String owner, name;
        String type;
        Boolean isStatic;
        boolean uniqueOfType;
        String writtenBy;
        int writeOrdinal;
        boolean ordinalOfReads;
        String handedIn, handedTo;
        boolean narrowed;
        String alternativeType;
        String keyHost, key;
        String flagHost;
        int flagBit;
        String[] lastReaders;
        String postMethod;
        boolean intLike;

        FieldSymbol(String owner, String name) {
            this.owner = owner;
            this.name = name;
        }

        @Override
        boolean hasFingerprint() {
            return keyHost != null || flagHost != null
                    || type != null && (uniqueOfType || writtenBy != null || lastReaders != null || handedIn != null);
        }

        /**
         * Renamed boolean flag: the field a TL serializer folds into its flags word with bit
         * {@code bit} - {@code flags = setFlag(flags, 1 << bit, field)}, called or inlined. Each
         * flag sits between two writes of the flags word, whatever order the compiler loaded
         * the constant and the field in.
         */
        /**
         * Where R8 turned a static field into a constant: the static field of this type starting
         * with {@code value}. Consulted when nothing else locates the field.
         */
        /**
         * An int field R8 may have narrowed to byte, short or char where its values fit: any of
         * those counts as its type, so write and read orders stay the same either way.
         */
        public FieldSymbol intLike() {
            this.intLike = true;
            return this;
        }

        /**
         * The last field of this type the first resolved {@code methodSymbolIds} reads - e.g. the
         * event a method posts after any others.
         */
        public FieldSymbol lastReadBy(String... methodSymbolIds) {
            this.lastReaders = methodSymbolIds;
            return this;
        }

        /**
         * For lastReadBy, where R8 has folded the read into a constant: the last constant those
         * methods pass to {@code postMethodSymbolId} as its first argument, and the static field
         * of this class that holds that value - as its initial value, or as <clinit> stores it.
         */
        public FieldSymbol postedThrough(String postMethodSymbolId) {
            this.postMethod = postMethodSymbolId;
            return this;
        }

        public FieldSymbol flagOf(String serializerSymbolId, int bit) {
            this.flagHost = serializerSymbolId;
            this.flagBit = bit;
            return this;
        }

        public FieldSymbol type(String sourceType) {
            this.type = sourceType;
            return this;
        }

        public FieldSymbol isStatic(boolean value) {
            this.isStatic = value;
            return this;
        }

        /** Renamed field: the class declares exactly one field of this type, so it is that one. */
        public FieldSymbol onlyOneOfType() {
            this.uniqueOfType = true;
            return this;
        }

        /**
         * Renamed field: the {@code ordinal}-th distinct field of this type that the given
         * (resolved) method writes, in the order it writes them. For initialisers that assign a
         * class's fields one after another, which R8 does not reorder.
         */
        public FieldSymbol writtenBy(String methodSymbolId, int ordinal) {
            this.writtenBy = methodSymbolId;
            this.writeOrdinal = ordinal;
            return this;
        }

        /**
         * Renamed field: the {@code ordinal}-th distinct field of this type that the given
         * (resolved) method reads, in order - e.g. the TextView a setter sets text on first.
         */
        public FieldSymbol readBy(String methodSymbolId, int ordinal) {
            this.writtenBy = methodSymbolId;
            this.writeOrdinal = ordinal;
            this.ordinalOfReads = true;
            return this;
        }

        /**
         * Renamed field: the field (of this type or a subclass of it) that {@code hostMethod} reads
         * last before calling a method named {@code calledName} - e.g. the view an Activity's
         * onCreate hands to setContentView. R8 narrows such fields to their anonymous subclass and
         * adds look-alikes of the same base type, so neither the type nor uniqueness pins them.
         * The host and the called method are each a name the build keeps or a method symbol id
         * ({@code "Owner#key"}), matched by what it resolved to.
         */
        public FieldSymbol handedTo(String hostMethod, String calledName) {
            this.handedIn = hostMethod;
            this.handedTo = calledName;
            return this;
        }

        /**
         * Renamed field: the first field of the class that the given (resolved) method writes after
         * loading the string {@code key} - a preference or queue name that travels with the field,
         * as in {@code storiesPosting = preferences.getString("storiesPosting", ...)}.
         */
        public FieldSymbol keyedBy(String methodSymbolId, String key) {
            this.keyHost = methodSymbolId;
            this.key = key;
            return this;
        }

        /** The type some builds declare the field with instead (for writtenBy / readBy). */
        public FieldSymbol orType(String sourceType) {
            this.alternativeType = sourceType;
            return this;
        }

        /**
         * R8 narrows a field's declared type to the one class ever stored in it - an anonymous
         * {@code new FrameLayout(ctx) { ... }} field ends up typed as that subclass. Accept any
         * subclass of the source type (for writtenBy / readBy).
         */
        public FieldSymbol narrowed() {
            this.narrowed = true;
            return this;
        }


        @Override
        String id() {
            return owner + "." + name;
        }

        @Override
        Resolver.Attempt attempt(Resolver r) {
            DexClass cls = r.cls(r.fullName(owner));
            if (cls == null) {
                return r.pendingOrResolvable(r.fullName(owner))
                        ? Resolver.Attempt.waiting() : Resolver.Attempt.notFound();
            }
            String typeDesc = type == null ? null : r.descriptor(type);
            if (type != null && typeDesc == null) return Resolver.Attempt.waiting();

            DexClass.Field kept = tryName(r) ? cls.fieldNamed(name) : null;
            if (kept != null && (name.length() > 3 || typeDesc == null || typeDesc.equals(kept.type()))) {
                return Resolver.Attempt.of(kept, true);
            }
            if (typeDesc != null && handedIn != null) {
                // A symbol id ("Owner#key") names the called method by what it resolved to.
                final DexClass.Method target = handedTo.contains("#") ? r.methods.get(handedTo) : null;
                if (handedTo.contains("#") && target == null) {
                    return r.isResolvedOrPending(handedTo) ? Resolver.Attempt.waiting() : Resolver.Attempt.notFound();
                }
                final List<String> handed = new ArrayList<>();
                final String owner = cls.descriptor;
                final String base = typeDesc;
                final Resolver resolver = r;
                // The host is a method symbol id ("Owner#key") or a name the build keeps.
                List<DexClass.Method> hosts;
                if (handedIn.contains("#")) {
                    DexClass.Method h = r.methods.get(handedIn);
                    if (h == null) return r.isResolvedOrPending(handedIn) ? Resolver.Attempt.waiting() : Resolver.Attempt.notFound();
                    hosts = java.util.Collections.singletonList(h);
                } else {
                    hosts = cls.methodsNamed(handedIn);
                }
                for (DexClass.Method host : hosts) {
                    if (!host.hasCode()) continue;
                    final DexFile dex = host.owner.dex;
                    final String[] lastRead = new String[1];
                    host.scan(new CodeScanner.Visitor() {
                        @Override
                        public void field(int opcode, int i, boolean write) {
                            if (!write && dex.fieldClass(i).equals(owner)
                                    && resolver.index.extendsClass(dex.fieldType(i), base)) {
                                lastRead[0] = dex.fieldName(i);
                            }
                        }

                        @Override
                        public void invoke(int opcode, int i) {
                            boolean called = target == null ? dex.methodName(i).equals(handedTo)
                                    : dex.methodName(i).equals(target.name())
                                    && dex.methodClass(i).equals(target.owner.descriptor);
                            if (called && lastRead[0] != null
                                    && !handed.contains(lastRead[0])) handed.add(lastRead[0]);
                        }
                    });
                }
                List<DexClass.Field> found = new ArrayList<>();
                for (String n : handed) {
                    DexClass.Field f = cls.fieldNamed(n);
                    if (f != null) found.add(f);
                }
                return Resolver.Attempt.single(found);
            }
            if (typeDesc != null && uniqueOfType) {
                List<DexClass.Field> ofType = new ArrayList<>();
                for (DexClass.Field f : cls.fields) {
                    if (typeDesc.equals(f.type()) && (isStatic == null || isStatic == f.isStatic())) ofType.add(f);
                }
                return Resolver.Attempt.single(ofType);
            }
            if (keyHost != null) {
                DexClass.Method host = r.methods.get(keyHost);
                if (host == null) return r.isResolvedOrPending(keyHost)
                        ? Resolver.Attempt.waiting() : Resolver.Attempt.notFound();
                if (!host.hasCode()) return Resolver.Attempt.notFound();
                final DexFile dex = host.owner.dex;
                final String owner = cls.descriptor;
                final String wanted = key;
                final String[] found = new String[1];
                host.scan(new CodeScanner.Visitor() {
                    boolean armed;

                    @Override
                    public void string(int i) {
                        if (wanted.equals(dex.string(i))) armed = true;
                    }

                    @Override
                    public void field(int opcode, int i, boolean write) {
                        if (!armed || !write) return;
                        armed = false;   // only the store the key leads to
                        if (found[0] == null && dex.fieldClass(i).equals(owner)) found[0] = dex.fieldName(i);
                    }
                });
                DexClass.Field declared = found[0] == null ? null : cls.fieldNamed(found[0]);
                return declared == null ? Resolver.Attempt.notFound() : Resolver.Attempt.of(declared, false);
            }
            if (flagHost != null) {
                DexClass.Method host = r.methods.get(flagHost);
                if (host == null) return r.isResolvedOrPending(flagHost)
                        ? Resolver.Attempt.waiting() : Resolver.Attempt.notFound();
                if (!host.hasCode()) return Resolver.Attempt.notFound();
                final DexFile dex = host.owner.dex;
                final String ownerDesc = cls.descriptor;
                final long bit = 1L << flagBit, mask = (int) ~(1L << flagBit);
                final java.util.Set<String> found = new java.util.LinkedHashSet<>();
                host.scan(new CodeScanner.Visitor() {
                    final List<String> reads = new ArrayList<>();
                    boolean hasBit;

                    @Override
                    public void constant(long value) {
                        if (value == bit || value == (int) bit || value == mask) hasBit = true;
                    }

                    @Override
                    public void field(int opcode, int i, boolean write) {
                        if (!dex.fieldClass(i).equals(ownerDesc)) return;
                        String t = dex.fieldType(i);
                        if (!write && t.equals("Z")) {
                            reads.add(dex.fieldName(i));
                        } else if (write && t.equals("I")) {
                            // The flags word was stored: one setFlag step is complete.
                            if (hasBit && reads.size() == 1) found.add(reads.get(0));
                            reads.clear();
                            hasBit = false;
                        }
                    }
                });
                List<DexClass.Field> fields = new ArrayList<>();
                for (String n : found) {
                    DexClass.Field f = cls.fieldNamed(n);
                    if (f != null) fields.add(f);
                }
                return Resolver.Attempt.single(fields);
            }
            if (typeDesc != null && writtenBy != null) {
                DexClass.Method writer = r.methods.get(writtenBy);
                if (writer == null && r.isResolvedOrPending(writtenBy)) return Resolver.Attempt.waiting();
                if (writer == null) return Resolver.Attempt.notFound();
                String altDesc = alternativeType == null ? null : r.descriptor(alternativeType);
                List<String> order = new ArrayList<>();
                for (Body.Refs.FieldRef f : Body.Refs.of(r, writer).fields) {
                    boolean typeFits = f.type.equals(typeDesc) || f.type.equals(altDesc)
                            || (narrowed && r.index.extendsClass(f.type, typeDesc))
                            || (intLike && f.type.length() == 1 && "BSCI".contains(f.type));
                    if (f.write != ordinalOfReads && f.owner.equals(cls.descriptor) && typeFits
                            && !order.contains(f.name)) order.add(f.name);
                }
                if (writeOrdinal >= order.size()) return Resolver.Attempt.notFound();
                DexClass.Field declared = cls.fieldNamed(order.get(writeOrdinal));
                return declared == null ? Resolver.Attempt.notFound() : Resolver.Attempt.of(declared, false);
            }
            if (typeDesc != null && lastReaders != null) {
                DexClass.Method post = postMethod == null ? null : r.methods.get(postMethod);
                if (postMethod != null && post == null && r.isResolvedOrPending(postMethod)) return Resolver.Attempt.waiting();
                for (String reader : lastReaders) {
                    DexClass.Method m = r.methods.get(reader);
                    if (m == null && r.isResolvedOrPending(reader)) return Resolver.Attempt.waiting();
                    if (m == null) continue;
                    String last = null;
                    for (Body.Refs.FieldRef f : Body.Refs.of(r, m).fields) {
                        if (!f.write && f.owner.equals(cls.descriptor) && f.type.equals(typeDesc)) last = f.name;
                    }
                    if (last != null) {
                        DexClass.Field declared = cls.fieldNamed(last);
                        return declared == null ? Resolver.Attempt.notFound() : Resolver.Attempt.of(declared, false);
                    }
                    if (post == null) continue;
                    List<Long> posted = m.constantArguments(post.owner.descriptor, post.name(), 1);
                    Long id = posted.isEmpty() ? null : posted.get(posted.size() - 1);
                    if (id != null) return holding(cls, typeDesc, id);
                }
            }
            return Resolver.Attempt.notFound();
        }

        /** The static field of {@code cls} holding {@code value}: its initial value, or what <clinit> stores. */
        private static Resolver.Attempt holding(DexClass cls, String typeDesc, long value) {
            List<DexClass.Field> found = new ArrayList<>();
            java.util.Map<String, Long> stored = new java.util.HashMap<>();
            for (DexClass.Method m : cls.methods) {
                if (m.name().equals("<clinit>")) stored = m.staticIntWrites();
            }
            for (DexClass.Field f : cls.fields) {
                if (!f.isStatic() || !f.type().equals(typeDesc)) continue;
                Long v = stored.containsKey(f.name()) ? stored.get(f.name()) : cls.initialValue(f);
                if (v != null && v == value) found.add(f);
            }
            return Resolver.Attempt.single(found);
        }

        @Override
        void store(Resolver r, Resolver.Attempt attempt, Mapping mapping) {
            DexClass.Field f = (DexClass.Field) attempt.found;
            r.fields.put(id(), f);
            mapping.fields.put(Mapping.memberKey(owner, name), f.name());
        }
    }

    // ------------------------------------------------------------------ utils

    static String javaName(String descriptor) {
        return DexNames.toJavaName(descriptor);
    }
}
