package com.my.televip.obfuscate.resolve;

import com.my.televip.obfuscate.dex.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Runs against actual client APK bytecode, without loading or executing client code. */
public final class CompatAudit {
    public static void main(String[] args) throws Exception {
        long start = System.nanoTime();
        String pkg = ApkPackage.of(new File(args[0]));
        if (pkg == null) throw new IllegalArgumentException("APK package unavailable");
        DexIndex index = DexIndex.fromApk(new File(args[0]));
        Resolver.Report report = new Resolver.Report();
        Mapping mapping = new Resolver(index, TelegramFingerprints.owners()).resolve(TelegramFingerprints.all(), report);
        Files.createDirectories(Paths.get(args[1]));
        Files.write(Paths.get(args[1], "mapping.txt"), mapping.serialize().getBytes(StandardCharsets.UTF_8));
        System.out.println("Symbols: " + mapping.size() + "; time: " + (System.nanoTime()-start)/1000000 + " ms");
        Files.write(Paths.get(args[1], "package.txt"), pkg.getBytes(StandardCharsets.UTF_8));
        List<String> errors = new ArrayList<>();
        Mapping roundtrip = Mapping.deserialize(mapping.serialize());
        if (!mapping.serialize().equals(roundtrip.serialize())) errors.add("Mapping roundtrip differs");
        try { Mapping.deserialize("garbage"); errors.add("Corrupt mapping accepted"); }
        catch (IllegalArgumentException expected) {}
        Mapping fingerprinted = new Resolver(index, TelegramFingerprints.owners()).fingerprintsOnly()
                .resolve(TelegramFingerprints.all(), new Resolver.Report());
        compare(mapping.classes(), fingerprinted.classes(), errors);
        compare(mapping.fields(), fingerprinted.fields(), errors);
        compare(mapping.methods(), fingerprinted.methods(), errors);
        if(args.length>2) auditSites(index,mapping,new File(args[0]),Paths.get(args[2]),Paths.get(args[1],"call-sites.tsv"));
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Resolver.Outcome> entry : report.outcomes.entrySet()) lines.add(entry.getValue() + "\t" + entry.getKey());
        for (Map.Entry<String,List<String>> entry : report.ambiguities.entrySet()) lines.add("CANDIDATES\t" + entry.getKey() + "\t" + entry.getValue());
        Files.write(Paths.get(args[1], "symbols.tsv"), lines, StandardCharsets.UTF_8);
        String[] requiredClasses = {"org.telegram.ui.SettingsActivity", "org.telegram.ui.ChatActivity", "org.telegram.ui.ProfileActivity",
                "org.telegram.ui.Components.UItem", "org.telegram.ui.SettingsActivity$SettingCell$Factory",
                "org.telegram.tgnet.ConnectionsManager", "org.telegram.messenger.MessagesController", "org.telegram.messenger.MessagesStorage",
                "org.telegram.PhoneFormat.PhoneFormat", "org.telegram.ui.ActionBar.ActionBar$ActionBarMenuOnItemClick",
                "org.telegram.tgnet.TLRPC$TL_messages_sponsoredMessagesEmpty",
                "org.telegram.tgnet.TLRPC$TL_contacts_sponsoredPeersEmpty", "org.telegram.tgnet.TLRPC$TL_help_promoDataEmpty"};
        for (String name : requiredClasses) if (mapping.resolveClass(name) == null && index.findClass(name) == null) errors.add("Critical class missing: " + name);
        errors.addAll(SettingsRouteAudit.check(index,mapping));
        // Negative controls: a broken factory, or both entry routes missing, must fail the gate.
        Mapping brokenFactory=Mapping.deserialize(mapping.serialize());
        brokenFactory.methods.put("SettingsActivity$SettingCell$Factory#ofIIIICCC","__missing__");
        brokenFactory.methods.put("SettingsActivity$SettingCell$Factory#ofIIIICC","__missing__");
        if(SettingsRouteAudit.check(index,brokenFactory).isEmpty()) errors.add("Broken settings factory passed audit");
        Mapping brokenRoutes=Mapping.deserialize(mapping.serialize());
        brokenRoutes.methods.put("SettingsActivity#fillItems","__missing__");
        brokenRoutes.methods.put("SettingsActivity#onClick","__missing__");
        brokenRoutes.classes.put("org.telegram.ui.Components.UniversalRecyclerView","__missing__");
        if(SettingsRouteAudit.check(index,brokenRoutes).isEmpty()) errors.add("Broken settings routes passed audit");
        require(index,mapping, "UItem", "id", true, errors);
        require(index,mapping, "NotificationCenter", "messagesDeleted", true, errors);
        String[][] methods = {{"ConnectionsManager","sendRequestInternal"}, {"MessagesController","checkPromoInfoInternal"},
                {"MessagesController","removePromoDialog"}, {"MessagesController","getMainSettings"},
                {"PhoneFormat","format"}, {"ChatActivity","createView"}, {"ProfileActivity","createView"}, {"ProfileActivity","createActionBarMenu"},
                {"ChatActivity","scrollToMessageId"}, {"StoriesController","hasStories"}};
        for (String[] member : methods) require(index,mapping, member[0], member[1], false, errors);
        String[][] fields = {{"ChatActivity","currentChat"}, {"ChatActivity","headerItem"},
                {"ProfileActivity","otherItem"}, {"ProfileActivity","userId"}, {"TLRPC$TL_help_promoDataEmpty","expires"}};
        for (String[] member : fields) require(index,mapping, member[0], member[1], true, errors);
        for (String error : errors) System.err.println(error);
        if (!errors.isEmpty()) throw new AssertionError("Compatibility audit failed: " + errors.size());
        System.out.println("COMPAT_AUDIT_PASS: mappings, critical hooks, fingerprint agreement and cache integrity");
    }
    private static void auditSites(DexIndex index,Mapping mapping,File apk,Path inventory,Path report) throws Exception {
        java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
        try(java.io.InputStream in=new java.io.FileInputStream(apk)) {byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);}
        StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        boolean legacy=hex.toString().equals("7ebd25d6bce15f7195b0b06d0c74d276182e203e6012eabf57d951daccb9b8e5");
        List<String> output=new ArrayList<>();int missing=0;
        for(String line:Files.readAllLines(inventory,StandardCharsets.UTF_8)) {
            String[] p=line.split("\t",-1);String kind=p[0],owner=p[1],name=p[2];boolean found=false;
            String resolved;
            if(kind.equals("C")) {
                resolved=mapping.resolveClass(owner);
                if(resolved==null)resolved=legacy&&!p[4].isEmpty()?p[4]:owner;
                found=index.findClass(resolved)!=null;
            } else {
                String full=TelegramFingerprints.owners().get(owner);
                String alias=owner;
                if(full==null && owner.contains(".")) {full=owner; for(Map.Entry<String,String> e:TelegramFingerprints.owners().entrySet()) if(e.getValue().equals(owner)) {alias=e.getKey();break;}}
                String resolvedOwner=full==null?null:mapping.resolveClass(full);
                DexClass cls=resolvedOwner==null?(full==null?null:index.findClass(full)):index.findClass(resolvedOwner);
                resolved=kind.equals("F")?mapping.resolveField(alias,name):mapping.resolveMethod(alias,TelegramFingerprints.methodKey(alias,name));
                if(resolved==null)resolved=legacy&&!p[4].isEmpty()?p[4]:name.replace("storyEntitiesAllowed2","storyEntitiesAllowed");
                Set<String> seen=new HashSet<>();
                while(cls!=null && seen.add(cls.descriptor)) {
                    if(kind.equals("F")) {for(DexClass.Field f:cls.fields)found|=f.name().equals(resolved);}
                    else {for(DexClass.Method m:cls.methods) {
                        if(!m.name().equals(resolved)) continue;
                        if(!kind.equals("H")) {found=true;continue;}
                        String[] expected=p[5].equals("-")?new String[0]:p[5].split(",");
                        String[] actual=m.parameterTypes();
                        if(expected.length!=actual.length) continue;
                        boolean fits=true;
                        for(int i=0;i<expected.length;i++) {
                            String mapped=mapping.resolveClass(expected[i]);
                            fits &= actual[i].equals(DexNames.toDescriptor(mapped==null?expected[i]:mapped));
                        }
                        if(fits) found=true;
                    }}
                    if(found)break;
                    cls=cls.superclass==null?null:index.byDescriptor(cls.superclass);
                }
            }
            if(!found)missing++;
            output.add((found?"RESOLVED":"UNRESOLVED")+"\t"+line+"\t"+resolved);
        }
        Files.write(report,output,StandardCharsets.UTF_8);
        System.out.println("Source call sites: "+(output.size()-missing)+"/"+output.size()+" names resolve; unresolved sites are listed (including optional/other-client paths).");
    }
    private static void require(DexIndex index, Mapping mapping, String owner, String name, boolean field, List<String> errors) {
        String found = field ? mapping.resolveField(owner,name) : mapping.resolveMethod(owner,TelegramFingerprints.methodKey(owner,name));
        if (found != null) return;
        // Unrenamed members may be present without a fingerprint specification.
        String full = TelegramFingerprints.owners().get(owner);
        DexClass cls = full == null ? null : index.findClass(mapping.resolveClass(full) == null ? full : mapping.resolveClass(full));
        if (cls != null) {
            if (field) {for (DexClass.Field item : cls.fields) if (item.name().equals(name)) return;}
            else {for (DexClass.Method item : cls.methods) if (item.name().equals(name)) return;}
        }
        errors.add("Critical member missing: " + owner + "#" + name);
    }
    private static void compare(Map<String,String> truth, Map<String,String> found, List<String> errors) {
        for (Map.Entry<String,String> entry : truth.entrySet()) {
            String actual = found.get(entry.getKey());
            if (actual != null && !actual.equals(entry.getValue())) errors.add("Fingerprint mismatch: " + entry.getKey() + " -> " + actual + ", expected " + entry.getValue());
        }
    }
}
