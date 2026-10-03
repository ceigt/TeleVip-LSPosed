package com.my.televip.obfuscate.resolve;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The result of resolving one client build: original name to the name that build actually uses.
 *
 * <p>Keyed exactly the way the static per-client tables are, so it can stand in for one without
 * any call site noticing: classes by full original name, fields and methods by the owner's simple
 * name (e.g. {@code TLRPC$Message}) plus the member key the call sites already use.</p>
 */
public final class Mapping {

    final Map<String, String> classes = new LinkedHashMap<>();
    final Map<String, String> fields = new LinkedHashMap<>();
    final Map<String, String> methods = new LinkedHashMap<>();
    /** Real parameter lists for methods the build deleted parameters from, by source method name. */
    final Map<String, String[]> parameters = new LinkedHashMap<>();

    static String memberKey(String ownerSimpleName, String key) {
        return ownerSimpleName + "#" + key;
    }

    public String resolveClass(String originalName) {
        return classes.get(originalName);
    }

    public String resolveField(String ownerSimpleName, String name) {
        return fields.get(memberKey(ownerSimpleName, name));
    }

    public String resolveMethod(String ownerSimpleName, String key) {
        return methods.get(memberKey(ownerSimpleName, key));
    }

    /** Java type names of the real parameters, or null if the build kept the source list. */
    public String[] resolveParameters(String methodName) {
        return parameters.get(methodName);
    }

    void putParameters(String methodName, String[] javaTypeNames) {
        // Keyed by bare method name because that is all the call sites pass. Two renamed methods
        // sharing a name and both losing parameters would collide; the first one keeps the slot.
        if (!parameters.containsKey(methodName)) parameters.put(methodName, javaTypeNames);
    }

    public Map<String, String> classes() {
        return Collections.unmodifiableMap(classes);
    }

    public Map<String, String> fields() {
        return Collections.unmodifiableMap(fields);
    }

    public Map<String, String> methods() {
        return Collections.unmodifiableMap(methods);
    }

    public int size() {
        return classes.size() + fields.size() + methods.size();
    }

    // ------------------------------------------------------------ persistence

    /**
     * One entry per line: {@code C|F|M <tab> key <tab> value}. Deliberately trivial, so a cached
     * mapping can be read back without a JSON library and inspected by eye when something is off.
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder("TELEVIP_MAPPING_1\n");
        for (Map.Entry<String, String> e : classes.entrySet()) line(sb, 'C', e);
        for (Map.Entry<String, String> e : fields.entrySet()) line(sb, 'F', e);
        for (Map.Entry<String, String> e : methods.entrySet()) line(sb, 'M', e);
        for (Map.Entry<String, String[]> e : parameters.entrySet()) {
            sb.append('P').append('\t').append(e.getKey()).append('\t')
                    .append(String.join(",", e.getValue())).append('\n');
        }
        return sb.toString();
    }

    private static void line(StringBuilder sb, char kind, Map.Entry<String, String> e) {
        sb.append(kind).append('\t').append(e.getKey()).append('\t').append(e.getValue()).append('\n');
    }

    public static Mapping deserialize(String text) {
        if (!text.startsWith("TELEVIP_MAPPING_1\n") || !text.endsWith("\n")) throw new IllegalArgumentException("Invalid mapping cache");
        Mapping mapping = new Mapping();
        for (String line : text.substring("TELEVIP_MAPPING_1\n".length()).split("\n")) {
            String[] parts = line.split("\t", -1);
            if (parts.length != 3 || parts[0].length() != 1 || parts[1].isEmpty()) throw new IllegalArgumentException("Malformed mapping row");
            switch (parts[0].charAt(0)) {
                case 'C': mapping.classes.put(parts[1], parts[2]); break;
                case 'F': mapping.fields.put(parts[1], parts[2]); break;
                case 'M': mapping.methods.put(parts[1], parts[2]); break;
                case 'P':
                    mapping.parameters.put(parts[1],
                            parts[2].isEmpty() ? new String[0] : parts[2].split(","));
                    break;
                default: throw new IllegalArgumentException("Unknown mapping row");
            }
        }
        return mapping;
    }
}
