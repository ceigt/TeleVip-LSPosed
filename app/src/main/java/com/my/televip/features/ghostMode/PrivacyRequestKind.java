package com.my.televip.features.ghostMode;

/** Stable protocol identities; UI/dispatcher obfuscation must not decide privacy policy. */
public enum PrivacyRequestKind {
    OTHER, READ_DIALOG, READ_DISCUSSION, READ_PRIVATE, READ_ENCRYPTED, READ_CHANNEL, TYPING, ONLINE, STORY_READ, STORY_VIEWS;

    public static PrivacyRequestKind named(String name) {
        if (name == null) return OTHER;
        switch (name) {
            case "org.telegram.tgnet.TLRPC$TL_messages_readHistory": return READ_DIALOG;
            case "org.telegram.tgnet.TLRPC$TL_messages_readDiscussion": return READ_DISCUSSION;
            case "org.telegram.tgnet.TLRPC$TL_messages_readEncryptedHistory": return READ_ENCRYPTED;
            case "org.telegram.tgnet.TLRPC$TL_messages_readMessageContents": return READ_PRIVATE;
            case "org.telegram.tgnet.TLRPC$TL_channels_readHistory":
            case "org.telegram.tgnet.TLRPC$TL_channels_readMessageContents": return READ_CHANNEL;
            case "org.telegram.tgnet.TLRPC$TL_messages_setTyping":
            case "org.telegram.tgnet.TLRPC$TL_messages_setEncryptedTyping": return TYPING;
            case "org.telegram.tgnet.tl.TL_account$updateStatus": return ONLINE;
            case "org.telegram.tgnet.tl.TL_stories$TL_stories_readStories": return STORY_READ;
            case "org.telegram.tgnet.tl.TL_stories$TL_stories_incrementStoryViews": return STORY_VIEWS;
            default: return OTHER;
        }
    }
    public boolean read() {
        return this == READ_DIALOG || this == READ_DISCUSSION || this == READ_PRIVATE || this == READ_ENCRYPTED || this == READ_CHANNEL;
    }
}
