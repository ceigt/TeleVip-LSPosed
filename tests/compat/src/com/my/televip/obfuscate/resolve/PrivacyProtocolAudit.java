package com.my.televip.obfuscate.resolve;

import com.my.televip.obfuscate.dex.*;
import com.my.televip.features.ghostMode.PrivacyRequestKind;
import java.util.*;

/** Verify the host protocol used by local privacy completions, without executing host code. */
final class PrivacyProtocolAudit {
    private static final String BASE = "org.telegram.tgnet.";
    private static final String[] REQUESTS = {
        "TLRPC$TL_messages_readHistory", "TLRPC$TL_messages_readDiscussion",
        "TLRPC$TL_messages_readEncryptedHistory", "TLRPC$TL_messages_readMessageContents",
        "TLRPC$TL_channels_readHistory", "TLRPC$TL_channels_readMessageContents",
        "TLRPC$TL_messages_setTyping", "TLRPC$TL_messages_setEncryptedTyping",
        "tl.TL_account$updateStatus", "tl.TL_stories$TL_stories_readStories",
        "tl.TL_stories$TL_stories_incrementStoryViews"
    };
    static List<String> check(DexIndex index, Mapping mapping) {
        List<String> errors = new ArrayList<>();
        String stream = descriptor(mapping, BASE + "InputSerializedData");
        String bool = descriptor(mapping, BASE + "TLRPC$Bool");
        String affected = descriptor(mapping, BASE + "TLRPC$TL_messages_affectedMessages");
        String vector = descriptor(mapping, BASE + "Vector");
        for (String suffix : REQUESTS) {
            String source = BASE + suffix;
            PrivacyRequestKind kind = PrivacyRequestKind.named(source);
            if (kind == PrivacyRequestKind.OTHER) { errors.add("Unclassified privacy request: " + source); continue; }
            String expected = kind == PrivacyRequestKind.STORY_READ ? vector
                    : kind == PrivacyRequestKind.READ_DIALOG || kind == PrivacyRequestKind.READ_PRIVATE ? affected : bool;
            DexClass request = cls(index, mapping, source);
            boolean decoded = false;
            if (request != null) for (DexClass.Method method : request.methods) {
                if (method.isStatic() || !method.hasCode() || (method.accessFlags & 0x40) != 0
                        || !(method.name().equals("deserializeResponse") || method.name().equals("deserializeResponseT"))
                        || !Arrays.equals(method.parameterTypes(), new String[]{stream, "I", "Z"})) continue;
                final boolean[] match = {false};
                method.scan(new CodeScanner.Visitor() {
                    @Override public void invoke(int opcode, int called) {
                        DexFile dex = method.owner.dex;
                        // Require a decode call taking this stream, constructor id and exception flag.
                        if (dex.protoReturnType(dex.methodProto(called)).equals(expected)
                                && Arrays.equals(dex.protoParameters(dex.methodProto(called)), new String[]{stream, "I", "Z"}))
                            match[0] = true;
                    }
                });
                decoded |= match[0];
            }
            if (!decoded) errors.add("Privacy response decoder differs: " + source);
        }
        constructor(index, mapping, BASE + "TLRPC$TL_boolTrue", false, errors);
        constructor(index, mapping, BASE + "TLRPC$TL_messages_affectedMessages", false, errors);
        constructor(index, mapping, BASE + "TLRPC$TL_error", false, errors);
        constructor(index, mapping, BASE + "Vector", true, errors);
        field(index, mapping, BASE + "TLRPC$TL_messages_affectedMessages", "TLRPC$TL_messages_affectedMessages", "pts", "I", errors);
        field(index, mapping, BASE + "TLRPC$TL_messages_affectedMessages", "TLRPC$TL_messages_affectedMessages", "pts_count", "I", errors);
        // Error fallback uses the protocol's literal public field names.
        field(index, mapping, BASE + "TLRPC$TL_error", null, "code", "I", errors);
        field(index, mapping, BASE + "TLRPC$TL_error", null, "text", "Ljava/lang/String;", errors);
        field(index, mapping, BASE + "tl.TL_account$updateStatus", "TL_account$updateStatus", "offline", "Z", errors);
        field(index, mapping, BASE + "TLRPC$TL_messages_readHistory", "TLRPC$TL_messages_readHistory", "peer",
                descriptor(mapping, BASE + "TLRPC$InputPeer"), errors);
        for (String name : new String[]{"channel_id", "chat_id"})
            field(index, mapping, BASE + "TLRPC$InputPeer", "TLRPC$InputPeer", name, "J", errors);
        delegate(index, mapping, BASE + "RequestDelegate", false, errors);
        delegate(index, mapping, BASE + "RequestDelegateTimestamp", true, errors);
        return errors;
    }
    private static void constructor(DexIndex index, Mapping mapping, String source, boolean nullableArgument, List<String> errors) {
        DexClass type = cls(index, mapping, source);
        int count = 0;
        if (type != null && !type.isAbstract() && !type.isInterface()) for (DexClass.Method m : type.methods) {
            String[] p = m.parameterTypes();
            if (m.isConstructor() && !m.isStatic() && (nullableArgument
                    ? p.length == 1 && (p[0].startsWith("L") || p[0].startsWith("[")) : p.length == 0)) count++;
        }
        if (count != 1) errors.add("Privacy response constructor unavailable/ambiguous: " + source);
    }
    private static void field(DexIndex index, Mapping mapping, String source, String alias, String name, String expected, List<String> errors) {
        String resolved = alias == null ? null : mapping.resolveField(alias, name);
        if (resolved == null) resolved = name;
        DexClass type = cls(index, mapping, source);
        Set<String> seen = new HashSet<>();
        DexClass.Field found = null;
        while (type != null && seen.add(type.descriptor)) {
            found = type.fieldNamed(resolved);
            if (found != null) break;
            type = type.superclass == null ? null : index.byDescriptor(type.superclass);
        }
        if (found == null || found.isStatic() || !found.type().equals(expected)) errors.add("Privacy field differs: " + source + "#" + name);
    }
    private static void delegate(DexIndex index, Mapping mapping, String source, boolean timestamp, List<String> errors) {
        DexClass type = cls(index, mapping, source);
        String tl = descriptor(mapping, BASE + "TLObject"), error = descriptor(mapping, BASE + "TLRPC$TL_error");
        String[] expected = timestamp ? new String[]{tl, error, "J"} : new String[]{tl, error};
        int count = 0;
        if (type != null && type.isInterface()) for (DexClass.Method m : type.methods)
            if (!m.isStatic() && m.returnType().equals("V") && Arrays.equals(m.parameterTypes(), expected)) count++;
        if (count != 1) errors.add("Privacy delegate differs: " + source);
    }
    private static DexClass cls(DexIndex index, Mapping mapping, String source) {
        String name = mapping.resolveClass(source); return index.findClass(name == null ? source : name);
    }
    private static String descriptor(Mapping mapping, String source) {
        String name = mapping.resolveClass(source); return DexNames.toDescriptor(name == null ? source : name);
    }
}
