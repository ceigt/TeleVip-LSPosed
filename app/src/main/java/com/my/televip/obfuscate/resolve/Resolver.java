package com.my.televip.obfuscate.resolve;

import com.my.televip.obfuscate.dex.DexClass;
import com.my.televip.obfuscate.dex.DexIndex;
import com.my.televip.obfuscate.dex.DexNames;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves a set of symbol descriptions against one client build.
 *
 * <p>Every symbol is looked for by its real name first: a build that keeps a name needs no
 * fingerprint, and R8 never invents a descriptive name, so a match on one is conclusive. Only what
 * the build renamed goes to a fingerprint. A fingerprint has to single out exactly one class or
 * member; none leaves the symbol unresolved, and two or more is treated the same way and recorded
 * as ambiguous. Attaching a privacy feature to the wrong member is worse than leaving it off,
 * because it looks like it is working.</p>
 *
 * <p>Symbols can depend on each other - a method's signature mentions other classes that may also
 * be renamed - so resolution repeats until a pass makes no progress.</p>
 */
public final class Resolver {

    /** How one symbol ended up. */
    public enum Outcome { KEPT, FINGERPRINTED, AMBIGUOUS, UNRESOLVED }

    public static final class Report {
        public final Map<String, Outcome> outcomes = new LinkedHashMap<>();
        public final Map<String, List<String>> ambiguities = new LinkedHashMap<>();

        public int count(Outcome outcome) {
            int n = 0;
            for (Outcome o : outcomes.values()) if (o == outcome) n++;
            return n;
        }
    }

    final DexIndex index;
    final Map<String, DexClass> classes = new HashMap<>();
    final Map<String, DexClass.Method> methods = new HashMap<>();
    final Map<String, DexClass.Field> fields = new HashMap<>();
    final Map<DexClass.Method, Body.Refs> refs = new HashMap<>();
    private final Set<String> pendingClasses = new java.util.HashSet<>();
    private final Set<String> pendingMethods = new java.util.HashSet<>();
    private final Set<String> pendingFields = new java.util.HashSet<>();
    private Map<String, String> ownerSimpleNames;
    private final Set<String> anchorStrings = new java.util.LinkedHashSet<>();
    private final Set<Long> anchorConstants = new java.util.LinkedHashSet<>();
    private DexIndex.AnchorHits anchorHits;
    private final Map<String, String> ownerFullNames;
    /** Ignore real names wherever a fingerprint exists: to test fingerprints on a build that keeps them. */
    boolean fingerprintsOnly;

    public Resolver(DexIndex index, Map<String, String> ownerFullNames) {
        this.index = index;
        this.ownerFullNames = ownerFullNames;
    }

    /** Finds every symbol that has a fingerprint by it alone, as if the build had renamed it. */
    public Resolver fingerprintsOnly() {
        fingerprintsOnly = true;
        return this;
    }

    // ------------------------------------------------------------------ run

    public Mapping resolve(Collection<Symbol> symbols, Report report) {
        Mapping mapping = new Mapping();
        List<Symbol> pending = new ArrayList<>(symbols);
        pendingClasses.clear();
        pendingMethods.clear();
        pendingFields.clear();
        for (Symbol s : symbols) {
            if (s instanceof Symbol.MethodSymbol) pendingMethods.add(s.id());
            if (s instanceof Symbol.FieldSymbol) pendingFields.add(s.id());
            if (s instanceof Symbol.ClassSymbol) {
                Symbol.ClassSymbol c = (Symbol.ClassSymbol) s;
                pendingClasses.add(c.original);
                for (Symbol.ClassSource source : c.sources) {
                    if (source instanceof Classes.StringAnchored) {
                        Collections.addAll(anchorStrings, ((Classes.StringAnchored) source).strings);
                    } else if (source instanceof Classes.ConstantAnchored) {
                        anchorConstants.add(((Classes.ConstantAnchored) source).value);
                    }
                }
            }
        }
        boolean progress = true;
        while (progress && !pending.isEmpty()) {
            progress = false;
            for (int i = 0; i < pending.size(); i++) {
                Symbol symbol = pending.get(i);
                Attempt attempt = symbol.attempt(this);
                if (attempt.kind == Attempt.Kind.WAITING) continue;
                pending.remove(i--);
                progress = true;
                record(symbol, attempt, mapping, report);
                if (symbol instanceof Symbol.ClassSymbol) {
                    pendingClasses.remove(((Symbol.ClassSymbol) symbol).original);
                }
                pendingMethods.remove(symbol.id());
                pendingFields.remove(symbol.id());
            }
        }
        // Anything still waiting depends on a symbol that never resolved.
        for (Symbol symbol : pending) report.outcomes.put(symbol.id(), Outcome.UNRESOLVED);
        pendingFields.clear();
        return mapping;
    }

    private void record(Symbol symbol, Attempt attempt, Mapping mapping, Report report) {
        switch (attempt.kind) {
            case FOUND:
                symbol.store(this, attempt, mapping);
                report.outcomes.put(symbol.id(), attempt.kept ? Outcome.KEPT : Outcome.FINGERPRINTED);
                break;
            case AMBIGUOUS:
                report.outcomes.put(symbol.id(), Outcome.AMBIGUOUS);
                report.ambiguities.put(symbol.id(), attempt.candidates);
                break;
            default:
                report.outcomes.put(symbol.id(), Outcome.UNRESOLVED);
                break;
        }
    }

