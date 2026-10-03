package com.my.televip.obfuscate.dex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One decoded class definition: its hierarchy and its declared fields and methods. */
public final class DexClass {

    public static final int ACC_STATIC = 0x8;
    public static final int ACC_ABSTRACT = 0x400;
    public static final int ACC_INTERFACE = 0x200;
    public static final int ACC_SYNTHETIC = 0x1000;
    public static final int ACC_CONSTRUCTOR = 0x10000;

    public final DexFile dex;
    public final int classDefIndex;
    public final String descriptor;
    public final int accessFlags;
    public final String superclass;
    public final String[] interfaces;
    public final List<Field> fields;
    public final List<Method> methods;

    DexClass(DexFile dex, int classDefIndex) {
        this.dex = dex;
        this.classDefIndex = classDefIndex;
        this.descriptor = dex.classDescriptor(classDefIndex);
        this.accessFlags = dex.classAccessFlags(classDefIndex);
        this.superclass = dex.classSuperclass(classDefIndex);
        this.interfaces = dex.classInterfaces(classDefIndex);

        List<Field> fieldList = new ArrayList<>();
        List<Method> methodList = new ArrayList<>();
        int dataOff = dex.classDataOffset(classDefIndex);
        if (dataOff != 0) {
            byte[] d = dex.data();
            int[] cursor = {dataOff};
            int staticFields = DexFile.uleb128(d, cursor);
            int instanceFields = DexFile.uleb128(d, cursor);
            int directMethods = DexFile.uleb128(d, cursor);
            int virtualMethods = DexFile.uleb128(d, cursor);
            readFields(d, cursor, staticFields, fieldList);
            readFields(d, cursor, instanceFields, fieldList);
            readMethods(d, cursor, directMethods, methodList);
            readMethods(d, cursor, virtualMethods, methodList);
        }
        this.fields = Collections.unmodifiableList(fieldList);
        this.methods = Collections.unmodifiableList(methodList);
    }

    private void readFields(byte[] d, int[] cursor, int count, List<Field> out) {
        int index = 0;
        for (int i = 0; i < count; i++) {
            index += DexFile.uleb128(d, cursor);
            int flags = DexFile.uleb128(d, cursor);
            out.add(new Field(this, index, flags));
        }
    }

    private void readMethods(byte[] d, int[] cursor, int count, List<Method> out) {
        int index = 0;
        for (int i = 0; i < count; i++) {
            index += DexFile.uleb128(d, cursor);
            int flags = DexFile.uleb128(d, cursor);
            int codeOff = DexFile.uleb128(d, cursor);
            out.add(new Method(this, index, flags, codeOff));
        }
    }

    /** {@code Lorg/telegram/ui/ChatActivity;} as {@code org.telegram.ui.ChatActivity}. */
    public String javaName() {
        return DexNames.toJavaName(descriptor);
    }

    public boolean isInterface() {
        return (accessFlags & ACC_INTERFACE) != 0;
    }

    public boolean isAbstract() {
        return (accessFlags & ACC_ABSTRACT) != 0;
    }

    public List<Method> methodsNamed(String name) {
        List<Method> result = new ArrayList<>();
        for (Method m : methods) if (m.name().equals(name)) result.add(m);
        return result;
    }

    private Long[] staticInts;

    /** The int-like constant a static field starts with, from the class's static values; or null. */
    public Long initialValue(Field field) {
        if (!field.isStatic()) return null;
        int staticCount = 0;
        for (Field f : fields) if (f.isStatic()) staticCount++;
        if (staticInts == null) staticInts = dex.staticIntValues(classDefIndex, staticCount);
        int i = 0;
        for (Field f : fields) {
            if (!f.isStatic()) continue;
            if (f == field) return i < staticInts.length ? staticInts[i] : null;
            i++;
        }
        return null;
    }

    public Field fieldNamed(String name) {
        for (Field f : fields) if (f.name().equals(name)) return f;
        return null;
    }

    @Override
    public String toString() {
        return javaName();
    }

    // ------------------------------------------------------------------ members

    public static final class Field {
        public final DexClass owner;
        public final int fieldIndex;
        public final int accessFlags;

        Field(DexClass owner, int fieldIndex, int accessFlags) {
            this.owner = owner;
            this.fieldIndex = fieldIndex;
            this.accessFlags = accessFlags;
        }

        public String name() {
            return owner.dex.fieldName(fieldIndex);
        }

        public String type() {
            return owner.dex.fieldType(fieldIndex);
        }

        public boolean isStatic() {
            return (accessFlags & ACC_STATIC) != 0;
        }

        @Override
        public String toString() {
            return owner.javaName() + "." + name() + ":" + type();
        }
    }

    public static final class Method {
        public final DexClass owner;
        public final int methodIndex;
        public final int accessFlags;
        final int codeOffset;

        Method(DexClass owner, int methodIndex, int accessFlags, int codeOffset) {
            this.owner = owner;
            this.methodIndex = methodIndex;
            this.accessFlags = accessFlags;
            this.codeOffset = codeOffset;
        }

        public String name() {
            return owner.dex.methodName(methodIndex);
        }

        public String returnType() {
            return owner.dex.protoReturnType(owner.dex.methodProto(methodIndex));
        }

        public String[] parameterTypes() {
            return owner.dex.protoParameters(owner.dex.methodProto(methodIndex));
        }

        public boolean isStatic() {
            return (accessFlags & ACC_STATIC) != 0;
        }

        public boolean isConstructor() {
            return (accessFlags & ACC_CONSTRUCTOR) != 0;
        }

        public boolean isSynthetic() {
            return (accessFlags & ACC_SYNTHETIC) != 0;
        }

        public boolean hasCode() {
            return codeOffset != 0;
        }

        /** Walks this method's bytecode. Does nothing for abstract and native methods. */
        public void scan(CodeScanner.Visitor visitor) {
            if (codeOffset != 0) CodeScanner.scan(owner.dex, codeOffset, visitor);
        }

        /** The int each static field of its own class is set to here, where constants decide it; by name. */
        public java.util.Map<String, Long> staticIntWrites() {
            java.util.Map<String, Long> out = new java.util.LinkedHashMap<>();
            if (codeOffset == 0) return out;
            for (java.util.Map.Entry<Integer, Long> e : CodeScanner.staticIntWrites(owner.dex, codeOffset).entrySet()) {
                if (owner.dex.fieldClass(e.getKey()).equals(owner.descriptor)) out.put(owner.dex.fieldName(e.getKey()), e.getValue());
            }
            return out;
        }

        /**
         * The constant passed in argument register {@code register} (0 is {@code this} for an
         * instance call) to each call of {@code owner.name} here, in order; null where unknown.
         */
        public java.util.List<Long> constantArguments(final String ownerDescriptor, final String name, final int register) {
            final java.util.List<Long> out = new java.util.ArrayList<>();
            if (codeOffset == 0) return out;
            final DexFile dex = owner.dex;
            CodeScanner.constantFlow(dex, codeOffset, new CodeScanner.ConstantSink() {
                @Override
                public void invoke(int methodIndex, Long[] registers) {
                    if (register < registers.length && dex.methodClass(methodIndex).equals(ownerDescriptor)
                            && dex.methodName(methodIndex).equals(name)) out.add(registers[register]);
                }
            });
            return out;
        }

        public String signature() {
            StringBuilder sb = new StringBuilder(name()).append('(');
            for (String p : parameterTypes()) sb.append(p);
            return sb.append(')').append(returnType()).toString();
        }

        @Override
        public String toString() {
            return owner.javaName() + "#" + signature();
        }
    }
}
