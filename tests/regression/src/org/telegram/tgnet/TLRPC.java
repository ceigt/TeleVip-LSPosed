package org.telegram.tgnet;
public final class TLRPC {
public static class TL_boolTrue extends TLObject {} public static class TL_error extends TLObject {public int code;public String text;}
public static class TL_messages_affectedMessages extends TLObject {public int pts,pts_count;}
public static class Peer {public long channel_id,chat_id;}
public static class TL_messages_readHistory extends TLObject {public Object peer;}
public static class TL_messages_readDiscussion extends TLObject {} public static class TL_messages_readEncryptedHistory extends TLObject {}
public static class TL_messages_readMessageContents extends TLObject {} public static class TL_channels_readHistory extends TLObject {}
public static class TL_channels_readMessageContents extends TLObject {} public static class TL_messages_setTyping extends TLObject {}
public static class TL_messages_setEncryptedTyping extends TLObject {}
}