    // ---------------------------------------------------------------- lookups

    /**
     * The class an original name resolved to. A name the build keeps resolves to itself even when
     * no symbol declares it, since R8 never gives a renamed class a descriptive name. Null when it
     * is renamed and has not been resolved (yet).
     */
    public DexClass cls(String originalName) {
        DexClass resolved = classes.get(originalName);
        if (resolved != null) return resolved;
        if (pendingClasses.contains(originalName)) return null;
        DexClass kept = index.findClass(originalName);
        if (kept != null) classes.put(originalName, kept);
        return kept;
    }

    /** Results of the one shared bytecode scan for every string and constant fingerprints anchor on. */
    DexIndex.AnchorHits anchors() {
        if (anchorHits == null) {
            anchorHits = index.scanAnchors(anchorStrings, anchorConstants);
        }
        return anchorHits;
    }

    /** True if a method symbol has resolved, or is still in this run's queue. */
    boolean isResolvedOrPending(String methodSymbolId) {
        return methods.containsKey(methodSymbolId) || pendingMethods.contains(methodSymbolId);
    }

    /** True while a class symbol for this name is still waiting to be resolved in this run. */
    boolean pendingOrResolvable(String originalName) {
        return pendingClasses.contains(originalName);
    }

    /**
     * What a field of an original class is called in this build: its resolved name when a field
     * symbol describes it, its own name otherwise. Null while that symbol is still being resolved.
     */
    String fieldName(String ownerFullName, String name) {
        if (ownerSimpleNames == null) {
            ownerSimpleNames = new HashMap<>();
            for (Map.Entry<String, String> e : ownerFullNames.entrySet()) ownerSimpleNames.put(e.getValue(), e.getKey());
        }
        String simple = ownerSimpleNames.get(ownerFullName);
        if (simple == null) return name;
        String id = simple + "." + name;
        DexClass.Field f = fields.get(id);
        if (f != null) return f.name();
        return pendingFields.contains(id) ? null : name;
    }

    String fullName(String ownerSimpleName) {
        String full = ownerFullNames.get(ownerSimpleName);
        return full != null ? full : ownerSimpleName;
    }

    /**
     * Descriptor for a type written as in Java source: {@code int}, {@code java.lang.String},
     * {@code android.view.View[]}, or an original app class name, which is mapped through whatever
     * has been resolved. Returns null for an app class that has not been resolved yet.
     */
    public String descriptor(String type) {
        if (type.endsWith("[]")) {
            String element = descriptor(type.substring(0, type.length() - 2));
            return element == null ? null : "[" + element;
        }
        if (type.indexOf('.') < 0) return DexNames.toDescriptor(type);   // primitive
        if (isPlatformType(type)) return DexNames.toDescriptor(type);
        DexClass resolved = cls(type);
        return resolved == null ? null : resolved.descriptor;
    }

    /** Method descriptor such as {@code (Ljava/lang/String;I)V}, or null if a type is unresolved. */
    public String proto(String returnType, String... params) {
        String ret = descriptor(returnType);
        if (ret == null) return null;
        String[] descs = new String[params.length];
        for (int i = 0; i < params.length; i++) {
            descs[i] = descriptor(params[i]);
            if (descs[i] == null) return null;
        }
        return protoOf(ret, descs);
    }

    static String protoOf(DexClass.Method method) {
        return protoOf(method.returnType(), method.parameterTypes());
    }

    static String protoOf(String returnDescriptor, String[] paramDescriptors) {
        StringBuilder sb = new StringBuilder("(");
        for (String p : paramDescriptors) sb.append(p);
        return sb.append(')').append(returnDescriptor).toString();
    }

    static boolean isPlatformType(String javaName) {
        return javaName.startsWith("java.") || javaName.startsWith("javax.")
                || javaName.startsWith("android.") || javaName.startsWith("dalvik.");
    }

    // ----------------------------------------------------------------- result

    static final class Attempt {
        enum Kind { FOUND, AMBIGUOUS, NOT_FOUND, WAITING }

        final Kind kind;
        final Object found;
        final boolean kept;
        final List<String> candidates;

        private Attempt(Kind kind, Object found, boolean kept, List<String> candidates) {
            this.kind = kind;
            this.found = found;
            this.kept = kept;
            this.candidates = candidates;
        }

        static Attempt waiting() {
            return new Attempt(Kind.WAITING, null, false, null);
        }

        static Attempt notFound() {
            return new Attempt(Kind.NOT_FOUND, null, false, null);
        }

        static Attempt of(Object found, boolean kept) {
            return new Attempt(Kind.FOUND, found, kept, null);
        }

        /** Exactly one candidate is a match; anything else is not. */
        static <T> Attempt single(Collection<T> candidates) {
            Set<T> unique = new LinkedHashSet<>(candidates);
            if (unique.size() == 1) return of(unique.iterator().next(), false);
            if (unique.isEmpty()) return notFound();
            List<String> names = new ArrayList<>();
            for (T t : unique) names.add(String.valueOf(t));
            Collections.sort(names);
            return new Attempt(Kind.AMBIGUOUS, null, false, names);
        }
    }
}
