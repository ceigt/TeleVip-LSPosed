package org.televip.regression;
import android.content.*;
import com.my.televip.Database.LocalStateStore;
import java.io.File;
import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

final class LocalStateRaceTests {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static int run(Context context)throws Exception{
        checks=0;CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicBoolean failCommit=new AtomicBoolean();
        Context wrapped=new ContextWrapper(context){
            @Override public Context getApplicationContext(){return this;}
            @Override public SharedPreferences getSharedPreferences(String name,int mode){
                SharedPreferences original=super.getSharedPreferences(name,mode);
                return (SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class[]{SharedPreferences.class},(proxy,method,args)->{
                    if(method.getName().equals("contains")&&Thread.currentThread().getName().equals("old-capture")){
                        Object old=method.invoke(original,args);entered.countDown();release.await();return old;
                    }
                    if(method.getName().equals("edit")){
                        SharedPreferences.Editor editor=original.edit();AtomicBoolean marker=new AtomicBoolean();
                        return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class[]{SharedPreferences.Editor.class},(p,m,a)->{
                            if(m.getName().equals("putBoolean")&&failCommit.get()){marker.set(true);return p;}
                            if(m.getName().equals("commit")&&marker.get())return false;
                            Object value=m.invoke(editor,a);return value==editor?p:value;
                        });
                    }
                    try{return method.invoke(original,args);}catch(InvocationTargetException error){throw error.getCause();}
                });
            }
        };
        File path=new File(context.getCacheDir(),"logout-race-"+System.nanoTime()+".db");
        LocalStateStore store=new LocalStateStore(wrapped,path.toString());
        try{
            store.save(new LocalStateStore.Record(7001,202,7,100,1000,"old",new byte[8]));
            AtomicReference<LocalStateStore.Session> old=new AtomicReference<>();
            Thread capture=new Thread(()->old.set(store.session(7001)),"old-capture");capture.start();
            check(entered.await(5,TimeUnit.SECONDS),"capture did not enter marker read");
            FutureTask<Void> revoke=new FutureTask<>(()->{store.revokeAccount(7001);return null;});new Thread(revoke).start();
            try{revoke.get(100,TimeUnit.MILLISECONDS);throw new AssertionError("session mint and revoke are not serialized");}
            catch(TimeoutException expected){checks++;}finally{release.countDown();}
            capture.join(5000);revoke.get(5,TimeUnit.SECONDS);store.finishAccountCleanup(7001);
            check(!store.isCurrent(old.get()),"stale capture adopted logout generation");
            store.save(new LocalStateStore.Record(7002,202,7,100,1000,"private",new byte[8]));
            failCommit.set(true);
            try{store.revokeAccount(7002);throw new AssertionError("fault injection was not observed");}catch(IllegalStateException expected){checks++;}
            check(store.records(7002).isEmpty(),"failed marker left account readable");
            store.finishAccountCleanup(7002);failCommit.set(false);
            check(store.records(7002).isEmpty(),"cleanup after failed marker retained data");
            store.save(new LocalStateStore.Record(7003,202,7,100,1000,"page",new byte[8]));
            check(store.browse(7003,"PAGE",0,1).size()==1&&store.browse(7003,"page",1,1).isEmpty(),"background preview pagination");
        }finally{release.countDown();store.close();context.deleteDatabase(path.toString());}
        File unopenedPath=new File(context.getCacheDir(),"logout-first-open-"+System.nanoTime()+".db");
        LocalStateStore unopened=new LocalStateStore(context,unopenedPath.toString());
        try{
            unopened.revokeAccount(7101);LocalStateStore.Session revoked=unopened.session(7101);
            check(!unopened.isCurrent(revoked),"revoked token valid before first open");
            unopened.getWritableDatabase();
            check(!unopened.isCurrent(unopened.session(7101)),"onOpen prematurely completed a live logout");
            unopened.finishAccountCleanup(7101,false);
            check(!unopened.isCurrent(unopened.session(7101)),"failed native logout released revocation");
            unopened.finishAccountCleanup(7101,true);
            check(unopened.isCurrent(unopened.session(7101))&&!unopened.isCurrent(revoked),"completed logout did not retain token isolation");
            unopened.revokeAccount(7102);unopened.close();
            unopened=new LocalStateStore(context,unopenedPath.toString());unopened.getWritableDatabase();
            check(unopened.isCurrent(unopened.session(7102)),"prior-process marker was not recovered");
        }finally{unopened.close();context.deleteDatabase(unopenedPath.toString());}
        return checks;
    }
}
