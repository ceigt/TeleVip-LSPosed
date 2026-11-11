package org.televip.regression;

import android.app.Instrumentation;
import android.os.*;
import com.my.televip.Class.*;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.compat.XC_MethodHook.MethodHookParam;
import com.my.televip.features.other.AutoMuteNewChats;
import com.my.televip.virtuals.messenger.MessagesStorage;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Real request wrapper, isolated native delegates/notification action; sends no requests. */
final class AutoMuteTests {
    public static class TL {}
    public static class Error {}
    public interface Delegate {void run(TL response,Error error);}
    public interface Timed {void run(TL response,Error error,long time);}
    public static class Peer {public long user_id;Peer(long id){user_id=id;}}
    public static class Channel {public long channel_id;Channel(long id){channel_id=id;}}
    public static class Join extends TL {public Channel channel=new Channel(120);}
    public static class Invite extends TL {}
    public static class Start extends TL {public Peer peer=new Peer(999);}
    public static class Send extends TL {public Peer peer=new Peer(999);public String message="/start";}
    public static class Chat {public long id;public boolean left;Chat(long id){this.id=id;}}
    public static class Updates extends TL {public ArrayList<Chat> chats=new ArrayList<>();}
    public static class Joined extends TL {public Updates updates=new Updates();}
    public static class Config {public long clientUserId=3031;public static Config getInstance(int slot){return config;}}
    public static class Owner {public int currentAccount=2;public Config getUserConfig(){return config;}}
    public static class User {public boolean bot=true;}
    public static class Controller {public static Controller getInstance(int slot){return new Controller();}public User getUser(Long id){return user;}}
    public static class Notifications {public static Notifications getInstance(int slot){check(slot==2,"wrong owning account");return new Notifications();}public void muteDialog(long dialog,long topic,boolean mute){check(topic==0&&mute,"wrong native mute args");muted.add(dialog);}}
    private static final Config config=new Config();private static final User user=new User();
    private static final List<Long> muted=new ArrayList<>();private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void type(String name,Class<?> cls){ClassLoad.classes.put("org.telegram.tgnet.TLRPC$"+name,cls);}
    private static MethodHookParam wrap(TL request,Object callback,boolean timed)throws Exception {
        MethodHookParam p=new MethodHookParam();p.thisObject=new Owner();p.args=new Object[]{request,timed?null:callback,timed?callback:null,null,null,0,0,0,false,0};
        Method method=AutoMuteNewChats.class.getDeclaredMethod("wrap",MethodHookParam.class);method.setAccessible(true);method.invoke(null,p);return p;
    }
    private static void idle(Instrumentation i)throws Exception{CountDownLatch done=new CountDownLatch(1);MessagesStorage.instance.getStorageQueue().postRunnable(done::countDown);check(done.await(10,TimeUnit.SECONDS),"host query stalled");i.runOnMainSync(()->{});}
    static int run(Instrumentation i)throws Exception {
        checks=0;muted.clear();com.my.televip.application.AndroidUtilities.init();i.getTargetContext().getSharedPreferences("TeleVipMuteDefaults",0).edit().clear().commit();
        type("TL_channels_joinChannel",Join.class);type("TL_messages_importChatInvite",Invite.class);type("TL_messages_startBot",Start.class);type("TL_messages_sendMessage",Send.class);
        type("TL_chatInviteJoinResultOk",Joined.class);type("Updates",Updates.class);
        ClassLoad.classes.put(ClassNames.USER_CONFIG,Config.class);ClassLoad.classes.put(ClassNames.MESSAGES_CONTROLLER,Controller.class);
        ClassLoad.classes.put(ClassNames.REQUEST_DELEGATE,Delegate.class);ClassLoad.classes.put(ClassNames.REQUEST_DELEGATE_TIMESTAMP,Timed.class);
        ClassLoad.classes.put("org.telegram.messenger.NotificationsController",Notifications.class);
        HandlerThread thread=new HandlerThread("mute-query-fixture");thread.start();MessagesStorage.instance=new MessagesStorage(new Handler(thread.getLooper()));
        ConfigManager.autoMuteNewChats.setEnable(true);
        try {
            Joined ok=new Joined();ok.updates.chats.add(new Chat(120));ok.updates.chats.add(new Chat(9999));
            int[] callbacks={0};Delegate original=(response,error)->{check(response==ok&&error==null,"native callback args changed");callbacks[0]++;};
            MethodHookParam p=wrap(new Join(),original,false);((Delegate)p.args[1]).run(ok,null);idle(i);
            check(callbacks[0]==1&&muted.equals(Arrays.asList(-120L)),"successful join target/dedup");
            ((Delegate)p.args[1]).run(ok,null);idle(i);check(muted.size()==1,"repeat callback remuted");
            MethodHookParam failure=wrap(new Invite(),(Delegate)(response,error)->{},false);((Delegate)failure.args[1]).run(null,new Error());idle(i);check(muted.size()==1,"failed join muted");
            MethodHookParam challenge=wrap(new Invite(),(Delegate)(response,error)->{},false);((Delegate)challenge.args[1]).run(new TL(),null);idle(i);check(muted.size()==1,"web challenge treated as joined");
            Updates invited=new Updates();invited.chats.add(new Chat(121));Chat left=new Chat(122);left.left=true;invited.chats.add(left);
            long[] time={0};MethodHookParam timed=wrap(new Invite(),(Timed)(response,error,stamp)->time[0]=stamp,true);((Timed)timed.args[2]).run(invited,null,123456L);idle(i);
            check(time[0]==123456L&&muted.contains(-121L)&&!muted.contains(-122L),"timestamp/left-member handling");
            MessagesStorage.instance.database.empty=false;
            MethodHookParam existing=wrap(new Start(),(Delegate)(response,error)->{},false);((Delegate)existing.args[1]).run(ok,null);idle(i);check(!muted.contains(999L),"existing bot muted");
            MessagesStorage.instance.database.empty=true;
            MethodHookParam first=wrap(new Send(),(Delegate)(response,error)->{},false);((Delegate)first.args[1]).run(ok,null);idle(i);check(muted.contains(999L),"first bot start not muted");
            int before=muted.size();user.bot=false;MethodHookParam human=wrap(new Send(),original,false);check(human.args[1]==original,"human /start wrapped");user.bot=true;
            check(AutoMuteNewChats.startCommand("/start a")&&!AutoMuteNewChats.startCommand("/starter")&&!AutoMuteNewChats.startCommand(null),"bot command boundary");
            config.clientUserId=4042;MethodHookParam changed=wrap(new Invite(),(Delegate)(response,error)->{},false);config.clientUserId=3031;((Delegate)changed.args[1]).run(invited,null);idle(i);check(muted.size()==before,"account-switch callback muted other owner");
            for(com.my.televip.virtuals.SQLite.SQLiteCursor cursor:MessagesStorage.instance.database.cursors)check(cursor.disposed,"mute query cursor leaked");
        }finally{ConfigManager.autoMuteNewChats.setEnable(false);thread.quitSafely();}
        return checks;
    }
}
