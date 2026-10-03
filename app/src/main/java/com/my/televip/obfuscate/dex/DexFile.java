package com.my.televip.obfuscate.dex;

import java.nio.charset.StandardCharsets;

/**
 * A read-only view of one dex file, decoded lazily straight from its bytes.
 *
 * <p>This exists so TeleVip can find a class or method that R8 renamed by looking at what it is
 * rather than what it is called: which strings it loads, which constants it uses, what it extends,
 * what its methods take and return. It is deliberately plain Java with no Android dependency, so
 * the exact code that runs inside the client on the phone also runs in JVM tests against a real
 * client APK. Anything a fingerprint relies on is therefore tested against the build it targets.</p>
 *
 * <p>Nothing is decoded up front beyond the header. Tables are read on demand and strings are
 * cached once decoded, because a Telegram client carries tens of thousands of classes and only a
 * small fraction of them are ever looked at.</p>
 */
public final class DexFile {

    static final int NO_INDEX = -1;

    private final byte[] data;
    private final String name;

    private final int stringIdsSize, stringIdsOff;
    private final int typeIdsSize, typeIdsOff;
    private final int protoIdsSize, protoIdsOff;
    private final int fieldIdsSize, fieldIdsOff;
    private final int methodIdsSize, methodIdsOff;
    private final int classDefsSize, classDefsOff;

    private final String[] strings;

    public DexFile(String name, byte[] data) {
        if (data == null || data.length < 0x70
                || data[0] != 'd' || data[1] != 'e' || data[2] != 'x' || data[3] != '\n') {
            throw new IllegalArgumentException("not a dex file: " + name);
        }
        if (u4(data, 0x28) != 0x12345678) {
            throw new IllegalArgumentException("unsupported endianness in " + name);
        }
        this.data = data;
        this.name = name;
        stringIdsSize = u4(data, 0x38);
        stringIdsOff = u4(data, 0x3C);
        typeIdsSize = u4(data, 0x40);
        typeIdsOff = u4(data, 0x44);
        protoIdsSize = u4(data, 0x48);
        protoIdsOff = u4(data, 0x4C);
        fieldIdsSize = u4(data, 0x50);
        fieldIdsOff = u4(data, 0x54);
        methodIdsSize = u4(data, 0x58);
        methodIdsOff = u4(data, 0x5C);
        classDefsSize = u4(data, 0x60);
        classDefsOff = u4(data, 0x64);
        strings = new String[stringIdsSize];
    }

    public String name() {
        return name;
    }

    byte[] data() {
        return data;
    }

    // ------------------------------------------------------------------ sizes

    public int methodCount() {
        return methodIdsSize;
    }

    public int classDefCount() {
        return classDefsSize;
    }

    // ---------------------------------------------------------------- strings

    public String string(int index) {
        if (index < 0 || index >= stringIdsSize) return null;
        String cached = strings[index];
        if (cached != null) return cached;
        int off = u4(data, stringIdsOff + index * 4);
        int[] cursor = {off};
        int utf16Length = uleb128(data, cursor);
        String decoded = mutf8(data, cursor[0], utf16Length);
        strings[index] = decoded;
        return decoded;
    }

