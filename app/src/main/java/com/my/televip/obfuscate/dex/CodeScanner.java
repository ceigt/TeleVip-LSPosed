package com.my.televip.obfuscate.dex;

/**
 * Walks a method's Dalvik bytecode and reports the few things fingerprints care about: strings,
 * integer constants, and the methods, fields and types it refers to.
 *
 * <p>Only instruction widths have to be exactly right for this to work - get one wrong and every
 * instruction after it is misread. They come straight from the format column of the Dalvik
 * bytecode table, and the three payload pseudo-instructions (switch tables and array data), which
 * sit inline in the instruction stream behind a nop, are skipped by their own declared size.</p>
 */
public final class CodeScanner {

    private CodeScanner() {
    }

    /** Callbacks for one method. Every method has an empty default, so implement only what you need. */
    public interface Visitor {
        default void string(int stringIndex) {
        }

        /** const, const/4, const/16, const/high16 and the const-wide family, sign-extended. */
        default void constant(long value) {
        }

        /** invoke-* of any kind. */
        default void invoke(int opcode, int methodIndex) {
        }

        /** iget/iput/sget/sput of any kind. {@code write} is true for the put forms. */
        default void field(int opcode, int fieldIndex, boolean write) {
        }

        /** new-instance, check-cast, instance-of, const-class, new-array and filled-new-array. */
        default void type(int opcode, int typeIndex) {
        }

        /** Every key of a sparse-switch table, and every key a packed-switch covers. */
        default void switchKey(int key) {
        }

        /** The opcode of every real instruction (payload tables are not instructions). */
        default void opcode(int opcode) {
        }
    }

    /** Width in 16-bit code units for each opcode, from the Dalvik instruction formats. */
    private static final byte[] WIDTH = new byte[256];

    static {
        java.util.Arrays.fill(WIDTH, (byte) 1);
        set(2, 0x02, 0x05, 0x08, 0x13, 0x15, 0x16, 0x19, 0x1a, 0x1c, 0x1f, 0x20, 0x22, 0x23, 0x29);
        set(3, 0x03, 0x06, 0x09, 0x14, 0x17, 0x1b, 0x24, 0x25, 0x26, 0x2a, 0x2b, 0x2c);
        set(5, 0x18);
        range(0x2d, 0x31, 2);   // cmp*
        range(0x32, 0x37, 2);   // if-test
        range(0x38, 0x3d, 2);   // if-testz
        range(0x44, 0x51, 2);   // aget/aput
        range(0x52, 0x5f, 2);   // iget/iput
        range(0x60, 0x6d, 2);   // sget/sput
        range(0x6e, 0x72, 3);   // invoke-kind
        range(0x74, 0x78, 3);   // invoke-kind/range
        range(0x90, 0xaf, 2);   // binop
        range(0xd0, 0xd7, 2);   // binop/lit16
        range(0xd8, 0xe2, 2);   // binop/lit8
        set(4, 0xfa, 0xfb);     // invoke-polymorphic(/range)
        set(3, 0xfc, 0xfd);     // invoke-custom(/range)
        set(2, 0xfe, 0xff);     // const-method-handle, const-method-type
    }

    private static void set(int width, int... opcodes) {
        for (int op : opcodes) WIDTH[op] = (byte) width;
    }

    private static void range(int from, int to, int width) {
        for (int op = from; op <= to; op++) WIDTH[op] = (byte) width;
    }

