package com.my.televip.obfuscate.resolve;

import com.my.televip.obfuscate.dex.*;
import java.util.*;

/** Mirrors the feature selectors against actual DEX signatures; unknown shapes fail publication. */
final class FeatureShapeAudit {
    static List<String> check(DexIndex index, Mapping mapping, String packageName) {
        List<String> errors=new ArrayList<>();
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
