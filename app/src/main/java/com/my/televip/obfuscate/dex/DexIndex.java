package com.my.televip.obfuscate.dex;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Every class in an APK's dex files, addressable by name and by what the code inside it does.
 *
 * <p>Built once per client build, inside the client, from its own APK. Only the class-def tables
 * are walked eagerly (to know where each class lives and who extends whom); class bodies are
 * decoded when first asked for, and bytecode is only scanned in batches for the specific strings
 * and constants the fingerprints ask about.</p>
 */
public final class DexIndex {

    private static final class Location {
        final DexFile dex;
        final int classDefIndex;

        Location(DexFile dex, int classDefIndex) {
            this.dex = dex;
            this.classDefIndex = classDefIndex;
        }
    }

    private final List<DexFile> dexFiles;
    private final Map<String, Location> byDescriptor = new HashMap<>();
    private final Map<String, List<String>> subclasses = new HashMap<>();
    private final Map<String, DexClass> decoded = new HashMap<>();

    public DexIndex(List<DexFile> dexFiles) {
        this.dexFiles = Collections.unmodifiableList(new ArrayList<>(dexFiles));
        for (DexFile dex : this.dexFiles) {
            for (int i = 0; i < dex.classDefCount(); i++) {
                String descriptor = dex.classDescriptor(i);
                // The first definition wins, exactly as the runtime class loader resolves it.
                if (byDescriptor.containsKey(descriptor)) continue;
                byDescriptor.put(descriptor, new Location(dex, i));
                String superclass = dex.classSuperclass(i);
                if (superclass != null) {
                    List<String> list = subclasses.get(superclass);
                    if (list == null) subclasses.put(superclass, list = new ArrayList<>());
                    list.add(descriptor);
                }
            }
        }
    }

    /** Loads classes.dex, classes2.dex, ... from an APK, in the order the runtime would. */
    public static DexIndex fromApk(File apk) throws IOException {
        List<DexFile> result = new ArrayList<>();
        try (ZipFile zip = new ZipFile(apk)) {
            for (int n = 1; ; n++) {
                String entryName = n == 1 ? "classes.dex" : "classes" + n + ".dex";
                ZipEntry entry = zip.getEntry(entryName);
                if (entry == null) break;
                try (InputStream in = zip.getInputStream(entry)) {
                    result.add(new DexFile(entryName, readAll(in, entry.getSize())));
                }
            }
        }
        if (result.isEmpty()) throw new IOException("no classes.dex in " + apk);
        return new DexIndex(result);
    }

    private static byte[] readAll(InputStream in, long sizeHint) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(
                sizeHint > 0 && sizeHint < Integer.MAX_VALUE ? (int) sizeHint : 1 << 20);
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- lookup

    public boolean hasClass(String javaName) {
        return byDescriptor.containsKey(DexNames.toDescriptor(javaName));
    }

    public DexClass findClass(String javaName) {
        return byDescriptor(DexNames.toDescriptor(javaName));
    }

    public DexClass byDescriptor(String descriptor) {
        if (descriptor == null) return null;
        DexClass cached = decoded.get(descriptor);
        if (cached != null) return cached;
        Location location = byDescriptor.get(descriptor);
        if (location == null) return null;
        DexClass cls = new DexClass(location.dex, location.classDefIndex);
        decoded.put(descriptor, cls);
        return cls;
    }

    /** Direct subclasses only. */
    public List<DexClass> subclassesOf(String descriptor) {
        List<String> list = subclasses.get(descriptor);
        if (list == null) return Collections.emptyList();
        List<DexClass> result = new ArrayList<>(list.size());
        for (String d : list) result.add(byDescriptor(d));
        return result;
    }

    public Iterable<String> descriptors() {
        return byDescriptor.keySet();
    }

    /** True when {@code descriptor} is {@code ancestor} or inherits from it, as far as this APK shows. */
    public boolean extendsClass(String descriptor, String ancestor) {
        String current = descriptor;
        for (int depth = 0; current != null && depth < 64; depth++) {
            if (current.equals(ancestor)) return true;
            Location location = byDescriptor.get(current);
            if (location == null) return false;
            current = location.dex.classSuperclass(location.classDefIndex);
        }
        return false;
    }