    static void scan(DexFile dex, int codeOffset, Visitor v) {
        byte[] d = dex.data();
        int insnsSize = DexFile.u4(d, codeOffset + 12);
        int base = codeOffset + 16;
        int end = base + insnsSize * 2;
        int pc = base;
        while (pc < end) {
            int unit = DexFile.u2(d, pc);
            int op = unit & 0xFF;

            if (op == 0x00 && unit != 0x0000) {
                // A payload pseudo-instruction: nop with an identifier in the high byte.
                pc += payloadWidth(d, pc, unit, v) * 2;
                continue;
            }

            v.opcode(op);
            switch (op) {
                case 0x12: // const/4 vA, #+B: B is the signed top nibble of the unit
                    v.constant(((short) unit) >> 12);
                    break;
                case 0x13: // const/16
                case 0x16: // const-wide/16
                    v.constant((short) DexFile.u2(d, pc + 2));
                    break;
                case 0x14: // const
                case 0x17: // const-wide/32
                    v.constant(DexFile.u4(d, pc + 2));
                    break;
                case 0x15: // const/high16
                    v.constant(DexFile.u2(d, pc + 2) << 16);
                    break;
                case 0x18: { // const-wide
                    long lo = DexFile.u4(d, pc + 2) & 0xFFFFFFFFL;
                    long hi = DexFile.u4(d, pc + 6) & 0xFFFFFFFFL;
                    v.constant(lo | (hi << 32));
                    break;
                }
                case 0x19: // const-wide/high16
                    v.constant(((long) DexFile.u2(d, pc + 2)) << 48);
                    break;
                case 0x1a: // const-string
                    v.string(DexFile.u2(d, pc + 2));
                    break;
                case 0x1b: // const-string/jumbo
                    v.string(DexFile.u4(d, pc + 2));
                    break;
                case 0x1c: case 0x1f: case 0x20: case 0x22: case 0x23: case 0x24: case 0x25:
                    v.type(op, DexFile.u2(d, pc + 2));
                    break;
                default:
                    if (op >= 0x52 && op <= 0x6d) {
                        boolean write = (op >= 0x59 && op <= 0x5f) || (op >= 0x67 && op <= 0x6d);
                        v.field(op, DexFile.u2(d, pc + 2), write);
                    } else if ((op >= 0x6e && op <= 0x72) || (op >= 0x74 && op <= 0x78)
                            || op == 0xfa || op == 0xfb) {
                        v.invoke(op, DexFile.u2(d, pc + 2));
                    }
                    break;
            }
            pc += WIDTH[op] * 2;
        }
    }

    /** What {@link #constantFlow} reports. */
    interface ConstantSink {
        /** An sput of a value that follows from constants. */
        default void staticWrite(int fieldIndex, long value) {
        }

        /** Every invoke, with the constant each argument register holds (null where unknown). */
        default void invoke(int methodIndex, Long[] registers) {
        }
    }

    /**
     * Follows int constants through a method - const, move, literal and register additions, and
     * statics this method set itself ({@code a = total++; b = total++; ...}) - to the static
     * fields they are stored in and the calls they are passed to. A register written by anything
     * else is forgotten, so what is reported is known; it ignores branches, which is enough for
     * the straight-line initialisers and calls it is used on.
     */
    static void constantFlow(DexFile dex, int codeOffset, ConstantSink sink) {
        byte[] d = dex.data();
        int insnsSize = DexFile.u4(d, codeOffset + 12);
        int base = codeOffset + 16;
        int end = base + insnsSize * 2;
        java.util.Map<Integer, Long> regs = new java.util.HashMap<>();
        java.util.Map<Integer, Long> statics = new java.util.HashMap<>();
        Visitor none = new Visitor() { };
        int pc = base;
        while (pc < end) {
            int unit = DexFile.u2(d, pc);
            int op = unit & 0xFF;
            if (op == 0x00 && unit != 0x0000) {
                pc += payloadWidth(d, pc, unit, none) * 2;
                continue;
            }
            int a = (unit >> 8) & 0xFF, a4 = (unit >> 8) & 0xF, b4 = (unit >> 12) & 0xF;
            switch (op) {
                case 0x01: put(regs, a4, regs.get(b4)); break;                                  // move
                case 0x02: put(regs, a, regs.get(DexFile.u2(d, pc + 2))); break;                // move/from16
                case 0x12: regs.put(a4, (long) (((short) unit) >> 12)); break;                  // const/4
                case 0x13: regs.put(a, (long) (short) DexFile.u2(d, pc + 2)); break;            // const/16
                case 0x14: regs.put(a, (long) DexFile.u4(d, pc + 2)); break;                    // const
                case 0x15: regs.put(a, (long) (DexFile.u2(d, pc + 2) << 16)); break;            // const/high16
                case 0xb0: put(regs, a4, sum(regs.get(a4), regs.get(b4))); break;               // add-int/2addr
                case 0x90: {                                                                    // add-int
                    int bc = DexFile.u2(d, pc + 2);
                    put(regs, a, sum(regs.get(bc & 0xFF), regs.get(bc >> 8)));
                    break;
                }
                case 0xd0: {                                                                    // add-int/lit16
                    Long src = regs.get(b4);
                    put(regs, a4, src == null ? null : src + (short) DexFile.u2(d, pc + 2));
                    break;
                }
                case 0xd8: {                                                                    // add-int/lit8
                    int bc = DexFile.u2(d, pc + 2);
                    Long src = regs.get(bc & 0xFF);
                    put(regs, a, src == null ? null : src + (byte) (bc >> 8));
                    break;
                }
                case 0x60:                                                                      // sget
                    put(regs, a, statics.get(DexFile.u2(d, pc + 2)));
                    break;
                case 0x67: {                                                                    // sput
                    int field = DexFile.u2(d, pc + 2);
                    Long value = regs.get(a);
                    if (value != null) {
                        statics.put(field, value);
                        sink.staticWrite(field, value);
                    }
                    break;
                }
                case 0x6e: case 0x6f: case 0x70: case 0x71: case 0x72: {                        // invoke-kind
                    int count = b4, words = DexFile.u2(d, pc + 4);
                    int[] r = {words & 0xF, (words >> 4) & 0xF, (words >> 8) & 0xF, (words >> 12) & 0xF, a4};
                    Long[] args = new Long[count];
                    for (int i = 0; i < count; i++) args[i] = regs.get(r[i]);
                    sink.invoke(DexFile.u2(d, pc + 2), args);
                    break;
                }
                case 0x74: case 0x75: case 0x76: case 0x77: case 0x78: {                        // invoke-kind/range
                    int first = DexFile.u2(d, pc + 4);
                    Long[] args = new Long[a];
                    for (int i = 0; i < a; i++) args[i] = regs.get(first + i);
                    sink.invoke(DexFile.u2(d, pc + 2), args);
                    break;
                }
                default: {
                    int dst = destination(op, a, a4);
                    if (op == 0x03 || op == 0x06 || op == 0x09) dst = DexFile.u2(d, pc + 2);  // move*/16
                    if (dst >= 0) {
                        regs.remove(dst);
                        regs.remove(dst + 1);   // the wide forms write a pair
                    }
                    break;
                }
            }
            pc += WIDTH[op] * 2;
        }
    }

