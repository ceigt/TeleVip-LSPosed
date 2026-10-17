package com.my.televip.compat;
import com.my.televip.features.media.MediaHookSelector;
import com.my.televip.features.ui.AdResponseFilter;

public final class FeatureSelectorsTest {
    public static class Message {}
    public static class Task implements Runnable {public void run() {}}
    public static class OldChat {
        public Task read(Message message, boolean flag) {return null;}
        public Task delete(Message message) {return null;}
    }
    public static class NewChat {
        public Task changedRead(Message message, boolean flag) {return null;}
        public static Task changedDelete(NewChat owner, Message message) {return null;}
    }
    public static class WrongChat {
        public Object read(Message message, boolean flag) {return null;}
        public static Task delete(Message message) {return null;}
        public Task reversed(boolean flag, Message message) {return null;}
    }
    public static class AmbiguousChat {
        public Task delete(Message message) {return null;}
        public static Task delete(AmbiguousChat owner, Message message) {return null;}
    }
    public static class TL {}
    public static class Empty extends TL {public Empty() {}}
    public static class AdBase extends TL {
        public java.util.ArrayList<Object> messages = new java.util.ArrayList<>();
        public java.util.List<Object> chats = new java.util.ArrayList<>();
        public Object other;
        public int flags;
        public static final java.util.List<Object> SHARED = java.util.Collections.singletonList("unchanged");
    }
    public static class StrippedEmpty extends AdBase {
        public java.util.Collection<Object> users = new java.util.ArrayList<>();
        private StrippedEmpty(int ignored) {throw new AssertionError("must not invoke removed constructor");}
    }
    public static class ConstructorEmpty extends AdBase {
        public ConstructorEmpty() {flags = 7; other = "constructor ran";}
    }
    public static class ThrowingEmpty extends TL {
        public ThrowingEmpty() {throw new IllegalStateException("constructor fault");}
    }
    public abstract static class AbstractEmpty extends TL {}
    public static class UnsupportedEmpty extends TL {
        public java.util.Set<Object> unsupported;
        private UnsupportedEmpty(int ignored) {}
    }
    public static class Other extends TL {public Other() {}}
    public static class Stream {}
    public static class Request {public TL deserializeResponse(Stream stream, int id, boolean strict) {return new Other();}}
    public static class WrongRequest {public TL deserializeResponse(Object stream, int id, boolean strict) {return new Other();}}
    public static class StaticRequest {public static TL deserializeResponse(Stream stream, int id, boolean strict) {return new Other();}}
    public static class WrongReturn {public Other deserializeResponse(Stream stream, int id, boolean strict) {return new Other();}}
    private static int count;
    private static void check(boolean value) {count++;if(!value)throw new AssertionError("Selector assertion "+count);}
    public static int run() throws Exception {
        count=0;
        check(MediaHookSelector.find(OldChat.class,"read",false,Message.class,boolean.class)!=null);
        check(MediaHookSelector.find(OldChat.class,"delete",true,Message.class)!=null);
        check(MediaHookSelector.find(NewChat.class,"changedRead",false,Message.class,boolean.class)!=null);
        check(MediaHookSelector.find(NewChat.class,"changedDelete",true,Message.class).getParameterTypes()[0]==NewChat.class);
        check(MediaHookSelector.find(NewChat.class,"changedDelete",false,Message.class)==null);
        check(MediaHookSelector.find(NewChat.class,"oldName",true,Message.class)==null);
        check(MediaHookSelector.find(WrongChat.class,"read",false,Message.class,boolean.class)==null);
        check(MediaHookSelector.find(WrongChat.class,"delete",true,Message.class)==null);
        check(MediaHookSelector.find(WrongChat.class,"reversed",false,Message.class,boolean.class)==null);
        check(MediaHookSelector.find(AmbiguousChat.class,"delete",true,Message.class)==null);
        check(MediaHookSelector.find(null,"delete",true,Message.class)==null);
        check(AdResponseFilter.reader(Request.class,Stream.class,TL.class,Empty.class)!=null);
        check(AdResponseFilter.reader(WrongRequest.class,Stream.class,TL.class,Empty.class)==null);
        check(AdResponseFilter.reader(StaticRequest.class,Stream.class,TL.class,Empty.class)==null);
        check(AdResponseFilter.reader(WrongReturn.class,Stream.class,TL.class,Empty.class)==null);
        check(AdResponseFilter.reader(null,Stream.class,TL.class,Empty.class)==null);
        AdResponseFilter.EmptyResponse ctor=AdResponseFilter.emptyResponse(Empty.class);
        Object response=new Other(),empty=new Empty();
        check(AdResponseFilter.answer(response,ctor,false)==response);
        check(AdResponseFilter.answer(null,ctor,true)==null);
        check(AdResponseFilter.answer(empty,ctor,true)==empty);
        check(AdResponseFilter.answer(response,ctor,true) instanceof Empty);
        AdResponseFilter.EmptyResponse stripped=AdResponseFilter.emptyResponse(StrippedEmpty.class);
        StrippedEmpty created=(StrippedEmpty)stripped.create();
        check(created.messages!=null && created.messages.isEmpty());
        check(created.chats!=null && created.chats.isEmpty());
        check(created.users!=null && created.users.isEmpty());
        check(created.flags==0 && created.other==null);
        check(AdBase.SHARED.size()==1);
        created.messages.add("first response");
        check(((StrippedEmpty)stripped.create()).messages.isEmpty());
        check(AdResponseFilter.answer(created,stripped,true)==created);
        check(AdResponseFilter.answer(response,stripped,false)==response);
        check(AdResponseFilter.answer(null,stripped,true)==null);
        check(AdResponseFilter.answer(response,stripped,true) instanceof StrippedEmpty);
        ConstructorEmpty constructed=(ConstructorEmpty)AdResponseFilter.emptyResponse(ConstructorEmpty.class).create();
        check(constructed.flags==7 && "constructor ran".equals(constructed.other));
        boolean rejected=false;
        try {AdResponseFilter.emptyResponse(ThrowingEmpty.class).create();}
        catch(java.lang.reflect.InvocationTargetException expected) {rejected=expected.getCause() instanceof IllegalStateException;}
        check(rejected);
        rejected=false;
        try {AdResponseFilter.emptyResponse(AbstractEmpty.class);}
        catch(IllegalArgumentException expected) {rejected=true;}
        check(rejected);
        rejected=false;
        try {AdResponseFilter.emptyResponse(UnsupportedEmpty.class);}
        catch(IllegalArgumentException expected) {rejected=true;}
        check(rejected);
        com.my.televip.features.media.MediaPathOverride paths=new com.my.televip.features.media.MediaPathOverride();
        Object first=new Message(),sameIdDifferentMessage=new Message();
        java.io.File path=new java.io.File("media-test");
        paths.remember(first,path);
        check(paths.find(first)==path);
        check(paths.find(sameIdDifferentMessage)==null);
        check(paths.find(null)==null);
        paths.remember(sameIdDifferentMessage,path);
        check(paths.find(first)==null && paths.find(sameIdDifferentMessage)==path);
        paths.remember(null,null);
        check(paths.find(sameIdDifferentMessage)==null);
        return count;
    }
    public static void main(String[] args) throws Exception {System.out.println("FEATURE_SELECTORS_PASS: "+run()+" assertions");}
}
