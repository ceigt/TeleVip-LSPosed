package com.my.televip.obfuscate.resolve;

import com.my.televip.obfuscate.StartupRequestQueue;
import com.my.televip.obfuscate.StartupDispatchSelector;
import com.my.televip.features.ui.AdResponseCallbacks;
import java.lang.reflect.Method;

public final class CompatSafetyTest {
    public interface Regular {void done(Object response, Exception error);}
    public interface Timestamp {void done(Object response, Exception error,long timestamp);}
    public interface Ambiguous {void first(Object response,Exception error);void second(Object response,Exception error);}
    public static class LambdaDispatch {
        public void lambda(Object request, Regular a, Timestamp b, Regular c, Regular d, int e, int f, int g, boolean h, int token) {}
    }
    public static class DualDispatch {
        private void sendRequestInternal(Object request, Regular a, Timestamp b, Regular c, Regular d, int e, int f, int g, boolean h, int token) {}
        public void lambda(Object request, Regular a, Timestamp b, Regular c, Regular d, int e, int f, int g, boolean h, int token) {}
    }
    public static class AmbiguousDispatch {
        public void first(Object request, Regular a, Timestamp b, Regular c, Regular d, int e, int f, int g, boolean h, int token) {}
        public void second(Object request, Regular a, Timestamp b, Regular c, Regular d, int e, int f, int g, boolean h, int token) {}
    }
    private static int calls;
    private static void check(boolean condition) {if(!condition) throw new AssertionError();}
    public static void main(String[] args) throws Throwable {
        check(StartupDispatchSelector.find(DualDispatch.class,Object.class).getName().equals("sendRequestInternal"));
        check(StartupDispatchSelector.find(LambdaDispatch.class,Object.class).getName().equals("lambda"));
        check(StartupDispatchSelector.find(AmbiguousDispatch.class,Object.class)==null);
        check(StartupDispatchSelector.find(Object.class,Object.class)==null);
        Object response=new Object();
        Regular regular=(value,error)-> {check(value==response && error==null);calls++;};
        Method method=AdResponseCallbacks.find(regular,response,false);
        AdResponseCallbacks.invoke(method,regular,response);check(calls==1);
        Timestamp timestamp=(value,error,time)-> {check(value==response && error==null && time==0L);calls++;};
        AdResponseCallbacks.invoke(AdResponseCallbacks.find(timestamp,response,true),timestamp,response);check(calls==2);
        boolean rejected=false;
        try {AdResponseCallbacks.find(new Ambiguous() {public void first(Object a,Exception b) {} public void second(Object a,Exception b) {}},response,false);}
        catch(NoSuchMethodException expected) {rejected=true;}
        check(rejected);
        Regular failing=(value,error)-> {throw new IllegalStateException("injected callback failure");};
        try {AdResponseCallbacks.invoke(AdResponseCallbacks.find(failing,response,false),failing,response);throw new AssertionError();}
        catch(IllegalStateException expected) {check(expected.getMessage().contains("injected"));}
        StartupRequestQueue<String> queue=new StartupRequestQueue<>(2);
        Object first=new Object(),second=new Object();
        check(queue.offer(first,1,"first")==StartupRequestQueue.Result.QUEUED);
        check(queue.offer(second,1,"other account")==StartupRequestQueue.Result.QUEUED);
        check(queue.offer(first,2,"excess")==StartupRequestQueue.Result.FULL);
        queue.cancel(first,1);check(queue.size()==1);
        check(queue.offer(first,3,"third")==StartupRequestQueue.Result.QUEUED);
        queue.release();queue.cancel(second,1); // Cancellation still works after release, before dispatch.
        check("third".equals(queue.poll()) && queue.poll()==null);
        check(queue.offer(first,4,"late")==StartupRequestQueue.Result.RELEASED);
        System.out.println("COMPAT_SAFETY_PASS: ordinary/timestamp callbacks, ambiguity/failure, bounded queue and account-scoped cancellation");
    }
}
