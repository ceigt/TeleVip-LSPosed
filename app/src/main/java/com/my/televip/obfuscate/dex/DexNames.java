package com.my.televip.obfuscate.dex;

/** Conversions between dex type descriptors and Java class names. */
public final class DexNames {

    private DexNames() {
    }

    /** {@code Lorg/x/Y$Z;} to {@code org.x.Y$Z}. Primitives and arrays are returned unchanged. */
    public static String toJavaName(String descriptor) {
        if (descriptor == null) return null;
        if (descriptor.length() > 2 && descriptor.charAt(0) == 'L'
                && descriptor.charAt(descriptor.length() - 1) == ';') {
            return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
        }
        return descriptor;
    }

    /** {@code org.x.Y$Z} to {@code Lorg/x/Y$Z;}. */
    public static String toDescriptor(String javaName) {
        switch (javaName) {
            case "void": return "V";
            case "boolean": return "Z";
            case "byte": return "B";
            case "short": return "S";
            case "char": return "C";
            case "int": return "I";
            case "long": return "J";
            case "float": return "F";
            case "double": return "D";
            default:
                if (javaName.endsWith("[]")) {
                    return "[" + toDescriptor(javaName.substring(0, javaName.length() - 2));
                }
                return "L" + javaName.replace('.', '/') + ";";
        }
    }
}