    /** The register an instruction writes, -1 if none: vA for the 4-bit formats, vAA otherwise. */
    private static int destination(int op, int a, int a4) {
        if (op == 0x04 || op == 0x07 || op == 0x21 || op == 0x20 || op == 0x23
                || (op >= 0x52 && op <= 0x58) || (op >= 0x7b && op <= 0x8f)
                || (op >= 0xb0 && op <= 0xd7)) return a4;
        if (op == 0x05 || op == 0x08 || (op >= 0x0a && op <= 0x0d) || (op >= 0x16 && op <= 0x1c)
                || op == 0x1f || op == 0x22 || (op >= 0x2d && op <= 0x31) || (op >= 0x44 && op <= 0x4a)
                || (op >= 0x60 && op <= 0x66) || (op >= 0x90 && op <= 0xaf) || (op >= 0xd8 && op <= 0xe2)) return a;
        return -1;
    }

    /** The int each sput in a method stores, by field index, where constants decide it. */
    static java.util.Map<Integer, Long> staticIntWrites(DexFile dex, int codeOffset) {
        final java.util.Map<Integer, Long> out = new java.util.LinkedHashMap<>();
        constantFlow(dex, codeOffset, new ConstantSink() {
            @Override
            public void staticWrite(int fieldIndex, long value) {
                out.put(fieldIndex, value);
            }
        });
        return out;
    }

    private static void put(java.util.Map<Integer, Long> regs, int reg, Long value) {
        if (value == null) regs.remove(reg);
        else regs.put(reg, value);
    }

    private static Long sum(Long x, Long y) {
        return x == null || y == null ? null : x + y;
    }

    /** Width of a payload in code units, reporting switch keys on the way past. */
    private static int payloadWidth(byte[] d, int pc, int ident, Visitor v) {
        switch (ident) {
            case 0x0100: { // packed-switch-payload: size, first_key, targets[size]
                int size = DexFile.u2(d, pc + 2);
                int firstKey = DexFile.u4(d, pc + 4);
                for (int i = 0; i < size; i++) v.switchKey(firstKey + i);
                return size * 2 + 4;
            }
            case 0x0200: { // sparse-switch-payload: size, keys[size], targets[size]
                int size = DexFile.u2(d, pc + 2);
                for (int i = 0; i < size; i++) v.switchKey(DexFile.u4(d, pc + 4 + i * 4));
                return size * 4 + 2;
            }
            case 0x0300: { // fill-array-data-payload: element_width, size, data
                int elementWidth = DexFile.u2(d, pc + 2);
                long size = DexFile.u4(d, pc + 4) & 0xFFFFFFFFL;
                return (int) ((size * elementWidth + 1) / 2 + 4);
            }
            default:
                // Not a payload after all. Treat it as a one-unit nop.
                return 1;
        }
    }
}
