package org.televip.regression;

import android.app.Instrumentation;
import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.compat.XposedBridge;
import com.my.televip.diagnostics.PrivacyStatus;
import com.my.televip.features.ghostMode.*;
import com.my.televip.features.media.MediaOpenScope;
import com.my.televip.obfuscate.BootstrapRequests;
import org.telegram.tgnet.*;
import org.telegram.tgnet.tl.*;
import java.lang.reflect.Method;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Exercise real startup/interception code against controlled host objects and injected faults. */
public final class PriorityFixTests {
    private static int checks;
    private static final ConnectionsManager connection = new ConnectionsManager();
    private static Method dispatch;
    public static class FinishedActivity extends android.app.Activity { @Override public boolean isFinishing(){return true;} }
    public static class FakeViewer { public android.view.View galleryButton;
        public void setIsAboutToSwitchToIndex(int index,boolean a,boolean b,boolean c){galleryButton.setVisibility(android.view.View.INVISIBLE);}}
    public static class BrokenOnline extends TLObject {}
    private static void check(boolean result, String text) { checks++; if (!result) throw new AssertionError(text); }
    private static void send(TLObject request, ConnectionsManager.Normal normal, ConnectionsManager.Timestamp timestamp) throws Throwable {
        XposedBridge.invoke(dispatch, connection, request, normal, timestamp, null, null, 0, 0, 0, false, 9000 + checks);
    }
    private static class Completion implements ConnectionsManager.Normal {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicInteger calls = new AtomicInteger();
        volatile TLObject response; volatile TLRPC.TL_error error;
        public void run(TLObject response, TLRPC.TL_error error) {
            this.response=response;this.error=error;calls.incrementAndGet();done.countDown();
        }
        void await() throws Exception { check(done.await(3,TimeUnit.SECONDS),"completion lost"); check(calls.get()==1,"completion not exactly once"); }
    }
    public static int run(Instrumentation instrumentation) throws Throwable {
        for (java.lang.reflect.Field field : ClassNames.class.getFields()) {
            if (field.getType()!=String.class) continue;
            String name=(String)field.get(null);
            try {ClassLoad.classes.put(name,Class.forName(name));} catch(ClassNotFoundException ignored) {}
        }
        ClassLoad.classes.put("org.telegram.tgnet.TLRPC$TL_boolTrue",TLRPC.TL_boolTrue.class);
        ClassLoad.classes.put("org.telegram.tgnet.TLRPC$TL_error",TLRPC.TL_error.class);
        ClassLoad.classes.put("org.telegram.tgnet.Vector",Vector.class);
        dispatch=ConnectionsManager.class.getDeclaredMethod("sendRequestInternal",TLObject.class,ConnectionsManager.Normal.class,
                ConnectionsManager.Timestamp.class,ConnectionsManager.Ack.class,ConnectionsManager.Write.class,
                int.class,int.class,int.class,boolean.class,int.class);
        ClassLoader missingHost=new ClassLoader(PriorityFixTests.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                if(name.equals("org.telegram.tgnet.ConnectionsManager")) throw new ClassNotFoundException(name);
                return super.loadClass(name,resolve);
            }
        };
        BootstrapRequests.install(missingHost);
        check(PrivacyStatus.hasIssues(),"missing startup gate was invisible");
        BootstrapRequests.install(PriorityFixTests.class.getClassLoader());
        check(!PrivacyStatus.hasIssues(),"recovered startup gate retained stale warning");
        send(new TLObject(),null,null);
        check(connection.delivered.get()==0,"startup request not held");
        Method start=com.my.televip.MainHook.class.getDeclaredMethod("start");start.setAccessible(true);
        com.my.televip.MainHook main=new com.my.televip.MainHook();
        instrumentation.runOnMainSync(()->{try {start.invoke(main);}catch(Exception e){throw new AssertionError(e);}});
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(com.my.televip.TeleVip.starts.get()<3 && System.nanoTime()<until) Thread.sleep(20);
        check(com.my.televip.TeleVip.starts.get()==3,"privacy failure did not retry three times");
        Thread.sleep(100);
        check(connection.delivered.get()==1,"privacy failure retained ordinary startup traffic");
        check(PrivacyStatus.hasIssues(),"initialization failure was invisible");
        check(!PrivacyStatus.warning().equals("ProtectionWarning"),"startup warning depends on unavailable host translations");
        PrivacyStatus.clear("privacy-init");
        com.my.televip.TeleVip.starts.set(0);com.my.televip.TeleVip.uiReady=false;GhostMode.isEnable=true;
        instrumentation.runOnMainSync(() -> com.my.televip.utils.Utils.setCurrentActivity(new FinishedActivity()));
        com.my.televip.MainHook uiMain=new com.my.televip.MainHook();
        instrumentation.runOnMainSync(()->{try {start.invoke(uiMain);}catch(Exception e){throw new AssertionError(e);}});
        until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(com.my.televip.TeleVip.starts.get()<3&&System.nanoTime()<until) Thread.sleep(20);
        check(com.my.televip.TeleVip.starts.get()==3,"settings failure did not retry");
        check(PrivacyStatus.hasIssues(),"settings failure was invisible");
        PrivacyStatus.clear("settings-init");com.my.televip.utils.Utils.setCurrentActivity(null);GhostMode.isEnable=false;
        com.my.televip.TeleVip.uiReady=true;
        PrivacyProtection.prepare();
        check(!PrivacyStatus.hasIssues(),"complete host types unexpectedly degraded");
        int delivered=connection.delivered.get();
        send(new TLObject(),null,null);
        check(connection.delivered.get()==delivered+1,"ordinary request blocked by fallback");
        for(TLObject request:new TLObject[]{new TLRPC.TL_messages_setTyping(),new TLRPC.TL_messages_setEncryptedTyping(),
                new TLRPC.TL_channels_readHistory(),new TLRPC.TL_messages_readEncryptedHistory(),new TL_stories.TL_stories_incrementStoryViews()}) {
            Completion callback=new Completion();delivered=connection.delivered.get();send(request,callback,null);callback.await();
            check(callback.response instanceof TLRPC.TL_boolTrue && callback.error==null,"wrong bool response");
            check(connection.delivered.get()==delivered,"privacy request leaked");
        }
        Completion vector=new Completion();send(new TL_stories.TL_stories_readStories(),vector,null);vector.await();
        check(vector.response instanceof Vector,"story read response is not a vector");
        Completion affected=new Completion();send(new TLRPC.TL_messages_readMessageContents(),affected,null);affected.await();
        check(affected.response instanceof TLRPC.TL_messages_affectedMessages,"content-read response type wrong");
        check(((TLRPC.TL_messages_affectedMessages)affected.response).pts==-1,"fake pts changed");
        AtomicInteger stamped=new AtomicInteger();CountDownLatch stampDone=new CountDownLatch(1);
        send(new TLRPC.TL_messages_setTyping(),null,(r,e,t)->{stamped.incrementAndGet();stampDone.countDown();});
        check(stampDone.await(3,TimeUnit.SECONDS)&&stamped.get()==1,"timestamp completion lost");
        ConfigManager.hideSeenChannel.setEnable(false);
        TLRPC.TL_messages_readHistory read=new TLRPC.TL_messages_readHistory();TLRPC.Peer peer=new TLRPC.Peer();peer.channel_id=123;read.peer=peer;
        delivered=connection.delivered.get();send(read,null,null);
        check(connection.delivered.get()==delivered+1,"unhidden channel receipt incorrectly blocked");
        read.peer=new Object();delivered=connection.delivered.get();Completion badPeer=new Completion();send(read,badPeer,null);badPeer.await();
        check(connection.delivered.get()==delivered,"unreadable peer leaked receipt");
        check(PrivacyStatus.hasIssues(),"peer failure was invisible");PrivacyStatus.clear("read-peer");
        ConfigManager.hideSeenChannel.setEnable(true);
        TL_account.updateStatus status=new TL_account.updateStatus();delivered=connection.delivered.get();send(status,null,null);
        check(status.offline&&connection.delivered.get()==delivered+1,"offline mutation blocked ordinary status flow");
        ClassLoad.classes.put(ClassNames.TL_ACCOUNT_UPDATE_STATUS,BrokenOnline.class);PrivacyProtection.prepare();
        Completion broken=new Completion();delivered=connection.delivered.get();send(new BrokenOnline(),broken,null);broken.await();
        check(connection.delivered.get()==delivered&&PrivacyStatus.hasIssues(),"online field failure leaked or was hidden");
        PrivacyStatus.clear("online-field");ClassLoad.classes.put(ClassNames.TL_ACCOUNT_UPDATE_STATUS,TL_account.updateStatus.class);PrivacyProtection.prepare();
        ClassLoad.classes.remove("org.telegram.tgnet.TLRPC$TL_boolTrue");Completion failure=new Completion();delivered=connection.delivered.get();
        send(new TLRPC.TL_messages_setTyping(),failure,null);failure.await();
        check(failure.error!=null&&failure.error.code==503,"local failure did not finish callback with error");
        check(connection.delivered.get()==delivered&&PrivacyStatus.hasIssues(),"response failure leaked or was hidden");
        ClassLoad.classes.put("org.telegram.tgnet.TLRPC$TL_boolTrue",TLRPC.TL_boolTrue.class);PrivacyStatus.clear("completion");
        TLObject permit=new TLRPC.TL_channels_readHistory();HideSeen.permits.allow(permit,connection);delivered=connection.delivered.get();send(permit,null,null);
        check(connection.delivered.get()==delivered+1,"one-shot read permit suppressed");send(permit,null,null);
        check(connection.delivered.get()==delivered+1,"read permit was reused");
        ConfigManager.hideTyping.setEnable(false);delivered=connection.delivered.get();send(new TLRPC.TL_messages_setTyping(),null,null);
        check(connection.delivered.get()==delivered+1,"disabled typing switch still suppresses requests");ConfigManager.hideTyping.setEnable(true);
        MediaOpenScope scope=new MediaOpenScope();AtomicInteger ttl=new AtomicInteger(30);Object message=new Object(),first=new Object(),second=new Object();
        scope.enter(first,message,ttl::get,ttl::set);check(ttl.get()==0&&scope.contains(message),"TTL override missing");
        scope.enter(second,message,ttl::get,ttl::set);scope.exit(second);check(ttl.get()==0&&scope.isActive(),"nested exit restored too early");
        scope.exit(first);check(ttl.get()==30&&!scope.isActive(),"original TTL not restored");
        try {scope.enter(first,message,ttl::get,ttl::set);throw new IllegalStateException("opening failure");}
        catch(IllegalStateException expected) {} finally {scope.exit(first);}
        check(ttl.get()==30&&!scope.isActive(),"exception left TTL overridden");
        check(PrivacyRequestKind.named("example.TL_messages_setTyping")==PrivacyRequestKind.OTHER,"substring classification accepted unrelated class");
        ClassLoad.classes.put(ClassNames.PHOTO_VIEWER,FakeViewer.class);
        com.my.televip.features.otherFeatures.AlwaysSaveMedia.init();
        FakeViewer viewer=new FakeViewer();instrumentation.runOnMainSync(()->viewer.galleryButton=new android.view.View(instrumentation.getTargetContext()));
        Method switchPhoto=FakeViewer.class.getDeclaredMethod("setIsAboutToSwitchToIndex",int.class,boolean.class,boolean.class,boolean.class);
        ConfigManager.secretMediaSave.setEnable(false);ConfigManager.removesContentSaving.setEnable(false);
        XposedBridge.invoke(switchPhoto,viewer,0,false,false,false);
        check(viewer.galleryButton.getVisibility()==android.view.View.INVISIBLE,"saving disabled but button forced visible");
        ConfigManager.secretMediaSave.setEnable(true);XposedBridge.invoke(switchPhoto,viewer,0,false,false,false);
        check(viewer.galleryButton.getVisibility()==android.view.View.VISIBLE,"secret saving button missing");
        ConfigManager.secretMediaSave.setEnable(false);ConfigManager.removesContentSaving.setEnable(true);
        XposedBridge.invoke(switchPhoto,viewer,0,false,false,false);
        check(viewer.galleryButton.getVisibility()==android.view.View.VISIBLE,"restricted-content saving button missing");
        ConfigManager.removesContentSaving.setEnable(false);XposedBridge.invoke(switchPhoto,viewer,0,false,false,false);
        check(viewer.galleryButton.getVisibility()==android.view.View.INVISIBLE,"button did not recover after disabling saving");
        com.my.televip.Database.MessageDatabase db=new com.my.televip.Database.MessageDatabase(instrumentation.getTargetContext(),
                new java.io.File(instrumentation.getTargetContext().getCacheDir(),"priority-lock.db").getPath());
        java.lang.reflect.Field lockField=db.getClass().getDeclaredField("sessionLock");lockField.setAccessible(true);
        Object lock=lockField.get(db);CountDownLatch locked=new CountDownLatch(1),unlock=new CountDownLatch(1);
        Thread holder=new Thread(()->{synchronized(lock){locked.countDown();try{unlock.await();}catch(InterruptedException e){throw new AssertionError(e);}}});
        holder.start();check(locked.await(3,TimeUnit.SECONDS),"writer lock not acquired");
        java.util.concurrent.ExecutorService reader=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {check(reader.submit(()->db.isCurrent(db.session(101))).get(1,TimeUnit.SECONDS),"UI account check waits for storage transaction");}
        finally {unlock.countDown();holder.join(3000);reader.shutdownNow();db.close();}
        return checks;
    }
}