    /** Binary search over the sorted string table. Returns -1 when the string is absent. */
    public int findString(String value) {
        int lo = 0, hi = stringIdsSize - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int cmp = compareDexStrings(string(mid), value);
            if (cmp < 0) lo = mid + 1;
            else if (cmp > 0) hi = mid - 1;
            else return mid;
        }
        return -1;
    }

    /**
     * Dex sorts its string table by UTF-16 code unit, which is what String.compareTo does too.
     * Kept as its own method so the assumption is written down in one place.
     */
    private static int compareDexStrings(String a, String b) {
        return a.compareTo(b);
    }

    // ------------------------------------------------------------------ types

    /** Type descriptor, e.g. {@code Lorg/telegram/ui/ChatActivity;} or {@code I}. */
    public String type(int index) {
        if (index < 0 || index >= typeIdsSize) return null;
        return string(u4(data, typeIdsOff + index * 4));
    }

    // ----------------------------------------------------------------- protos

    public String protoReturnType(int protoIndex) {
        return type(u4(data, protoIdsOff + protoIndex * 12 + 4));
    }

    public String[] protoParameters(int protoIndex) {
        int paramsOff = u4(data, protoIdsOff + protoIndex * 12 + 8);
        if (paramsOff == 0) return new String[0];
        int size = u4(data, paramsOff);
        String[] params = new String[size];
        for (int i = 0; i < size; i++) {
            params[i] = type(u2(data, paramsOff + 4 + i * 2));
        }
        return params;
    }

    /** Index into the proto table, or -1 if this dex has no such signature. {@code ret} null = any. */
    public int[] findProtos(String ret, String[] params) {
        java.util.List<Integer> found = new java.util.ArrayList<>();
        for (int i = 0; i < protoIdsSize; i++) {
            if (ret != null && !ret.equals(protoReturnType(i))) continue;
            int paramsOff = u4(data, protoIdsOff + i * 12 + 8);
            int size = paramsOff == 0 ? 0 : u4(data, paramsOff);
            if (size != params.length) continue;
            boolean same = true;
            for (int p = 0; p < size && same; p++) {
                same = params[p].equals(type(u2(data, paramsOff + 4 + p * 2)));
            }
            if (same) found.add(i);
        }
        int[] result = new int[found.size()];
        for (int i = 0; i < result.length; i++) result[i] = found.get(i);
        return result;
    }

    // --------------------------------------------------------- field / method

    public String fieldClass(int index) {
        return type(u2(data, fieldIdsOff + index * 8));
    }

    public String fieldType(int index) {
        return type(u2(data, fieldIdsOff + index * 8 + 2));
    }

    public String fieldName(int index) {
        return string(u4(data, fieldIdsOff + index * 8 + 4));
    }

    public String methodClass(int index) {
        return type(u2(data, methodIdsOff + index * 8));
    }

    public int methodProto(int index) {
        return u2(data, methodIdsOff + index * 8 + 2);
    }

    public String methodName(int index) {
        return string(u4(data, methodIdsOff + index * 8 + 4));
    }

    // ------------------------------------------------------------- class defs

    public String classDescriptor(int classDefIndex) {
        return type(u4(data, classDefsOff + classDefIndex * 32));
    }

    public int classAccessFlags(int classDefIndex) {
        return u4(data, classDefsOff + classDefIndex * 32 + 4);
    }

    public String classSuperclass(int classDefIndex) {
        int idx = u4(data, classDefsOff + classDefIndex * 32 + 8);
        return idx == NO_INDEX ? null : type(idx);
    }

    public String[] classInterfaces(int classDefIndex) {
        int off = u4(data, classDefsOff + classDefIndex * 32 + 12);
        if (off == 0) return new String[0];
        int size = u4(data, off);
        String[] result = new String[size];
        for (int i = 0; i < size; i++) {
            result[i] = type(u2(data, off + 4 + i * 2));
        }
        return result;
    }

    /**
     * The initial values of a class's static fields that are int-like constants (byte, short,
     * char, int), in static field order; null where the value is of another kind or absent.
     */
    public Long[] staticIntValues(int classDefIndex, int staticFieldCount) {
        Long[] out = new Long[staticFieldCount];
        int off = u4(data, classDefsOff + classDefIndex * 32 + 28);
        if (off == 0) return out;
        int[] cursor = {off};
        int size = uleb128(data, cursor);
        for (int i = 0; i < size; i++) {
            Long v = readEncodedInt(cursor);
            if (i < staticFieldCount) out[i] = v;
        }
        return out;
    }

    /** Reads one encoded_value; returns it if int-like, skips it otherwise. */
    private Long readEncodedInt(int[] cursor) {
        int header = data[cursor[0]++] & 0xFF;
        int type = header & 0x1F, arg = header >> 5;
        switch (type) {
            case 0x00: case 0x02: case 0x03: case 0x04: {          // byte, short, char, int
                long v = 0;
                for (int b = 0; b <= arg; b++) v |= (long) (data[cursor[0]++] & 0xFF) << (8 * b);
                int bits = 8 * (arg + 1);
                if (type != 0x03 && bits < 64) v = (v << (64 - bits)) >> (64 - bits);   // sign-extend
                return v;
            }
            case 0x1C: {                                            // array
                int n = uleb128(data, cursor);
                for (int i = 0; i < n; i++) readEncodedInt(cursor);
                return null;
            }
            case 0x1D: {                                            // annotation
                uleb128(data, cursor);
                int n = uleb128(data, cursor);
                for (int i = 0; i < n; i++) {
                    uleb128(data, cursor);
                    readEncodedInt(cursor);
                }
                return null;
            }
            case 0x1E: case 0x1F:                                   // null, boolean
                return null;
            default:                                                // sized values
                cursor[0] += arg + 1;
                return null;
        }
    }

    int classDataOffset(int classDefIndex) {
        return u4(data, classDefsOff + classDefIndex * 32 + 24);
    }

    // --------------------------------------------------------------- encoding

    static int u2(byte[] d, int off) {
        return (d[off] & 0xFF) | ((d[off + 1] & 0xFF) << 8);
    }

    static int u4(byte[] d, int off) {
        return (d[off] & 0xFF) | ((d[off + 1] & 0xFF) << 8)
                | ((d[off + 2] & 0xFF) << 16) | ((d[off + 3] & 0xFF) << 24);
    }

    /** Reads an unsigned LEB128 at cursor[0] and advances it. */
    static int uleb128(byte[] d, int[] cursor) {
        int result = 0;
        int shift = 0;
        int pos = cursor[0];
        int b;
        do {
            b = d[pos++] & 0xFF;
            result |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0 && shift < 35);
        cursor[0] = pos;
        return result;
    }

    /**
     * Modified UTF-8 as dex stores it: NUL is two bytes, and characters outside the BMP are two
     * separately encoded surrogates, so plain UTF-8 decoding would be subtly wrong.
     */
    static String mutf8(byte[] d, int off, int utf16Length) {
        char[] out = new char[utf16Length];
        int pos = off;
        for (int i = 0; i < utf16Length; i++) {
            int a = d[pos++] & 0xFF;
            if (a < 0x80) {
                out[i] = (char) a;
            } else if ((a & 0xE0) == 0xC0) {
                int b = d[pos++] & 0x3F;
                out[i] = (char) (((a & 0x1F) << 6) | b);
            } else if ((a & 0xF0) == 0xE0) {
                int b = d[pos++] & 0x3F;
                int c = d[pos++] & 0x3F;
                out[i] = (char) (((a & 0x0F) << 12) | (b << 6) | c);
            } else {
                // Not valid MUTF-8. Fall back to a lossy decode of the remainder rather than throw:
                // one odd string must not take down the whole index.
                return new String(d, off, Math.max(0, pos - off), StandardCharsets.ISO_8859_1);
            }
        }
        return new String(out);
    }
}
