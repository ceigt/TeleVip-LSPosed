package org.televip.regression;

import android.os.Handler;
import android.os.HandlerThread;
import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.Database.MessageDatabase;
import com.my.televip.compat.XposedBridge;
import com.my.televip.features.messages.SaveEditsHistory;
import com.my.televip.messages.MessageStorage;
import com.my.televip.virtuals.messenger.MessagesStorage;
import com.my.televip.virtuals.tgnet.TLRPC;
import java.util.*;
import java.util.concurrent.*;

/** Production storage hook + real module SQLite, with a controlled Telegram storage queue. */
public final class Upstream15Tests {
    public static class HostMessage {
        public TLRPC.MessageData messageOwner;
        public static long getDialogId(TLRPC.MessageData message) { return message.dialog; }
    }
    public static class HostConfig { public long clientUserId=101; public void clearConfig() {} }
    public static class HostChat {
        public HostMessage selectedObject;
        public void fillMessageMenu(HostMessage message,ArrayList<?> icons,ArrayList<?> labels,ArrayList<?> options) {}
        public void processSelectedOption(int option) {}
    }
    private static int checks;
    private static void check(boolean condition,String label) { checks++;if(!condition)throw new AssertionError(label); }
    private static void drain(Handler handler) throws Exception {
        CountDownLatch hostDone=new CountDownLatch(1);handler.post(hostDone::countDown);
        if(!hostDone.await(10,TimeUnit.SECONDS))throw new AssertionError("Host queue stuck");
        CountDownLatch moduleDone=new CountDownLatch(1);MessageStorage.post(moduleDone::countDown);
        if(!moduleDone.await(10,TimeUnit.SECONDS))throw new AssertionError("Module queue stuck");
    }
    private static void send(MessagesStorage storage,Handler handler,int load,TLRPC.MessageData... messages) throws Throwable {
        TLRPC.messages_Messages batch=new TLRPC.messages_Messages(new ArrayList<Object>(Arrays.asList(messages)));
        XposedBridge.invoke(MessagesStorage.class.getMethod("putMessages",TLRPC.messages_Messages.class,long.class,int.class,
                int.class,boolean.class,int.class,long.class),storage,batch,0L,load,0,false,0,0L);
        drain(handler);
    }
    private static void old(MessagesStorage storage,int mid,long dialog,String text) {
        storage.database.history.put(mid+":"+dialog,new TLRPC.MessageData(mid,dialog,0,text));
    }
    public static int run() throws Throwable {
        checks=0;
        checks+=com.my.televip.compat.StoryListsTest.run();
        ClassLoad.classes.put(ClassNames.USER_CONFIG,HostConfig.class);
        ClassLoad.classes.put(ClassNames.CHAT_ACTIVITY,HostChat.class);
        ClassLoad.classes.put(ClassNames.MESSAGE_OBJECT,HostMessage.class);
        ClassLoad.classes.put(ClassNames.MESSAGES_STORAGE,MessagesStorage.class);
        ClassLoad.classes.put(ClassNames.TL_MESSAGES_MESSAGES,TLRPC.messages_Messages.class);
        ConfigManager.saveEditsHistory.setEnable(true);
        SaveEditsHistory.init();
        check(SaveEditsHistory.isEnable,"Production history hooks did not install");
        HostMessage selected=new HostMessage();selected.messageOwner=new TLRPC.MessageData(19,-1234567890123L,0,"menu fixture");
        com.my.televip.virtuals.messenger.MessageObject wrapper=new com.my.televip.virtuals.messenger.MessageObject(selected);
        check(wrapper.getDialogId()==-1234567890123L,"Instance wrapper did not use canonical static decoder");
        selected.messageOwner=null;check(wrapper.getDialogId()==0,"Null owner dialog ID");
        selected.messageOwner=new TLRPC.MessageData(19,-1234567890123L,0,"menu fixture");
        HostChat hostChat=new HostChat();hostChat.selectedObject=selected;
        ArrayList<Integer> icons=new ArrayList<>(),options=new ArrayList<>();ArrayList<CharSequence> labels=new ArrayList<>();
        java.lang.reflect.Method menu=HostChat.class.getMethod("fillMessageMenu",HostMessage.class,ArrayList.class,ArrayList.class,ArrayList.class);
        XposedBridge.invoke(menu,hostChat,selected,icons,labels,options);
        check(options.contains(8353847)&&labels.size()==1,"Production history menu missing on optimized message wrapper");
        XposedBridge.invoke(menu,hostChat,selected,icons,labels,options);check(labels.size()==1,"History menu duplicated");
        java.lang.reflect.Field field=SaveEditsHistory.class.getDeclaredField("database");field.setAccessible(true);
        MessageDatabase db=(MessageDatabase)field.get(null);
        HandlerThread thread=new HandlerThread("History capture host");thread.start();Handler handler=new Handler(thread.getLooper());
        try {
            MessagesStorage first=new MessagesStorage(handler);first.database.history=new HashMap<>();
            MessageDatabase.AccountSession session=db.session(101);
            old(first,7,-100,"before-offline-edit");
            send(first,handler,0,new TLRPC.MessageData(7,-100,1<<15,"after-offline-edit"));
            check(db.getHistory(session,-100,7).size()==1,"Offline group edit not captured");
            check(db.getHistory(session,-100,7).get(0).text.equals("before-offline-edit"),"Host write raced old text capture");
            old(first,8,-100,"unedited-old");int queries=first.database.queries.size();
            send(first,handler,0,new TLRPC.MessageData(8,-100,0,"new-load"));
            check(first.database.queries.size()==queries,"Unedited history load queried host storage");
            check(db.getHistory(session,-100,8).isEmpty(),"Unedited history load invented an edit");
            old(first,9,-100,"live-old");send(first,handler,-2,new TLRPC.MessageData(9,-100,0,"live-new"));
            check(db.getHistory(session,-100,9).get(0).text.equals("live-old"),"Live edit without flag lost");
            old(first,7,-200,"channel-old");send(first,handler,2,new TLRPC.MessageData(7,-200,1<<15,"channel-new"));
            check(db.getHistory(session,-200,7).get(0).text.equals("channel-old"),"Channel history not separated");
            check(db.getHistory(session,-100,7).get(0).text.equals("before-offline-edit"),"Other dialog history changed");
            MessagesStorage second=new MessagesStorage(handler);second.accountId=202;second.database.history=new HashMap<>();
            old(second,7,-100,"other-account-old");send(second,handler,1,new TLRPC.MessageData(7,-100,1<<15,"other-account-new"));
            check(db.getHistory(db.session(202),-100,7).get(0).text.equals("other-account-old"),"Account history mixed");
            check(db.getHistory(session,-100,7).size()==1,"First account history changed");
            send(first,handler,0,new TLRPC.MessageData(99,-100,1<<15,"never-cached"));
            check(db.getHistory(session,-100,99).isEmpty(),"Missing cached copy invented an old version");
            old(first,10,-100,"same");send(first,handler,0,new TLRPC.MessageData(10,-100,1<<15,"same"));
            check(db.getHistory(session,-100,10).isEmpty(),"Unchanged edited copy created history");
            ConfigManager.saveEditsHistory.setEnable(false);queries=first.database.queries.size();
            old(first,11,-100,"disabled-old");send(first,handler,-2,new TLRPC.MessageData(11,-100,1<<15,"disabled-new"));
            check(first.database.queries.size()==queries,"Disabled recording queried host storage");
            check(db.getHistory(session,-100,11).isEmpty(),"Disabled recording wrote history");
            ConfigManager.saveEditsHistory.setEnable(true);
            // Actual 13.2 prototype: the payload moves to index 5, regardless of client branding.
            com.my.televip.obfuscate.ArgsResolver.groupedStorage=true;
            java.lang.reflect.Field storageHook=SaveEditsHistory.class.getDeclaredField("storageHook");storageHook.setAccessible(true);storageHook.setBoolean(null,false);
            SaveEditsHistory.init();
            java.lang.reflect.Method grouped=MessagesStorage.class.getMethod("putMessages",int.class,int.class,int.class,long.class,long.class,TLRPC.messages_Messages.class,boolean.class);
            old(first,12,-100,"grouped-live-old");
            XposedBridge.invoke(grouped,first,-2,0,0,-100L,0L,new TLRPC.messages_Messages(new ArrayList<Object>(Arrays.asList(new TLRPC.MessageData(12,-100,0,"grouped-live-new")))),false);
            drain(handler);
            check(db.getHistory(session,-100,12).get(0).text.equals("grouped-live-old"),"Grouped live edit lost or read wrong integer");
            old(first,13,-100,"grouped-offline-old");
            XposedBridge.invoke(grouped,first,0,-2,0,-100L,0L,new TLRPC.messages_Messages(new ArrayList<Object>(Arrays.asList(new TLRPC.MessageData(13,-100,1<<15,"grouped-offline-new")))),false);
            drain(handler);
            check(db.getHistory(session,-100,13).get(0).text.equals("grouped-offline-old"),"Grouped offline edit payload lost");
            com.my.televip.obfuscate.ArgsResolver.groupedStorage=false;
            old(first,20,-100,"own-local-A");
            TLRPC.MessageData local=new TLRPC.MessageData(20,-100,0,"own-local-B");local.send_state=3;
            final TLRPC.MessageData optimistic=local;
            java.lang.reflect.Method localWrite=MessagesStorage.class.getMethod("putLocalMessagesStored",ArrayList.class,boolean.class,boolean.class,int.class,boolean.class,int.class,long.class);
            handler.post(()->{try{XposedBridge.invoke(localWrite,first,new ArrayList<Object>(Arrays.asList(optimistic)),false,false,0,false,0,0L);}catch(Throwable error){throw new AssertionError(error);}});
            drain(handler);check(db.getHistory(session,-100,20).size()==1&&db.getHistory(session,-100,20).get(0).text.equals("own-local-A"),"Own optimistic edit lost prior text");
            old(first,21,-100,"regular-A");local=new TLRPC.MessageData(21,-100,1<<15,"scheduled-B");final TLRPC.MessageData scheduled=local;
            handler.post(()->{try{XposedBridge.invoke(localWrite,first,new ArrayList<Object>(Arrays.asList(scheduled)),false,false,0,false,1,0L);}catch(Throwable error){throw new AssertionError(error);}});
            drain(handler);check(db.getHistory(session,-100,21).isEmpty(),"Scheduled local write mixed into regular history");
            for(com.my.televip.virtuals.SQLite.SQLiteCursor cursor:first.database.cursors) {
                check(cursor.disposed,"History cursor leaked");
                if(cursor.data.previous!=null)check(cursor.data.reused,"History byte buffer leaked");
            }
        } finally {ConfigManager.saveEditsHistory.setEnable(true);thread.quitSafely();db.close();}
        return checks;
    }
}