    /**
     * Classes defined in this APK that declare a method with these parameter types (and this
     * return type, unless it is null). Walks the method tables only - no bytecode is read.
     */
    public List<DexClass> classesDeclaring(String returnDescriptor, String[] paramDescriptors) {
        java.util.Set<String> owners = new java.util.LinkedHashSet<>();
        for (DexFile dex : dexFiles) {
            int[] protos = dex.findProtos(returnDescriptor, paramDescriptors);
            if (protos.length == 0) continue;
            java.util.Set<Integer> wanted = new java.util.HashSet<>();
            for (int p : protos) wanted.add(p);
            for (int m = 0; m < dex.methodCount(); m++) {
                if (wanted.contains(dex.methodProto(m))) owners.add(dex.methodClass(m));
            }
        }
        List<DexClass> result = new ArrayList<>();
        for (String owner : owners) {
            DexClass c = byDescriptor(owner);   // null for classes only referenced, not defined
            if (c != null) result.add(c);
        }
        return result;
    }

    // --------------------------------------------------------------- anchors

    /** What one scan over all bytecode found for the requested strings and constants. */
    public static final class AnchorHits {
        public final Map<String, List<DexClass.Method>> byString = new LinkedHashMap<>();
        public final Map<Long, List<DexClass.Method>> byConstant = new LinkedHashMap<>();

        public List<DexClass.Method> string(String s) {
            List<DexClass.Method> list = byString.get(s);
            return list == null ? Collections.<DexClass.Method>emptyList() : list;
        }

        public List<DexClass.Method> constant(long c) {
            List<DexClass.Method> list = byConstant.get(c);
            return list == null ? Collections.<DexClass.Method>emptyList() : list;
        }
    }

    /**
     * One pass over every method body in the APK, recording which methods load any of the given
     * strings or constants. Batched on purpose: this is the expensive part of resolution, so all
     * fingerprints declare their anchors up front and share a single scan.
     */
    public AnchorHits scanAnchors(Collection<String> strings, Collection<Long> constants) {
        final AnchorHits hits = new AnchorHits();
        for (String s : strings) hits.byString.put(s, new ArrayList<DexClass.Method>());
        for (Long c : constants) hits.byConstant.put(c, new ArrayList<DexClass.Method>());
        final java.util.Set<Long> wantedConstants = new java.util.HashSet<>(constants);

        for (DexFile dex : dexFiles) {
            // Anchor strings only exist in a dex under an index of that dex's own table.
            final Map<Integer, String> wantedStrings = new HashMap<>();
            for (String s : strings) {
                int idx = dex.findString(s);
                if (idx >= 0) wantedStrings.put(idx, s);
            }
            if (wantedStrings.isEmpty() && wantedConstants.isEmpty()) continue;

            for (int i = 0; i < dex.classDefCount(); i++) {
                String descriptor = dex.classDescriptor(i);
                Location location = byDescriptor.get(descriptor);
                if (location == null || location.dex != dex || location.classDefIndex != i) continue;
                DexClass cls = byDescriptor(descriptor);
                for (final DexClass.Method method : cls.methods) {
                    if (!method.hasCode()) continue;
                    method.scan(new CodeScanner.Visitor() {
                        @Override
                        public void string(int stringIndex) {
                            String anchor = wantedStrings.get(stringIndex);
                            if (anchor != null) addOnce(hits.byString.get(anchor), method);
                        }

                        @Override
                        public void constant(long value) {
                            if (wantedConstants.contains(value)) {
                                addOnce(hits.byConstant.get(value), method);
                            }
                        }
                    });
                }
            }
        }
        return hits;
    }

    private static void addOnce(List<DexClass.Method> list, DexClass.Method method) {
        if (list.isEmpty() || list.get(list.size() - 1) != method) list.add(method);
    }

}
