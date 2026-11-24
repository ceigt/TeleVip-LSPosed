package com.my.televip.obfuscate.resolve;

import com.my.televip.obfuscate.dex.*;
import java.io.File;
import java.util.*;

/** Required real-DEX targets for the independently implemented Telegram extras. */
public final class TelegramExtrasAudit {
    private static final String[][] METHODS = {
        {"TL_account$initPasskeyLogin", "serializeToStream", "V", "@OutputSerializedData"},
        {"UserCell", "getCurrentObject", "Ljava/lang/Object;"},
        {"UserCell", "update", "V", "I"},
        {"SendMessagesHelper", "sendForward", "I", "Ljava/util/ArrayList;", "J", "Z", "Z", "Z", "I", "I", "@MessageObject", "I", "J", "J", "*"},
        {"TranslateController", "chatEnabled", "Z"},
        {"TranslateController", "contextEnabled", "Z"},
        {"TranslateController", "featureAvailable", "Z"},
        {"TranslateController", "dialogFeatureAvailable", "Z", "J"},
        {"TranslateController", "pushToTranslate", "V", "@MessageObject", "Ljava/lang/String;", "*"},
        {"ConnectionsManager", "sendRequest", "I", "@TLObject", "@RequestDelegate"},
        {"ConnectionsManager", "cancelRequest", "V", "I", "Z"},
        {"ConnectionsManager", "getCurrentDatacenterId", "I"},
        {"BaseFragment", "getMessagesController", "@MessagesController"},
        {"BaseFragment", "getConnectionsManager", "@ConnectionsManager"},
        {"BuildVars", "useInvoiceBilling", "Z"},
        {"SmsFeeView", "setParams", "V", "Landroid/os/Bundle;", "Z"},
        {"TLRPC$TL_auth_sendCode", "serializeToStream", "V", "@OutputSerializedData"},
        {"TLRPC$TL_codeSettings", "serializeToStream", "V", "@OutputSerializedData"},
        {"TLRPC$TL_auth_sentCodeTypeFirebaseSms", "readParams", "V", "@InputSerializedData", "Z"}
    };
    private static final String[][] FIELDS = {
        {"TL_account$initPasskeyLogin", "api_id", "I"}, {"TL_account$initPasskeyLogin", "api_hash", "Ljava/lang/String;"},
        {"TLRPC$TL_error", "code", "I"}, {"TLRPC$TL_error", "text", "Ljava/lang/String;"},
        {"TLRPC$User", "mutual_contact", "Z"},
        {"TLRPC$User", "photo", "@TLRPC$UserProfilePhoto"},
        {"TLRPC$UserProfilePhoto", "dc_id", "I"},
        {"BuildVars", "APP_ID", "I"}, {"BuildVars", "APP_HASH", "Ljava/lang/String;"},
        {"BuildVars", "SAFETYNET_KEY", "Ljava/lang/String;"}, {"BuildVars", "SUPPORTS_PASSKEYS", "Z"},
        {"BaseController", "currentAccount", "I"},
        {"TLRPC$TL_codeSettings", "allow_firebase", "Z"},
        {"TLRPC$auth_SentCodeType", "verifiedFirebase", "Z"},
        {"TLRPC$TL_auth_sendCode", "api_id", "I"}, {"TLRPC$TL_auth_sendCode", "api_hash", "Ljava/lang/String;"},
        {"TLRPC$TL_messages_translateText", "peer", "@TLRPC$InputPeer"},
        {"TLRPC$TL_messages_translateText", "id", "Ljava/util/ArrayList;"},
        {"TLRPC$TL_messages_translateText", "text", "Ljava/util/ArrayList;"},
        {"TLRPC$TL_messages_translateText", "to_lang", "Ljava/lang/String;"},
        {"TLRPC$TL_textWithEntities", "text", "Ljava/lang/String;"},
        {"TLRPC$TL_textWithEntities", "entities", "Ljava/util/ArrayList;"},
        {"TLRPC$TL_messages_translateResult", "result", "Ljava/util/ArrayList;"}
    };
    private static String descriptor(Mapping map, String type) {
        if (!type.startsWith("@")) return type;
        String canonical = TelegramFingerprints.owners().get(type.substring(1));
        if (canonical == null && (type.equals("@InputSerializedData") || type.equals("@OutputSerializedData"))) canonical = "org.telegram.tgnet." + type.substring(1);
        String name = canonical == null ? null : map.resolveClass(canonical);
        if (name == null) name = canonical;
        return name == null ? "__unresolved__" : "L" + name.replace('.', '/') + ";";
    }
    private static DexClass owner(DexIndex index, Mapping map, String key) {
        String canonical = TelegramFingerprints.owners().get(key);
        String name = canonical == null ? null : map.resolveClass(canonical);
        if (name == null) name = canonical;
        return name == null ? null : index.findClass(name);
    }
    static List<String> check(DexIndex index, Mapping mapping) {
        List<String> errors = new ArrayList<>();
        for (String[] spec : METHODS) {
            DexClass type = owner(index, mapping, spec[0]);
            int matches = 0;
            if (type != null) for (DexClass.Method method : type.methods) {
                if (!method.name().equals(mapping.resolveMethod(spec[0], spec[1]))
                        || !method.returnType().equals(descriptor(mapping, spec[2]))) continue;
                String[] params = method.parameterTypes();
                if (params.length != spec.length - 3) continue;
                boolean fit = true;
                for (int p = 0; p < params.length; p++) {
                    if (!spec[p + 3].equals("*") && !params[p].equals(descriptor(mapping, spec[p + 3]))) fit = false;
                }
                if (fit && method.isStatic() == spec[0].equals("BuildVars")) matches++;
            }
            if (matches != 1) errors.add("Telegram extra method " + spec[0] + "#" + spec[1]);
        }
        Map<String, Set<String>> distinct = new HashMap<>();
        for (String[] spec : FIELDS) {
            DexClass type = owner(index, mapping, spec[0]);
            String name = mapping.resolveField(spec[0], spec[1]);
            DexClass.Field field = name == null || type == null ? null : type.fieldNamed(name);
            if (field == null || !field.type().equals(descriptor(mapping, spec[2]))
                    || field.isStatic() != spec[0].equals("BuildVars")) {
                errors.add("Telegram extra field " + spec[0] + "." + spec[1]);
            }
            if (name != null && !distinct.computeIfAbsent(spec[0], key -> new HashSet<>()).add(name))
                errors.add("Overlapping Telegram extra fields " + spec[0]);
        }
        for (String key : new String[]{"TLRPC$TL_textWithEntities", "TLRPC$TL_messages_translateResult", "TLRPC$TL_error"}) {
            DexClass type = owner(index, mapping, key);
            if (type == null || type.methods.stream().noneMatch(m -> m.isConstructor() && m.parameterTypes().length == 0))
                errors.add("Missing translation response constructor " + key);
        }
        DexClass cell = owner(index, mapping, "UserCell");
        if (cell == null || cell.methods.stream().filter(m -> m.isConstructor() && Arrays.equals(
                m.parameterTypes(), new String[]{"Landroid/content/Context;", "I", "I", "Z"})).count() != 1)
            errors.add("Missing exact contacts-list cell constructor");
        DexClass cm = owner(index, mapping, "ConnectionsManager");
        String[] init = {"I","I","I","I","Ljava/lang/String;","Ljava/lang/String;","Ljava/lang/String;",
                "Ljava/lang/String;","Ljava/lang/String;","Ljava/lang/String;","Ljava/lang/String;",
                "Ljava/lang/String;","Ljava/lang/String;","Ljava/lang/String;","Ljava/lang/String;",
                "I","J","Z","Z","Z","I","I"};
        if (cm == null || cm.methods.stream().filter(m -> m.name().equals("native_init")
                && m.isStatic() && m.returnType().equals("V") && Arrays.equals(m.parameterTypes(), init)).count() != 1)
            errors.add("Unexpected native API identity initialization signature");
        return errors;
    }
    public static void main(String[] args) throws Exception {
        if (!"org.telegram.messenger".equals(ApkPackage.of(new File(args[0])))) {
            System.out.println("Telegram extras are enabled only in org.telegram.messenger");
            return;
        }
        DexIndex index = DexIndex.fromApk(new File(args[0]));
        Resolver.Report report = new Resolver.Report();
        Mapping mapping = new Resolver(index, TelegramFingerprints.owners())
                .resolve(TelegramFingerprints.all(), report);
        List<String> errors = check(index, mapping);
        if (!errors.isEmpty()) throw new AssertionError(errors.toString());
        for (String[] method : METHODS) {
            Mapping bad = Mapping.deserialize(mapping.serialize());
            bad.methods.put(method[0] + "#" + method[1], "__missing__");
            if (check(index, bad).isEmpty()) throw new AssertionError("Missing method accepted " + Arrays.toString(method));
        }
        for (String[] field : FIELDS) {
            Mapping bad = Mapping.deserialize(mapping.serialize());
            bad.fields.put(field[0] + "#" + field[1], "__missing__");
            if (check(index, bad).isEmpty()) throw new AssertionError("Missing field accepted " + Arrays.toString(field));
        }
        System.out.println("TELEGRAM_EXTRAS_AUDIT_PASS: " + METHODS.length + " method shapes, "
                + FIELDS.length + " typed fields and individual removal controls");
    }
}
