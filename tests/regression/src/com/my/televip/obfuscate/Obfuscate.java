package com.my.televip.obfuscate;
public final class Obfuscate {
    public static String getClassName(String name) { return name; }
    public static String getMethodName(String type,String name) { return name; }
    public static String getFieldName(String type,String name) {
        return type.equals("ChatActivity") && name.equals("currentChat") ? "e" : name;
    }
}
