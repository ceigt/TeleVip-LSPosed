package org.televip.regression;

import android.content.Context;
import android.os.*;
import android.view.View;
import com.my.televip.Class.*;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.Database.LocalStateStore;
import com.my.televip.compat.XposedBridge;
import com.my.televip.features.messages.*;
import com.my.televip.messages.MessageStorage;
import org.telegram.tgnet.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Execute the production lifecycle/hooks against a host that never contacts Telegram. */
final class ArchiveSafetyTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void drain()throws Exception{CountDownLatch done=new CountDownLatch(1);MessageStorage.post(done::countDown);check(done.await(5,TimeUnit.SECONDS),"local queue stalled");}
    public static class Config {public long clientUserId=9301;public void clearConfig(){clientUserId=0;}}
    private static final Config config=new Config();
    public static class Peer {} public static class Encrypted {}
    public static class Request extends TLObject {public int min_date,max_date;}
    public static class Reply extends TLObject {public int offset;Reply(int n){offset=n;}}
    public static class Connection extends ConnectionsManager {
        public int currentAccount=0;
        public Config getUserConfig(){return config;}
        Normal normal;Timestamp timestamp;
        @Override public void sendRequestInternal(TLObject request,Normal normal,Timestamp timestamp,Ack ack,Write write,int a,int b,int c,boolean d,int token){this.normal=normal;this.timestamp=timestamp;}
    }
    private static final Connection connection=new Connection();
    private static Method dispatch;
    public static class Controller {
        public int currentAccount=0,calls;boolean timestamp,throwCallback;
        public Config getUserConfig(){return config;}
        public void deleteMessages(ArrayList<Integer> ids,ArrayList<Long> random,Encrypted secret,long dialog,boolean all,int mode,boolean cache,long task,TLObject request,int topic,boolean moved,int newId){calls++;}
        public void deleteDialogLocal(long dialog,int onlyHistory,int mode,int max,boolean revoke,Peer peer,long task){}
        public void deleteMessagesRange(long dialog,long channel,int min,int max,boolean all,Runnable complete)throws Throwable{
            Request request=new Request();request.min_date=min;request.max_date=max;
            ConnectionsManager.Normal normal=(r,e)->{calls++;if(throwCallback)throw new IllegalStateException("native callback failure");};
            ConnectionsManager.Timestamp timed=(r,e,t)->{calls++;if(throwCallback)throw new IllegalStateException("native timestamp failure");};
            XposedBridge.invoke(dispatch,connection,request,timestamp?null:normal,timestamp?timed:null,null,null,0,0,0,false,1);
        }
    }
    public static class Chat {
        public Bundle arguments=new Bundle();public long threadMessageId=50;public int highlightMessageId=Integer.MAX_VALUE;
        public Config getUserConfig(){return config;}
        public boolean onFragmentCreate(){return true;}
        public View createView(Context context){return null;}
        public void onPause(){}
        Chat(){arguments.putLong("chat_id",44);arguments.putInt("message_id",50);}
    }
    private static LocalStateStore.Record row(long dialog,int mid,int date){return new LocalStateStore.Record(config.clientUserId,dialog,mid,date,1000,"synthetic archive",new byte[8]);}
    private static boolean has(long dialog,int mid){return LocalData.store().records(config.clientUserId).stream().anyMatch(r->r.dialog==dialog&&r.id==mid);}
    private static void installed(Class<?> cls,String field,boolean value)throws Exception{Field f=cls.getDeclaredField(field);f.setAccessible(true);f.setBoolean(null,value);}
    static int run(Context context)throws Throwable{
        checks=0;config.clientUserId=9301;
        Map<String,Class<?>> previous=new HashMap<>();
        String[] names={ClassNames.USER_CONFIG,ClassNames.MESSAGES_CONTROLLER,ClassNames.CONNECTIONS_MANAGER,ClassNames.TLRPC_ENCRYPTED_CHAT,"org.telegram.tgnet.TLRPC$InputPeer",ClassNames.CHAT_ACTIVITY,"org.telegram.tgnet.TLRPC$TL_messages_deleteHistory","org.telegram.tgnet.TLRPC$TL_messages_affectedHistory",ClassNames.TL_OBJECT,ClassNames.REQUEST_DELEGATE,ClassNames.REQUEST_DELEGATE_TIMESTAMP,ClassNames.QUICK_ACK_DELEGATE,ClassNames.WRITE_TO_SOCKET_DELEGATE};
        Class<?>[] types={Config.class,Controller.class,Connection.class,Encrypted.class,Peer.class,Chat.class,Request.class,Reply.class,TLObject.class,ConnectionsManager.Normal.class,ConnectionsManager.Timestamp.class,ConnectionsManager.Ack.class,ConnectionsManager.Write.class};
        for(int i=0;i<names.length;i++)previous.put(names[i],ClassLoad.classes.put(names[i],types[i]));
        boolean remember=ConfigManager.localReadPosition.isEnable(),hidden=ConfigManager.hideSeenChannel.isEnable();
        try{
            dispatch=Connection.class.getDeclaredMethod("sendRequestInternal",TLObject.class,ConnectionsManager.Normal.class,ConnectionsManager.Timestamp.class,ConnectionsManager.Ack.class,ConnectionsManager.Write.class,int.class,int.class,int.class,boolean.class,int.class);
            installed(LocalData.class,"cleanup",false);installed(LocalData.class,"deletes",false);installed(LocalData.class,"clears",false);
            installed(RangeDeletionCleanup.class,"installed",false);LocalData.initCleanup();
            check(getFlag(RangeDeletionCleanup.class,"installed"),"range hooks did not install");
            Controller controller=new Controller();
            Method deletion=Controller.class.getDeclaredMethod("deleteMessages",ArrayList.class,ArrayList.class,Encrypted.class,long.class,boolean.class,int.class,boolean.class,long.class,TLObject.class,int.class,boolean.class,int.class);
            for(int mode:new int[]{0,2,3,7,1,5,9}){
                int mid=100+mode;LocalData.store().save(row(-44,mid,100));
                ByteArrayOutputStream old=new ByteArrayOutputStream();LocalData.store().exportBackup(old,config.clientUserId);
                XposedBridge.invoke(deletion,controller,new ArrayList<>(Collections.singletonList(mid)),null,null,-44L,false,mode,false,0L,null,0,false,0);drain();
                boolean ordinary=mode==0||mode==2||mode==3||mode==7;
                check(has(-44,mid)!=ordinary,"wrong deletion ID namespace: "+mode);
                check(LocalData.store().importBackup(new ByteArrayInputStream(old.toByteArray()),config.clientUserId)==0,"old backup resurrected a user deletion");
                LocalData.store().forget(config.clientUserId,-44,Collections.singletonList(mid));
            }
            Method range=Controller.class.getDeclaredMethod("deleteMessagesRange",long.class,long.class,int.class,int.class,boolean.class,Runnable.class);
            for(boolean timestamp:new boolean[]{false,true}){
                controller.timestamp=timestamp;long dialog=timestamp?-46:-45;
                LocalData.store().save(row(dialog,1,100));LocalData.store().save(row(dialog,2,201));
                XposedBridge.invoke(range,controller,dialog,0L,90,110,false,(Runnable)()->{});
                check(has(dialog,1),"range removed data before server response");
                deliver(timestamp,null,new TLRPC.TL_error());drain();check(has(dialog,1),"failed range deletion lost archive");
                deliver(timestamp,new Reply(1),null);drain();check(has(dialog,1),"partial range response committed final tombstone");
                deliver(timestamp,new Reply(0),null);drain();check(!has(dialog,1)&&has(dialog,2),"success range cleanup was not exact");
                check(!LocalData.store().save(row(dialog,3,100)),"successful date deletion lacks import tombstone");
                LocalData.store().forget(config.clientUserId,dialog,Collections.singletonList(2));
            }
            controller.timestamp=false;
            LocalData.store().save(row(-47,1,100));XposedBridge.invoke(range,controller,-47L,0L,90,110,false,(Runnable)()->{});
            LocalData.store().revokeAccount(9301);LocalData.store().finishAccountCleanup(9301);LocalData.store().save(row(-47,2,100));
            deliver(false,new Reply(0),null);drain();check(has(-47,2),"old response cleaned a new login's data");
            controller.throwCallback=true;XposedBridge.invoke(range,controller,-47L,0L,90,110,false,(Runnable)()->{});
            try{deliver(false,new Reply(0),null);throw new AssertionError("native callback error swallowed");}catch(IllegalStateException expected){checks++;}
            drain();check(!has(-47,2),"confirmed success lost cleanup when native callback threw");
            ConfigManager.localReadPosition.setEnable(true);ConfigManager.hideSeenChannel.setEnable(true);installed(LocalData.class,"restore",false);installed(LocalData.class,"views",false);installed(LocalData.class,"pause",false);LocalData.initReading();
            LocalData.store().savePosition(9301,-44,50,80,70);Method create=Chat.class.getDeclaredMethod("onFragmentCreate");
            Chat automatic=new Chat();XposedBridge.invoke(create,automatic);check(automatic.arguments.getInt("message_id")==70,"native MAX_VALUE topic sentinel not resumed");
            Chat explicit=new Chat();explicit.highlightMessageId=50;XposedBridge.invoke(create,explicit);check(explicit.arguments.getInt("message_id")==50,"explicit topic message was overridden");
            Chat date=new Chat();date.arguments.putInt("start_from_date",123);XposedBridge.invoke(create,date);check(date.arguments.getInt("message_id")==50,"date navigation overridden");
            staging(context);
        }finally{
            LocalData.store().revokeAccount(9301);LocalData.store().finishAccountCleanup(9301);ConfigManager.localReadPosition.setEnable(remember);ConfigManager.hideSeenChannel.setEnable(hidden);
            for(String name:names){Class<?> cls=previous.get(name);if(cls==null)ClassLoad.classes.remove(name);else ClassLoad.classes.put(name,cls);}
        }
        return checks;
    }
    private static boolean getFlag(Class<?> cls,String name)throws Exception{Field f=cls.getDeclaredField(name);f.setAccessible(true);return f.getBoolean(null);}
    private static void deliver(boolean timestamp,TLObject result,TLRPC.TL_error error){if(timestamp)connection.timestamp.run(result,error,9876L);else connection.normal.run(result,error);}
    private static File staged(ArchiveTransfers.Job job)throws Exception{Field f=job.getClass().getDeclaredField("file");f.setAccessible(true);return (File)f.get(job);}
    private static void staging(Context context)throws Exception{
        Field directory=ArchiveTransfers.class.getDeclaredField("directory");directory.setAccessible(true);directory.set(null,null);
        File old=new File(context.getCacheDir(),"televip-archive-crashed.json"),keep=new File(context.getCacheDir(),"unrelated-archive-fixture.txt");
        try(FileOutputStream out=new FileOutputStream(old)){out.write("old plaintext".getBytes(StandardCharsets.UTF_8));}check(keep.createNewFile(),"unrelated fixture exists");
        ArchiveTransfers.initialize(context);check(!old.exists()&&keep.exists(),"startup sweep erased unrelated data or kept legacy plaintext");
        byte[] text="TELEVIP-TEST-private-归档".getBytes(StandardCharsets.UTF_8);
        CountDownLatch closeEntered=new CountDownLatch(1),release=new CountDownLatch(1);
        try(ArchiveTransfers.Job own=ArchiveTransfers.begin(context,9301);ArchiveTransfers.Job other=ArchiveTransfers.begin(context,9302)){
            try(OutputStream out=own.writer()){out.write(text);}try(OutputStream out=other.writer()){out.write(text);}
            byte[] raw=new byte[(int)staged(own).length()];try(InputStream in=new FileInputStream(staged(own))){check(in.read(raw)==raw.length,"staging fixture short read");}
            check(!new String(raw,StandardCharsets.UTF_8).contains("TELEVIP-TEST-private"),"staging file contains plaintext");
            ByteArrayOutputStream decoded=new ByteArrayOutputStream();try(InputStream in=own.reader()){byte[] b=new byte[64];int n;while((n=in.read(b))!=-1)decoded.write(b,0,n);}check(Arrays.equals(text,decoded.toByteArray()),"encrypted staging round trip failed");
            own.track((Closeable)()->{closeEntered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            long start=SystemClock.elapsedRealtime();ArchiveTransfers.cancelAccount(9301);check(SystemClock.elapsedRealtime()-start<500,"provider close blocked logout");
            check(!staged(own).exists()&&staged(other).exists(),"account cancellation erased another operation or kept own file");
            check(closeEntered.await(2,TimeUnit.SECONDS),"provider not closed on cancellation");
            try{own.writer();throw new AssertionError("cancelled writer recreated staging");}catch(IOException expected){checks++;}
            try{own.reader();throw new AssertionError("cancelled operation remained readable");}catch(IOException expected){checks++;}
        }finally{release.countDown();keep.delete();}
    }
}
