package com.my.televip.obfuscate.resolve;

import com.my.televip.obfuscate.dex.*;
import java.util.*;

/** Mirrors the feature selectors against actual DEX signatures; unknown shapes fail publication. */
final class FeatureShapeAudit {
    static List<String> check(DexIndex index, Mapping mapping, String packageName) {
        List<String> errors=new ArrayList<>();
        errors.addAll(downloads(index, mapping));
        errors.addAll(stories(index, mapping));
        errors.addAll(upstreamUi(index, mapping));
        errors.addAll(adaptedRoutes(index, mapping));
        if("org.telegram.messenger".equals(packageName))errors.addAll(independent(index,mapping));
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
        if("org.telegram.messenger".equals(packageName)) errors.addAll(phoneDisplay(index, mapping));
        return errors;
    }
    static List<String> independent(DexIndex index,Mapping mapping){
        List<String> errors=new ArrayList<>();
        String[][] fields={
            {"NativeByteBuffer","buffer","Ljava/nio/ByteBuffer;"},
            {"TLRPC$Message","date","I"},{"TLRPC$Message","media",descriptor(mapping,"org.telegram.tgnet.TLRPC$MessageMedia")},
            {"TLRPC$MessageMedia","ttl_seconds","I"},{"BaseFragment","arguments","Landroid/os/Bundle;"},
            {"BaseFragment","currentAccount","I"},{"BaseController","currentAccount","I"},{"MessageObject","currentAccount","I"},
            {"ChatActivity","threadMessageId","J"},{"PhotoViewer","imagesArr","Ljava/util/ArrayList;"},
            {"TLRPC$UserFull","noforwards_peer_enabled","Z"},{"TLRPC$UserFull","noforwards_my_enabled","Z"},
            {"ChatMessagePoll","cell",descriptor(mapping,"org.telegram.ui.Cells.ChatMessageCell")},
            {"ChatMessageCell","totalHeight","I"},{"ChatMessageCell","instantTextNewLine","Z"},
            {"ChatMessageCell","availableTimeWidth","I"},{"ChatMessageCell","backgroundWidth","I"},
            {"TLRPC$TL_channels_joinChannel","channel",descriptor(mapping,"org.telegram.tgnet.TLRPC$InputChannel")},
            {"TLRPC$InputChannel","channel_id","J"},{"TLRPC$Chat","id","J"},{"TLRPC$Chat","left","Z"},
            {"TLRPC$User","bot","Z"},{"TLRPC$TL_messages_startBot","peer",descriptor(mapping,"org.telegram.tgnet.TLRPC$InputPeer")},
            {"TLRPC$TL_messages_sendMessage","message","Ljava/lang/String;"},
            {"TLRPC$Updates","chats","Ljava/util/ArrayList;"},{"TLRPC$TL_chatInviteJoinResultOk","updates",descriptor(mapping,"org.telegram.tgnet.TLRPC$Updates")},
            {"TLRPC$Message","action",descriptor(mapping,"org.telegram.tgnet.TLRPC$MessageAction")},{"TLRPC$MessageAction","users","Ljava/util/ArrayList;"}
        };
        for(String[] f:fields)downloadField(cls(index,mapping,TelegramFingerprints.owners().get(f[0])),mapping.resolveField(f[0],f[1]),f[2],errors);
        if(!exact(index,mapping,"ChatMessagePoll","setMessageContentIfPoll","V",descriptor(mapping,"org.telegram.messenger.MessageObject"),"Z"))errors.add("Invalid poll layout callback");
        if(!exact(index,mapping,"ChatActivity","onFragmentCreate","Z")||!exact(index,mapping,"ChatActivity","onPause","V"))errors.add("Invalid local reading lifecycle");
        if(!exact(index,mapping,"MessagesController","getUserFull",descriptor(mapping,"org.telegram.tgnet.TLRPC$UserFull"),"J"))errors.add("Invalid owning-account user-full lookup");
        if(!exact(index,mapping,"MessagesController","getUser",descriptor(mapping,"org.telegram.tgnet.TLRPC$User"),"Ljava/lang/Long;"))errors.add("Invalid owning-account bot lookup");
        if(!exact(index,mapping,"NotificationsController","muteDialog","V","J","J","Z"))errors.add("Invalid native mute action");
        // These native private methods reorder parameters; the handlers use the published types.
        for(String[] pair:new String[][]{{"MessagesController","updateInterfaceWithMessages"},{"NotificationsController","muteUntil"}})
            if(mapping.resolveMethod(pair[0],pair[1])==null)errors.add("Missing reviewed reordered route: "+Arrays.toString(pair));
        return errors;
    }
    static List<String> adaptedRoutes(DexIndex index, Mapping mapping) {
        List<String> errors = new ArrayList<>();
        DexClass profile = cls(index, mapping, "org.telegram.ui.ProfileActivity");
        String array = mapping.resolveField("ProfileActivity", "onlineTextView");
        String modern = mapping.resolveField("ProfileActivity", "statusTextView");
        DexClass.Field old = profile == null || array == null ? null : profile.fieldNamed(array);
        DexClass.Field current = profile == null || modern == null ? null : profile.fieldNamed(modern);
        boolean legacy = old != null && old.type().equals("[" + descriptor(mapping, "org.telegram.ui.ActionBar.SimpleTextView"));
        boolean next = current != null && subtype(index, current.type(), descriptor(mapping, "org.telegram.ui.ActionBar.SimpleTextView"), new HashSet<>())
                && exact(index,mapping,"ProfileActivity","updateStatusText","V")
                && exact(index,mapping,"ProfileStatusTextView","setText","Z","Ljava/lang/CharSequence;","Z");
        if (!legacy && !next) errors.add("Missing validated profile status route");
        String titleArray = mapping.resolveField("ProfileActivity", "nameTextView");
        String titleSingle = mapping.resolveField("ProfileActivity", "nameTextViewSingle");
        DexClass.Field title = profile == null ? null : titleSingle == null ? null : profile.fieldNamed(titleSingle);
        DexClass.Field titles = profile == null ? null : titleArray == null ? null : profile.fieldNamed(titleArray);
        if (!(titles != null && titles.type().equals("[" + descriptor(mapping,"org.telegram.ui.ActionBar.SimpleTextView")))
                && !(title != null && subtype(index,title.type(),descriptor(mapping,"org.telegram.ui.ActionBar.SimpleTextView"),new HashSet<>())))
            errors.add("Missing validated profile name route");
        if (!exact(index,mapping,"SimpleTextView","setTextCZ","Z","Ljava/lang/CharSequence;","Z")) errors.add("Missing forced profile text setter");
        if (!exact(index,mapping,"MessagesController","processNewDifferenceParams","V","I","I","I","I")
                && !exact(index,mapping,"MessagesController","processNewDifferenceParamsIII","V","I","I","I")) errors.add("Missing difference update route");
        if (!exact(index,mapping,"MessageObject","needDrawBluredPreview","Z")) errors.add("Missing secret preview predicate");
        DexClass shared = cls(index,mapping,"org.telegram.messenger.SharedConfig");
        String field = mapping.resolveField("SharedConfig","pendingAppUpdate");
        DexClass.Field update = shared == null || field == null ? null : shared.fieldNamed(field);
        boolean pending = update != null && update.isStatic()
                && update.type().equals("Lorg/telegram/tgnet/TLRPC$TL_help_appUpdate;")
                && exact(index,mapping,"SharedConfig","loadConfig","V");
        boolean oldUpdate = exact(index,mapping,"SharedConfig","isAppUpdateAvailable","Z")
                && (exact(index,mapping,"SharedConfig","setNewAppVersionAvailable","Z","Lorg/telegram/tgnet/TLRPC$TL_help_appUpdate;")
                    || exact(index,mapping,"SharedConfig","setNewAppVersionAvailable","V","Lorg/telegram/tgnet/TLRPC$TL_help_appUpdate;"));
        if (!pending && !oldUpdate) errors.add("Missing update suppression route");
        return errors;
    }
    private static boolean exact(DexIndex index, Mapping mapping, String owner, String key, String result, String... parameters) {
        String full = TelegramFingerprints.owners().get(owner);
        DexClass type = full == null ? null : cls(index,mapping,full);
        String name = mapping.resolveMethod(owner,key);
        int count = 0;
        if (type != null && name != null) for (DexClass.Method m : type.methods)
            if (m.name().equals(name) && m.returnType().equals(result) && Arrays.equals(m.parameterTypes(),parameters)) count++;
        return count == 1;
    }
    static List<String> upstreamUi(DexIndex index, Mapping mapping) {
        List<String> errors = new ArrayList<>();
        DexClass dialogs = cls(index, mapping, "org.telegram.ui.DialogsActivity");
        String update = mapping.resolveMethod("DialogsActivity", "updateProxyButton");
        int updates = 0;
        if (dialogs != null && update != null) for (DexClass.Method method : dialogs.methods) {
            final boolean[] proxyKey = {false}, alpha = {false};
            method.scan(new CodeScanner.Visitor() {
                @Override public void string(int target) {
                    if (dialogs.dex.string(target).equals("proxy_enabled")) proxyKey[0] = true;
                }
                @Override public void invoke(int opcode, int target) {
                    if (dialogs.dex.methodName(target).equals("getAlpha")) alpha[0] = true;
                }
            });
            if (method.name().equals(update) && !method.isStatic() && method.returnType().equals("V")
                    && Arrays.equals(method.parameterTypes(), new String[]{"Z", "Z"}) && proxyKey[0] && alpha[0]) updates++;
        }
        if (updates != 1) errors.add("Invalid Downloads visibility update scope");
        DexClass text = cls(index, mapping, "org.telegram.ui.ActionBar.SimpleTextView");
        for (String key : new String[]{"getPaint", "getExactWidth", "setEllipsizeByGradient"}) {
            String name = mapping.resolveMethod("SimpleTextView", key);
            int methods = 0;
            if (text != null && name != null) for (DexClass.Method method : text.methods)
                if (method.name().equals(name) && !method.isStatic()
                        && Arrays.equals(method.parameterTypes(), key.equals("setEllipsizeByGradient") ? new String[]{"Z"} : new String[0])
                        && method.returnType().equals(key.equals("getPaint") ? "Landroid/text/TextPaint;" : key.equals("getExactWidth") ? "F" : "V")) methods++;
            if (methods != 1) errors.add("Invalid profile text metric: " + key);
        }
        return errors;
    }
    static List<String> phoneDisplay(DexIndex index, Mapping mapping) {
        List<String> errors = new ArrayList<>();
        // HidePhone needs these concrete scopes even if a settings callback alternative exists.
        for(String owner:new String[]{"SettingsActivity","UserInfoActivity"}) {
            DexClass scope=cls(index,mapping,"org.telegram.ui."+owner);
            String fill=mapping.resolveMethod(owner,"fillItems");int fills=0;
            if(scope!=null && fill!=null) for(DexClass.Method m:scope.methods) if(m.name().equals(fill)
                    && m.returnType().equals("V") && Arrays.asList(m.parameterTypes()).contains("Ljava/util/ArrayList;")
                    && (!m.isStatic() || m.parameterTypes().length>0 && m.parameterTypes()[0].equals(scope.descriptor))) fills++;
            if(fills!=1) errors.add("Invalid phone display scope: "+owner);
        }
        DexClass adapter = cls(index, mapping, "org.telegram.ui.ProfileActivity$ListAdapter");
        String ownerName = mapping.resolveField("ProfileActivity$ListAdapter", "profileActivity");
        DexClass.Field owner = adapter == null || ownerName == null ? null : adapter.fieldNamed(ownerName);
        if (owner == null || owner.isStatic() || !owner.type().equals(descriptor(mapping, "org.telegram.ui.ProfileActivity")))
            errors.add("Invalid phone display profile owner");
        String bind = mapping.resolveMethod("ProfileActivity$ListAdapter", "onBindViewHolder");
        int binders = 0;
        if (adapter != null && bind != null) for (DexClass.Method method : adapter.methods) {
            String[] params = method.parameterTypes();
            if (method.name().equals(bind) && !method.isStatic() && method.returnType().equals("V")
                    && params.length == 2 && params[0].startsWith("L") && params[1].equals("I")
                    && phoneFormatterCall(method, mapping)) binders++;
        }
        if (binders != 1) errors.add("Invalid phone display profile row binder");
        DexClass help = cls(index, mapping, "org.telegram.ui.ChangePhoneHelpActivity");
        String create = mapping.resolveMethod("ChangePhoneHelpActivity", "createView");
        int views = 0;
        if (help != null && create != null) for (DexClass.Method method : help.methods) {
            final boolean[] keepLabel = {false};
            method.scan(new CodeScanner.Visitor() {
                @Override public void string(int target) {
                    if (help.dex.string(target).equals("PhoneNumberKeepButton")) keepLabel[0] = true;
                }
            });
            if (method.name().equals(create) && !method.isStatic() && method.returnType().equals("Landroid/view/View;")
                    && Arrays.equals(method.parameterTypes(), new String[]{"Landroid/content/Context;"})
                    && keepLabel[0] && phoneFormatterCall(method, mapping)) views++;
        }
        if (views != 1) errors.add("Invalid phone display change-number introduction");
        return errors;
    }
    private static boolean phoneFormatterCall(DexClass.Method method, Mapping mapping) {
        final boolean[] found = {false};
        String formatter = descriptor(mapping, "org.telegram.PhoneFormat.PhoneFormat");
        method.scan(new CodeScanner.Visitor() {
            @Override public void invoke(int opcode, int target) {
                DexFile dex = method.owner.dex;
                if (!dex.methodClass(target).equals(formatter)) return;
                int proto = dex.methodProto(target);
                String[] params = dex.protoParameters(proto);
                // R8 outlines format(String), retaining getInstance() in the UI caller.
                if (params.length == 0 && dex.protoReturnType(proto).equals(formatter)
                        || Arrays.equals(params, new String[]{"Ljava/lang/String;"})
                        && dex.protoReturnType(proto).equals("Ljava/lang/String;")) found[0] = true;
            }
        });
        return found[0];
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
