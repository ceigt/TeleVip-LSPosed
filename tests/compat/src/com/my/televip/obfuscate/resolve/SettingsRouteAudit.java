package com.my.televip.obfuscate.resolve;

import com.my.televip.obfuscate.dex.*;
import java.util.*;

/** Mirrors NativeSettingsEntry's supported routes, including its constructor callback fallback. */
final class SettingsRouteAudit {
    static DexClass cls(DexIndex index, Mapping map, String full) {
        String name = map.resolveClass(full);
        return index.findClass(name == null ? full : name);
    }
    static String name(Mapping map, String owner, String member) {
        String resolved = map.resolveMethod(owner, TelegramFingerprints.methodKey(owner, member));
        return resolved == null ? member : resolved;
    }
    static int unique(DexClass cls, String name, String argument) {
        if (cls == null) return 0;
        int count = 0;
        for (DexClass.Method m : cls.methods) if (m.name().equals(name) && m.returnType().equals("V")
                && Arrays.asList(m.parameterTypes()).contains(argument)) count++;
        return count;
    }
    static boolean assignable(DexIndex index, String supertype, DexClass child) {
        Set<String> seen = new HashSet<>();
        while (child != null && seen.add(child.descriptor)) {
            if (child.descriptor.equals(supertype)) return true;
            child = child.superclass == null ? null : index.byDescriptor(child.superclass);
        }
        return false;
    }
    static boolean callback(DexIndex index, DexClass recycler, DexClass settings) {
        if (recycler == null || settings == null) return false;
        for (DexClass.Method m : recycler.methods) {
            String[] p = m.parameterTypes();
            if (!m.isConstructor() || p.length != 4 || !assignable(index, p[0], settings)) continue;
            DexClass fill = index.byDescriptor(p[1]), click = index.byDescriptor(p[2]);
            if (fill != null && fill.isInterface() && click != null && click.isInterface()) return true;
        }
        return false;
    }
    static List<String> check(DexIndex index, Mapping map) {
        List<String> errors = new ArrayList<>();
        DexClass settings = cls(index,map,"org.telegram.ui.SettingsActivity");
        DexClass factory = cls(index,map,"org.telegram.ui.SettingsActivity$SettingCell$Factory");
        DexClass row = cls(index,map,"org.telegram.ui.Components.UItem");
        if (settings == null || factory == null || row == null) {errors.add("Native settings classes unavailable");return errors;}
        boolean creates = false;
        for (int count : new int[]{7,6}) {
            int matches = 0;
            String name = name(map,"SettingsActivity$SettingCell$Factory",count == 7 ? "ofIIIICCC" : "of");
            for (DexClass.Method m : factory.methods) {
                String[] p = m.parameterTypes();
                if (!m.name().equals(name) || !m.isStatic() || !m.returnType().equals(row.descriptor) || p.length != count) continue;
                boolean fits = true;
                for (int i=0;i<count;i++) fits &= i<4 ? p[i].equals("I") : p[i].equals("Ljava/lang/CharSequence;") || p[i].equals("Ljava/lang/String;");
                if (fits) matches++;
            }
            if (matches > 1) {errors.add("Ambiguous native settings row factory");break;}
            if (matches == 1) {creates = true;break;}
        }
        if (!creates) errors.add("No supported native settings row factory");
        boolean direct = unique(settings,name(map,"SettingsActivity","fillItems"),"Ljava/util/ArrayList;") == 1
                && unique(settings,name(map,"SettingsActivity","onClick"),row.descriptor) == 1;
        boolean fallback = callback(index,cls(index,map,"org.telegram.ui.Components.UniversalRecyclerView"),settings);
        if (!direct && !fallback) errors.add("Native settings has neither a unique fill/click route nor a constructor callback route");
        return errors;
    }
}
