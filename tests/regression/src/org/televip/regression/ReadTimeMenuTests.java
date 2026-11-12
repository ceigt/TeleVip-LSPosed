package org.televip.regression;
import android.app.*;
import android.content.Intent;
import android.os.*;
import android.widget.TextView;
import com.my.televip.Class.*;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.features.messages.*;
import com.my.televip.application.AndroidUtilities;
import com.my.televip.virtuals.messenger.MessageObject;
import com.my.televip.virtuals.messenger.MessagesController;
import com.my.televip.virtuals.tgnet.TLRPC;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

final class ReadTimeMenuTests {
    public interface Delegate {void run(Object response,Object error);}
    public static class Request {public Object peer;public int msg_id;}
    public static class Reply {public int date=1500;}
    public static class Config {public static int selectedAccount=1;public long clientUserId=8101;public static Config getInstance(int slot){return slot==1?own:other;}}
    private static final Config own=new Config(),other=new Config();
    public static class Fragment {public int currentAccount=1;public Config getUserConfig(){return own;}}
    public static class RawMessage {
        public TLRPC.MessageData messageOwner=new TLRPC.MessageData(91,202,0,"synthetic query");
        public static long getDialogId(TLRPC.MessageData data){return data.dialog;}
    }
    public static class Controller extends MessagesController.Native {
        Controller(){super(com.my.televip.virtuals.messenger.MessagesStorage.instance);}
        public static Controller getInstance(int slot){return new Controller();}
        public Object getInputPeerJ(long dialog){return new Object();}
    }
    public static class Connection {
        public static Connection getInstance(int slot){return connection;}
        public int getCurrentTime(){return 2000;}
        public int sendRequest(Object request,Object callback){delegate=(Delegate)callback;return ++sent;}
        public void cancelRequest(int id,boolean notify){cancelled++;}
    }
    private static Fragment retained;
    private static final Connection connection=new Connection();private static Delegate delegate;private static int sent,cancelled,checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    @SuppressWarnings("unchecked") private static AlertDialog box()throws Exception{
        Field f=ArchiveUi.class.getDeclaredField("windows");f.setAccessible(true);
        for(AlertDialog d:((Map<AlertDialog,Long>)f.get(null)).keySet())if(d.isShowing())return d;
        return null;
    }
    private static ReadTimeCache cache()throws Exception{Field f=ReadTimeMenu.class.getDeclaredField("cache");f.setAccessible(true);return (ReadTimeCache)f.get(null);}
    static int run(Instrumentation instrument)throws Exception{
        checks=0;other.clientUserId=8202;Config.selectedAccount=1;sent=cancelled=0;AndroidUtilities.init();
        Map<String,Class<?>> previous=new HashMap<>();
        String[] names={ClassNames.USER_CONFIG,ClassNames.CONNECTIONS_MANAGER,ClassNames.MESSAGES_CONTROLLER,ClassNames.MESSAGE_OBJECT,ClassNames.REQUEST_DELEGATE,"org.telegram.tgnet.TLRPC$TL_messages_getOutboxReadDate","org.telegram.tgnet.TLRPC$TL_outboxReadDate"};
        Class<?>[] types={Config.class,Connection.class,Controller.class,RawMessage.class,Delegate.class,Request.class,Reply.class};
        for(int i=0;i<names.length;i++)previous.put(names[i],ClassLoad.classes.put(names[i],types[i]));
        Activity activity=instrument.startActivitySync(new Intent(instrument.getTargetContext(),DialogTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Method query=ReadTimeMenu.class.getDeclaredMethod("query",Activity.class,Object.class,MessageObject.class);query.setAccessible(true);
        Fragment fragment=new Fragment();retained=fragment;MessageObject message=new MessageObject(new RawMessage());
        Runnable ask=()->{try{cache().clearAccount(8101);query.invoke(null,activity,fragment,message);}catch(Exception error){throw new IllegalStateException(error);}};
        ConfigManager.showReadTime.setEnable(true);
        try{
            instrument.runOnMainSync(ask);check(sent==1,"production coordinator did not dispatch");
            instrument.runOnMainSync(()->{try{box().dismiss();}catch(Exception e){throw new IllegalStateException(e);}});instrument.waitForIdleSync();
            check(cancelled==1,"dismiss did not cancel native request");
            instrument.runOnMainSync(ask);Delegate callback=delegate;callback.run(new Reply(),null);instrument.waitForIdleSync();
            check(cache().get(8101,202,91,SystemClock.elapsedRealtime()).date==1500,"actual delegate completion lost result");
            ArchiveUi.dismissAccount(8101);instrument.waitForIdleSync();
            instrument.runOnMainSync(ask);Config.selectedAccount=2;delegate.run(new Reply(),null);
            CountDownLatch switched=new CountDownLatch(1);AndroidUtilities.applicationHandler.postDelayed(switched::countDown,450);check(switched.await(3,TimeUnit.SECONDS),"window guard stalled");
            instrument.runOnMainSync(()->{try{check(box()==null,"old account window remained visible");}catch(Exception e){throw new IllegalStateException(e);}});
            Config.selectedAccount=1;instrument.runOnMainSync(ask);
            Field pending=ReadTimeCache.class.getDeclaredField("pending");pending.setAccessible(true);
            Object token=((Map<?,?>)pending.get(cache())).values().iterator().next();Field deadline=token.getClass().getDeclaredField("deadline");deadline.setAccessible(true);deadline.setLong(token,0);
            delegate.run(new Reply(),null);instrument.waitForIdleSync();
            CountDownLatch timeout=new CountDownLatch(1);AndroidUtilities.applicationHandler.postDelayed(timeout::countDown,30500);check(timeout.await(35,TimeUnit.SECONDS),"timeout did not execute");
            instrument.runOnMainSync(()->{try{
                AlertDialog d=box();android.view.ViewGroup root=(android.view.ViewGroup)d.findViewById(android.R.id.custom);
                check(contains(root,"ReadTimeUnavailable"),"expired callback orphaned loading label");
            }catch(Exception e){throw new IllegalStateException(e);}});
        }finally{
            retained=null;Config.selectedAccount=1;ArchiveUi.dismissAccount(0);instrument.waitForIdleSync();ConfigManager.showReadTime.setEnable(false);
            instrument.runOnMainSync(activity::finish);
            for(String name:names){Class<?> old=previous.get(name);if(old==null)ClassLoad.classes.remove(name);else ClassLoad.classes.put(name,old);}
        }
        return checks;
    }
    private static boolean contains(android.view.View view,String text){
        if(view instanceof TextView&&((TextView)view).getText().toString().equals(text))return true;
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(contains(group.getChildAt(i),text))return true;}
        return false;
    }
}
