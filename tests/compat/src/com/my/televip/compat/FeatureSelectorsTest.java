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
        java.lang.reflect.Constructor<?> ctor=Empty.class.getDeclaredConstructor();
        Object response=new Other(),empty=new Empty();
        check(AdResponseFilter.answer(response,ctor,false)==response);
        check(AdResponseFilter.answer(null,ctor,true)==null);
        check(AdResponseFilter.answer(empty,ctor,true)==empty);
        check(AdResponseFilter.answer(response,ctor,true) instanceof Empty);
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
