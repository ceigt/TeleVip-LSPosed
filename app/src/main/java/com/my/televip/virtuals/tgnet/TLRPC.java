package com.my.televip.virtuals.tgnet;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.obfuscate.Obfuscate;

import java.util.ArrayList;

import com.my.televip.compat.XposedHelpers;

public class TLRPC {
    public static class Peer {
        final Object peer;

        public Peer(Object peer) {
            this.peer = peer;
        }

        public long getUser_id(){
            return XposedHelpers.getLongField(peer, Obfuscate.getFieldName("TLRPC$Peer", "user_id"));
        }
        public long getChat_id(){
            return XposedHelpers.getLongField(peer, Obfuscate.getFieldName("TLRPC$Peer", "chat_id"));
        }
        public long getChannel_id(){
            return XposedHelpers.getLongField(peer, Obfuscate.getFieldName("TLRPC$Peer", "channel_id"));
        }
    }

    public static class User {
        Object user;

        public User(Object user) {
            this.user = user;
        }

        public String getPhone() {
            return (String) XposedHelpers.getObjectField(user, Obfuscate.getFieldName("TLRPC$User", "phone"));
        }

        public void setPhone(String phone){
            XposedHelpers.setObjectField(user, Obfuscate.getFieldName("TLRPC$User", "phone"), phone);
        }

        public Object getUser(){
            return user;
        }

    }

    public static class Message {
        private final Object message;
        private int id;
        public Message(Object message) {
            this.message = message;
        }

        public Message() {
            this.message = XposedHelpers.newInstance(ClassLoad.getClass(ClassNames.MESSAGE));
        }

        //get
        public int getId(){
            if (id == 0){
                id = XposedHelpers.getIntField(message, Obfuscate.getFieldName("TLRPC$Message", "id"));
            }
            return id;
        }

        public String getMessage(){
            return (String) XposedHelpers.getObjectField(message, Obfuscate.getFieldName("TLRPC$Message", "message"));
        }

        public Peer getFrom_id(){
            Object from = XposedHelpers.getObjectField(message, Obfuscate.getFieldName("TLRPC$Message", "from_id"));
            return from == null ? null : new TLRPC.Peer(from);
        }

        public int getFlags(){
            return XposedHelpers.getIntField(message, Obfuscate.getFieldName("TLRPC$Message", "flags"));
        }

        public int getTtl(){
            return XposedHelpers.getIntField(message, Obfuscate.getFieldName("TLRPC$Message", "ttl"));
        }

        public Object get_Message(){
            return message;
        }

        //set
        public void setId(int id){
            XposedHelpers.setIntField(message, Obfuscate.getFieldName("TLRPC$Message", "id"), id);
            this.id = id;
        }

        public void setMessage(String msg){
            XposedHelpers.setObjectField(message, Obfuscate.getFieldName("TLRPC$Message", "message"), msg);
        }

        public void setFlags(int flags){
            XposedHelpers.setIntField(message, Obfuscate.getFieldName("TLRPC$Message", "flags"), flags);
        }

        public void setTtl(Object ttl){
            XposedHelpers.setObjectField(message, Obfuscate.getFieldName("TLRPC$Message", "ttl"), ttl);
        }

        public static Message TLdeserialize(NativeByteBuffer stream, int constructor, boolean exception){
            return new Message(XposedHelpers.callStaticMethod(ClassLoad.getClass(ClassNames.MESSAGE), Obfuscate.getMethodName("TLRPC$Message", "TLdeserialize"), stream.nativeByteBuffer, constructor, exception));
        }

        public void readAttachPath(NativeByteBuffer stream, long currentUserId){
            XposedHelpers.callMethod(message, Obfuscate.getMethodName("TLRPC$Message", "readAttachPath"), stream.nativeByteBuffer, currentUserId);
        }
    }

    public static class InputPeer {

        private final Object inputPeer;

        public InputPeer(Object message) {
            this.inputPeer = message;
        }
        public long getUser_id(){
            return XposedHelpers.getLongField(inputPeer, Obfuscate.getFieldName("TLRPC$InputPeer", "user_id"));
        }
        public long getChat_id(){
            return XposedHelpers.getLongField(inputPeer, Obfuscate.getFieldName("TLRPC$InputPeer", "chat_id"));
        }
        public long getChannel_id(){
            return XposedHelpers.getLongField(inputPeer, Obfuscate.getFieldName("TLRPC$InputPeer", "channel_id"));
        }

