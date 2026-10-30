package com.my.televip.obfuscate.resolve;

import com.my.televip.obfuscate.dex.*;
import java.util.*;

/** Mirrors the feature selectors against actual DEX signatures; unknown shapes fail publication. */
final class FeatureShapeAudit {
    static List<String> check(DexIndex index, Mapping mapping, String packageName) {
        List<String> errors=new ArrayList<>();
        errors.addAll(downloads(index, mapping));
        errors.addAll(stories(index, mapping));
        DexClass chat=cls(index,mapping,"org.telegram.ui.ChatActivity");
        String mo=descriptor(mapping,"org.telegram.messenger.MessageObject");
        scheduler(index,chat,mapping.resolveMethod("ChatActivity","sendSecretMessageRead"),false,new String[]{mo,"Z"},errors);
        scheduler(index,chat,mapping.resolveMethod("ChatActivity","sendSecretMediaDelete"),true,new String[]{mo},errors);
        DexClass viewer=cls(index,mapping,"org.telegram.ui.SecretMediaViewer");
        String callback=mapping.resolveField("SecretMediaViewer","onClose");
        DexClass.Field field=viewer==null||callback==null?null:viewer.fieldNamed(callback);
        if(field==null || field.isStatic() || !field.type().equals("Ljava/lang/Runnable;")) errors.add("Invalid secret close callback");
        String stream=descriptor(mapping,"org.telegram.tgnet.InputSerializedData"), tl=descriptor(mapping,"org.telegram.tgnet.TLObject");
        String[] requests={"TL_messages_getSponsoredMessages","TL_contacts_getSponsoredPeers"};
        String[] responses={"TL_messages_sponsoredMessagesEmpty","TL_contacts_sponsoredPeersEmpty"};
        for(int i=0;i<requests.length;i++) {
            DexClass request=cls(index,mapping,"org.telegram.tgnet.TLRPC$"+requests[i]);
            DexClass response=cls(index,mapping,"org.telegram.tgnet.TLRPC$"+responses[i]);
            int readers=0,constructors=0;
            if(request!=null && response!=null) {
                for(DexClass.Method m:request.methods) if(m.name().equals("deserializeResponse") && !m.isStatic()
                        && (m.accessFlags&0x40)==0 && Arrays.equals(m.parameterTypes(),new String[]{stream,"I","Z"})
                        && subtype(index,m.returnType(),tl,new HashSet<>())
                        && subtype(index,response.descriptor,m.returnType(),new HashSet<>())) readers++;
                for(DexClass.Method m:response.methods) if(m.isConstructor() && !m.isStatic() && m.parameterTypes().length==0) constructors++;
            }
            if(readers!=1 || constructors!=1) errors.add("Invalid ad response reader/constructor: "+requests[i]);
        }
        DexClass controller=cls(index,mapping,"org.telegram.messenger.MessagesController");
        String cache=mapping.resolveMethod("MessagesController","getSponsoredMessages");int count=0;
        if(controller!=null) for(DexClass.Method m:controller.methods) if(m.name().equals(cache) && !m.isStatic()
                && Arrays.equals(m.parameterTypes(),new String[]{"J"}) && m.returnType().startsWith("L")) count++;
        if(count!=1) errors.add("Invalid sponsored cache getter");
        // HidePhone needs these concrete display scopes even if a settings callback alternative exists.
        if("org.telegram.messenger".equals(packageName)) for(String owner:new String[]{"SettingsActivity","UserInfoActivity"}) {
            DexClass scope=cls(index,mapping,"org.telegram.ui."+owner);
            String fill=mapping.resolveMethod(owner,"fillItems");int fills=0;
            if(scope!=null && fill!=null) for(DexClass.Method m:scope.methods) if(m.name().equals(fill)
                    && m.returnType().equals("V") && Arrays.asList(m.parameterTypes()).contains("Ljava/util/ArrayList;")
                    && (!m.isStatic() || m.parameterTypes().length>0 && m.parameterTypes()[0].equals(scope.descriptor))) fills++;
            if(fills!=1) errors.add("Invalid phone display scope: "+owner);
        }
        return errors;
    }
    static List<String> downloads(DexIndex index, Mapping mapping) {
        List<String> errors = new ArrayList<>();
        DexClass operation = cls(index, mapping, "org.telegram.messenger.FileLoadOperation");
        DexClass config = cls(index, mapping, "org.telegram.messenger.UserConfig");
        DexClass user = cls(index, mapping, "org.telegram.tgnet.TLRPC$User");
        String start = mapping.resolveMethod("FileLoadOperation", "startSJZ");
        String premium = mapping.resolveMethod("UserConfig", "isPremium");
        String helper = mapping.resolveMethod("FileLoadOperation", "updateDownloadByteBudget");
        if (helper == null) helper = "updateDownloadByteBudget";
        int helpers = 0, policies = 0, callers = 0, protectedCallers = 0;
        if (operation != null) for (DexClass.Method method : operation.methods) if (method.name().equals(helper)) helpers++;
        if (operation != null && config != null && premium != null) {
            for (DexClass.Method method : operation.methods) {
                String[] p = method.parameterTypes();
                boolean selected = !method.isStatic() && (helpers > 0
                        ? helpers == 1 && method.name().equals(helper) && method.returnType().equals("V") && p.length == 0
                        : method.name().equals(start) && method.returnType().equals("Z") && p.length == 3
                                && p[0].startsWith("L") && p[1].equals("J") && p[2].equals("Z"));
                if (selected) policies++;
                final boolean[] callsPremium = {false};
                method.scan(new CodeScanner.Visitor() {
                    @Override public void invoke(int opcode, int target) {
                        DexFile dex = method.owner.dex;
                        if (dex.methodClass(target).equals(config.descriptor) && dex.methodName(target).equals(premium)
                                && dex.protoReturnType(dex.methodProto(target)).equals("Z")
                                && dex.protoParameters(dex.methodProto(target)).length == 0) callsPremium[0] = true;
                    }
                });
                if (callsPremium[0]) { callers++; if (selected) protectedCallers++; }
            }
        }
        if (policies != 1 || callers == 0 || protectedCallers != callers) errors.add("Invalid real-account download policy scope");
        downloadField(operation, mapping.resolveField("FileLoadOperation", "currentAccount"), "I", errors);
        downloadField(config, mapping.resolveField("UserConfig", "currentUser"), descriptor(mapping, "org.telegram.tgnet.TLRPC$User"), errors);
        downloadField(user, mapping.resolveField("TLRPC$User", "premium"), "Z", errors);
        return errors;
    }
    static List<String> stories(DexIndex index, Mapping mapping) {
        List<String> errors = new ArrayList<>();
        DexClass owner = cls(index,mapping,"org.telegram.ui.Stories.StoriesController");
        String visible=mapping.resolveField("StoriesController","dialogListStories");
        String hidden=mapping.resolveField("StoriesController","hiddenListStories");
        List<String> writes=new ArrayList<>(); int constructors=0;
        if(owner!=null) for(DexClass.Method method:owner.methods) if(method.name().equals("<init>")
                && Arrays.equals(method.parameterTypes(),new String[]{"I"})) {
            constructors++;
            method.scan(new CodeScanner.Visitor() {
                @Override public void field(int opcode,int target,boolean write) {
                    DexFile dex=owner.dex;
                    if(write && dex.fieldClass(target).equals(owner.descriptor) && dex.fieldType(target).equals("Ljava/util/ArrayList;")) {
                        String name=dex.fieldName(target);if(!writes.contains(name))writes.add(name);
                    }
                }
            });
        }
        if(constructors!=1 || writes.size()<2 || !writes.get(0).equals(visible) || !writes.get(1).equals(hidden))
            errors.add("Invalid story presentation-list constructor/fields");
        for(String name:new String[]{visible,hidden}) {
            DexClass.Field field=owner==null || name==null ? null : owner.fieldNamed(name);
            if(field==null || field.isStatic() || !field.type().equals("Ljava/util/ArrayList;")) errors.add("Invalid mutable story list field: "+name);
        }
        DexClass peer=cls(index,mapping,"org.telegram.tgnet.tl.TL_stories$PeerStories");
        if(peer==null) errors.add("Missing PeerStories type for story filter");
        return errors;
    }
    private static void downloadField(DexClass owner, String name, String type, List<String> errors) {
        DexClass.Field field = owner == null || name == null ? null : owner.fieldNamed(name);
        if (field == null || field.isStatic() || !field.type().equals(type)) errors.add("Invalid download account field: " + name);
    }
    private static void scheduler(DexIndex index,DexClass owner,String name,boolean staticized,String[] parameters,List<String> errors) {
        int count=0;
        if(owner!=null && name!=null) for(DexClass.Method m:owner.methods) {
            if(!m.name().equals(name) || !subtype(index,m.returnType(),"Ljava/lang/Runnable;",new HashSet<>())) continue;
            String[] actual=m.parameterTypes();
            if(staticized && m.isStatic() && actual.length==parameters.length+1 && actual[0].equals(owner.descriptor))
                actual=Arrays.copyOfRange(actual,1,actual.length);
            else if(m.isStatic()) continue;
            if(Arrays.equals(actual,parameters)) count++;
        }
        if(count!=1) errors.add("Invalid secret scheduler: "+name);
    }
    private static DexClass cls(DexIndex index,Mapping mapping,String source) {
        String resolved=mapping.resolveClass(source);return index.findClass(resolved==null?source:resolved);
    }
    private static String descriptor(Mapping mapping,String source) {
        String resolved=mapping.resolveClass(source);return DexNames.toDescriptor(resolved==null?source:resolved);
    }
    private static boolean subtype(DexIndex index,String type,String parent,Set<String> seen) {
        if(type.equals(parent)) return true;
        if(!seen.add(type)) return false;
        DexClass c=index.byDescriptor(type);if(c==null)return false;
        for(String iface:c.interfaces) if(subtype(index,iface,parent,seen))return true;
        return c.superclass!=null && subtype(index,c.superclass,parent,seen);
    }
}
