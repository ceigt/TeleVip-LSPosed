package org.televip.regression;

import android.content.Context;
import com.my.televip.Database.LocalStateStore;
import com.my.televip.features.messages.LocalData;
import com.my.televip.features.other.BooleanFieldScope;
import com.my.televip.settings.SettingsSearch;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.messages.MessageStorage;
import com.my.televip.virtuals.messenger.MessagesStorage;
import com.my.televip.virtuals.tgnet.NativeByteBuffer;
import com.my.televip.virtuals.tgnet.TLRPC;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

final class IndependentFeatureTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private interface Throwing {void run()throws Exception;}
    private static void rejects(Throwing call,String why)throws Exception {try{call.run();}catch(Exception expected){checks++;return;}throw new AssertionError(why);}
    public static class Parent {private boolean inherited=true;}
    public static final class Flags extends Parent {public boolean blocked=true,already=false;public int wrong;public static boolean global=true;}
    private static LocalStateStore.Record row(long account,long dialog,int id,int date,String text){return new LocalStateStore.Record(account,dialog,id,date,1000+id,text,new byte[16]);}
    private static byte[] backup(LocalStateStore store,long account)throws Exception {ByteArrayOutputStream out=new ByteArrayOutputStream();store.exportBackup(out,account);return out.toByteArray();}
    private static int restore(LocalStateStore store,byte[] data,long account)throws Exception{return store.importBackup(new ByteArrayInputStream(data),account);}
    static int run(Context context)throws Exception {
        checks=0;
        Locale old=Locale.getDefault();try{Locale.setDefault(new Locale("tr","TR"));
            check(SettingsSearch.matches("HISTORY local","Local History","Messages"),"word/case search");
            check(SettingsSearch.matches("阅读 位置","本地阅读位置",null),"Chinese search");
            check(!SettingsSearch.matches("history remote","Local history"),"all words required");
            check(SettingsSearch.matches("  ",null,"anything"),"empty query");
        }finally{Locale.setDefault(old);}
        BooleanFieldScope scope=new BooleanFieldScope();Flags flags=new Flags();Object outer=new Object(),inner=new Object();
        scope.enter(outer);scope.clear(flags,"blocked");scope.clear(flags,"inherited");scope.clear(flags,"already");
        check(!flags.blocked&&!flags.already,"scope clear");scope.enter(inner);scope.clear(flags,"blocked");scope.exit(inner);
        check(!flags.blocked,"nested restored too early");rejects(()->scope.clear(flags,"wrong"),"nonboolean accepted");
        rejects(()->scope.clear(flags,"global"),"static accepted");scope.exit(outer);
        check(flags.blocked&&!flags.already&&Flags.global,"scope restoration changed defaults");
        scope.enter(outer);scope.clear(flags,"blocked");rejects(()->scope.exit(inner),"unbalanced accepted");check(flags.blocked,"mismatch lost restoration");
        scope.enter(outer);scope.clear(flags,"blocked");Thread worker=new Thread(()->scope.exit(outer));worker.start();worker.join();
        check(!flags.blocked,"scope leaked across threads");scope.exit(outer);check(flags.blocked,"owner thread restoration");

        File dir=new File(context.getCacheDir(),"local-"+UUID.randomUUID());check(dir.mkdirs(),"create fixture");
        File path=new File(dir,"state.db");LocalStateStore store=new LocalStateStore(context,path.toString());
        byte[] original;
        try {
            check(store.save(row(101,-20,7,100,"中文 🐈\nfirst")),"initial insert");
            check(!store.save(row(101,-20,7,101,"changed")),"duplicate replaced first copy");
            check(store.save(row(202,-20,7,100,"other account")),"account isolation");
            check(store.save(row(101,-21,7,100,"other dialog")),"dialog isolation");
            original=backup(store,101);String json=new String(original,StandardCharsets.UTF_8);
            check(!json.contains("other account"),"backup exposed another account");
            check(restore(store,original,101)==0,"duplicate import");
            LocalStateStore initial=store; rejects(()->restore(initial,original,303),"foreign account accepted");
            store.forget(101,-20,Collections.singletonList(7));
            check(restore(store,original,101)==0&&store.records(101).size()==1,"old backup resurrected forgotten record");
            store.clearDialog(101,-21,150);check(!store.save(row(101,-21,8,120,"old")),"clear cutoff ignored");
            check(store.save(row(101,-21,8,151,"new")),"new post-clear message rejected");
            store.savePosition(101,-21,0,90,70);store.savePosition(101,-21,0,80,60);store.savePosition(101,-21,3,50,40);
            check(store.position(101,-21,0).read==90&&store.position(101,-21,0).scroll==60,"reading high-water/scroll");
            check(store.position(101,-21,3).scroll==40&&store.position(202,-21,0)==null,"topic/account positions");
            store.close();store=new LocalStateStore(context,path.toString());store.loadPositions();
            check(store.position(101,-21,3).scroll==40,"positions lost after reopen");
            LocalStateStore active=store;
            for(String bad:new String[]{json.replace("\"version\":1","\"version\":2"),json.replace("\"mid\":7","\"mid\":7.5"),json+"x",json.replace("\"date\":100","\"date\":2147483648")}){
                int before=store.records(101).size();rejects(()->restore(active,bad.getBytes(StandardCharsets.UTF_8),101),"invalid backup accepted");
                check(store.records(101).size()==before,"failed import mutated archive");
            }
            LocalStateStore.Session prior=store.session(101);store.revokeAccount(101);
            check(!store.save(prior,row(101,-22,99,200,"late")),"logout accepted queued write");
            LocalStateStore.Session during=store.session(101);
            store.finishAccountCleanup(101);check(store.records(101).isEmpty()&&store.position(101,-21,0)==null,"logout left data");
            check(!store.save(prior,row(101,-22,99,200,"late")),"cleanup revived old token");
            check(!store.save(during,row(101,-22,99,200,"late")),"cleanup revived a revoked-period token");
            check(store.save(store.session(101),row(101,-22,100,201,"new login")),"fresh login cannot record");
            check(store.records(202).size()==1,"logout erased other account");
            rejects(()->active.exportBackup(new OutputStream(){public void write(int value)throws IOException{throw new IOException("expected");}},101),"failed stream swallowed");
            check(store.records(101).size()==1,"failed export changed database");
        }finally{store.close();context.deleteDatabase(path.toString());}
        LocalStateStore empty=new LocalStateStore(context,new File(dir,"import.db").toString());
        try{LocalStateStore clean=empty;
            byte[] invalid=new String(original,StandardCharsets.UTF_8).replace("\"version\":1","\"version\":2").getBytes(StandardCharsets.UTF_8);
            rejects(()->restore(clean,invalid,101),"invalid header committed inserted rows");check(empty.records(101).isEmpty(),"import rollback left earlier valid rows");
            check(restore(empty,original,101)==2,"Unicode round trip count");
            check(empty.records(101).stream().anyMatch(r->r.text.equals("中文 🐈\nfirst")),"Unicode lost");
            empty.revokeAccount(101);empty.close();empty=new LocalStateStore(context,new File(dir,"import.db").toString());
            check(empty.records(101).isEmpty(),"persisted logout cleanup lost");
        }finally{empty.close();context.deleteDatabase(new File(dir,"import.db").toString());}
        LocalStateStore full=new LocalStateStore(context,new File(dir,"full.db").toString());
        try{
            byte[] large=new byte[LocalStateStore.MAX_PAYLOAD];
            for(int n=1;n<=15;n++)check(full.save(new LocalStateStore.Record(101,-99,n,100,1000,"x",large)),"bounded archive insert");
            rejects(()->full.save(new LocalStateStore.Record(101,-99,16,100,1000,"x",large)),"archive exceeded byte budget");
            check(full.records(101).size()==15,"full archive silently evicted data");
            rejects(()->full.save(new LocalStateStore.Record(101,-99,17,100,1000,"x",new byte[LocalStateStore.MAX_PAYLOAD+1])),"oversized payload accepted");
        }finally{full.close();context.deleteDatabase(new File(dir,"full.db").toString());}
        // Exercise the production capture with an isolated host row; never touches the user's host.
        android.os.HandlerThread thread=new android.os.HandlerThread("capture-fixture");thread.start();
        try{
            MessagesStorage host=new MessagesStorage(new android.os.Handler(thread.getLooper()));host.accountId=909;
            NativeByteBuffer data=new NativeByteBuffer();data.previous=new TLRPC.MessageData(55,-77,0,"capture fixture");
            ConfigManager.durableDeletedMessages.setEnable(true);ConfigManager.showDeletedMessages.setEnable(true);
            LocalData.capture(host,-77,55,data);CountDownLatch done=new CountDownLatch(1);MessageStorage.post(done::countDown);
            check(done.await(10,TimeUnit.SECONDS),"capture worker stalled");
            check(LocalData.store().records(909).size()==1&&!data.reused,"production capture missing or stole native buffer");
            data.previous=new TLRPC.MessageData(56,-77,0,"ephemeral");data.previous.ttl=10;
            LocalData.capture(host,-77,56,data);done=new CountDownLatch(1);CountDownLatch barrier=done;MessageStorage.post(barrier::countDown);check(done.await(10,TimeUnit.SECONDS),"ttl worker stalled");
            check(LocalData.store().records(909).size()==1,"ephemeral message archived");
            LocalData.store().revokeAccount(909);LocalData.store().finishAccountCleanup(909);
        }finally{ConfigManager.durableDeletedMessages.setEnable(false);thread.quitSafely();}
        return checks;
    }
}