        public Object getInputPeer() {
            return inputPeer;
        }

    }

    public static class messages_Messages {
        Object messages_Messages;

        public messages_Messages(Object messages) {
            messages_Messages = messages;
        }
        public ArrayList<Object> getMessages(){
            return (ArrayList<Object>) XposedHelpers.getObjectField(messages_Messages, Obfuscate.getFieldName("TLRPC$messages_Messages", "messages"));
        }
    }

    public static class TL_updateDeleteChannelMessages {
        private final Object tl_updateDeleteChannelMessages;

        public TL_updateDeleteChannelMessages(Object instance)
        {
            this.tl_updateDeleteChannelMessages = instance;
        }

        public long getChannelID()
        {
            return XposedHelpers.getLongField(tl_updateDeleteChannelMessages,  Obfuscate.getFieldName("TL_update$TL_updateDeleteChannelMessages", "channel_id"));
        }

        public ArrayList<Integer> getMessages() {
            return (ArrayList<Integer>) XposedHelpers.getObjectField(tl_updateDeleteChannelMessages, Obfuscate.getFieldName("TL_update$TL_updateDeleteChannelMessages", "messages"));
        }
    }

    public static class TL_updateDeleteMessages {
        private final Object tl_updateDeleteMessages;

        public TL_updateDeleteMessages(Object instance) {
            this.tl_updateDeleteMessages = instance;
        }

        public ArrayList<Integer> getMessages() {
            return (ArrayList<Integer>) XposedHelpers.getObjectField(tl_updateDeleteMessages, Obfuscate.getFieldName("TL_update$TL_updateDeleteMessages", "messages"));
        }
    }

    public static class TL_messages_affectedMessages {
        final Object instance;

        public TL_messages_affectedMessages()
        {
            this.instance = XposedHelpers.newInstance(ClassLoad.getClass(ClassNames.TL_MESSAGES_AFFECTED));
        }
        public TL_messages_affectedMessages(Object instance)
        {
            this.instance = instance;
        }

        public int getPts(){
            return XposedHelpers.getIntField(instance, Obfuscate.getFieldName("TLRPC$TL_messages_affectedMessages", "pts"));
        }

        public int getPtsCount(){
            return XposedHelpers.getIntField(instance, Obfuscate.getFieldName("TLRPC$TL_messages_affectedMessages", "pts_count"));
        }
        public void setPts(int pts){
            XposedHelpers.setIntField(instance, Obfuscate.getFieldName("TLRPC$TL_messages_affectedMessages", "pts"), pts);
        }

        public void setPtsCount(int pts_count){
            XposedHelpers.setIntField(instance, Obfuscate.getFieldName("TLRPC$TL_messages_affectedMessages", "pts_count"), pts_count);
        }

        public Object getTL_messages_affectedMessages(){
            return instance;
        }

    }

    public static class TL_channels_readHistory {
        final Object instance;

        public TL_channels_readHistory()
        {
            this.instance = XposedHelpers.newInstance(ClassLoad.getClass(ClassNames.TL_CHANNELS_READ_HISTORY));
        }
        public TL_channels_readHistory(Object instance)
        {
            this.instance = instance;
        }

        public void setChannel(Object channel){
            XposedHelpers.setObjectField(instance, Obfuscate.getFieldName("TLRPC$TL_channels_readHistory", "channel"), channel);
        }

        public void setMax_id(int max_id){
            XposedHelpers.setIntField(instance, Obfuscate.getFieldName("TLRPC$TL_channels_readHistory", "max_id"), max_id);
        }

        public Object getTL_channels_readHistory(){
            return instance;
        }
    }

    public static class TL_messages_readHistory {
        final Object instance;

        public TL_messages_readHistory()
        {
            this.instance = XposedHelpers.newInstance(ClassLoad.getClass(ClassNames.TL_MESSAGES_READ_HISTORY));
        }

        public TL_messages_readHistory(Object instance)
        {
            this.instance = instance;
        }

        public void setPeer(InputPeer peer){
            XposedHelpers.setObjectField(instance, Obfuscate.getFieldName("TLRPC$TL_messages_readHistory", "peer"), peer.inputPeer);
        }

        public void setMax_id(int max_id){
            XposedHelpers.setIntField(instance, Obfuscate.getFieldName("TLRPC$TL_messages_readHistory", "max_id"), max_id);
        }

        public Object getTL_messages_readHistory(){
            return instance;
        }

    }
}
