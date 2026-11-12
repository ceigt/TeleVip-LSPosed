package com.my.televip.features.other;

import android.content.SharedPreferences;
import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.application.ApplicationLoaderHook;
import com.my.televip.application.AndroidUtilities;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.compat.XposedHelpers;
import com.my.televip.hooks.HMethod;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.messenger.BaseController;
import com.my.televip.virtuals.messenger.MessagesStorage;
import com.my.televip.virtuals.messenger.UserConfig;
import com.my.televip.virtuals.messenger.MessageObject;
import com.my.televip.virtuals.tgnet.TLRPC;
import com.my.televip.virtuals.SQLite.SQLiteCursor;
import com.my.televip.logging.Logger;
import java.lang.reflect.*;
import java.util.*;

/** Apply the host's normal mute action once, after a successful join or first bot start. */
public final class AutoMuteNewChats {
    private static boolean installed, membership;
    private static final long started=System.currentTimeMillis()/1000;
    private AutoMuteNewChats(){}
    private static boolean enabled(){return ConfigManager.autoMuteNewChats!=null&&ConfigManager.autoMuteNewChats.isEnable();}
    private static Object field(Object object,String owner,String name){return XposedHelpers.getObjectField(object,Obfuscate.getFieldName(owner,name));}
    private static boolean type(Object object,String name){Class<?> cls=ClassLoad.getClass("org.telegram.tgnet.TLRPC$"+name);return cls!=null&&cls.isInstance(object);}
    public static boolean startCommand(String text){return text!=null&&(text.equals("/start")||text.startsWith("/start "));}
    private static SharedPreferences applied(){return ApplicationLoaderHook.getApplicationContext().getSharedPreferences("TeleVipMuteDefaults",0);}
    public static synchronized void init(){
        if(!membership)membership=HMethod.hookMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER),Obfuscate.getMethodName("MessagesController","updateInterfaceWithMessages"),ArgsResolver.merge("updateInterfaceWithMessages",new Class[]{long.class,ArrayList.class,int.class},new BaseMethodHook(){
            @Override protected void afterMethod(MethodHookParam p){
                if(!enabled()||p.hasThrowable())return;
                int mode=0;long dialog=0;ArrayList<?> messages=null;
                for(Object arg:p.args){if(arg instanceof Integer)mode=(int)arg;else if(arg instanceof Long)dialog=(long)arg;else if(arg instanceof ArrayList)messages=(ArrayList<?>)arg;}
                if(mode!=0||dialog>=0||messages==null)return;
                try {
                    BaseController owner=new BaseController(p.thisObject);long account=owner.getUserConfig().getClientUserId();
                    for(Object item:messages){
                        TLRPC.Message message=new MessageObject(item).getMessageOwner();Object raw=message.get_Message();
                        if(((Number)field(raw,"TLRPC$Message","date")).longValue()<started)continue;
                        Object action=field(raw,"TLRPC$Message","action");boolean joined=false;
                        if(type(action,"TL_messageActionChatAddUser")||type(action,"TL_messageActionChatCreate"))
                            joined=((ArrayList<?>)field(action,"TLRPC$MessageAction","users")).contains(account);
                        else if(type(action,"TL_messageActionChatJoinedByLink")||type(action,"TL_messageActionChannelCreate")){
                            Object from=field(raw,"TLRPC$Message","from_id");joined=from!=null&&((Number)field(from,"TLRPC$Peer","user_id")).longValue()==account;
                        }
                        if(joined){mute(owner.getCurrentAccount(),account,dialog);break;}
                    }
                }catch(Throwable error){Logger.e(error);}
            }
        }));
        if(installed)return;
        installed=HMethod.hookMethod(ClassLoad.getClass(ClassNames.CONNECTIONS_MANAGER),Obfuscate.getMethodName("ConnectionsManager","sendRequestInternal"),
            ArgsResolver.merge("sendRequestInternal",new Class[]{ClassLoad.getClass(ClassNames.TL_OBJECT),ClassLoad.getClass(ClassNames.REQUEST_DELEGATE),ClassLoad.getClass(ClassNames.REQUEST_DELEGATE_TIMESTAMP),ClassLoad.getClass(ClassNames.QUICK_ACK_DELEGATE),ClassLoad.getClass(ClassNames.WRITE_TO_SOCKET_DELEGATE),int.class,int.class,int.class,boolean.class,int.class},new BaseMethodHook(){
                @Override protected void beforeMethod(MethodHookParam p){if(enabled())try{wrap(p);}catch(Throwable error){Logger.e(error);}}
            }));
    }
    private static void wrap(BaseMethodHook.MethodHookParam p)throws Exception {
        Object request=p.args[0];boolean join=type(request,"TL_channels_joinChannel"),invite=type(request,"TL_messages_importChatInvite");
        boolean start=type(request,"TL_messages_startBot"),send=type(request,"TL_messages_sendMessage");
        if(!join&&!invite&&!start&&!send)return;
        BaseController owner=new BaseController(p.thisObject);int slot=owner.getCurrentAccount();long account=owner.getUserConfig().getClientUserId();
        if(account<=0)return;
        long dialog=0;
        if(join){Object channel=field(request,"TLRPC$TL_channels_joinChannel","channel");dialog=-((Number)field(channel,"TLRPC$InputChannel","channel_id")).longValue();}
        if(start||send){
            if(send&&!startCommand((String)field(request,"TLRPC$TL_messages_sendMessage","message")))return;
            Object peer=field(request,start?"TLRPC$TL_messages_startBot":"TLRPC$TL_messages_sendMessage","peer");
            dialog=((Number)field(peer,"TLRPC$InputPeer","user_id")).longValue();if(dialog<=0)return;
            Object controller=XposedHelpers.callStaticMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER),Obfuscate.getMethodName("MessagesController","getInstance"),slot);
            Object user=XposedHelpers.callMethod(controller,Obfuscate.getMethodName("MessagesController","getUser"),dialog);
            if(user==null||!Boolean.TRUE.equals(field(user,"TLRPC$User","bot")))return;
        }
        final long target=dialog;final boolean bot=start||send;
        Pending pending=new Pending(slot,account,target,bot);
        if(bot){
            SharedPreferences notifications=(SharedPreferences)XposedHelpers.callStaticMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER),Obfuscate.getMethodName("MessagesController","getNotificationsSettings"),slot);
            if(notifications.contains("notify2_"+target))return;
            MessagesStorage storage=MessagesStorage.getInstance(slot);
            storage.getStorageQueue().postRunnable(()->{
                SQLiteCursor cursor=null;
                try{cursor=storage.getDatabase().queryFinalized("SELECT 1 FROM dialogs WHERE did="+target+" AND last_mid>0 UNION ALL SELECT 1 FROM messages_v2 WHERE uid="+target+" AND mid>0 LIMIT 1",new Object[0]);pending.fresh(!cursor.next());}
                catch(Throwable error){Logger.e(error);pending.fresh(false);}finally{if(cursor!=null)cursor.dispose();}
            });
        }
        for(int index:new int[]{1,2}){
            Object callback=p.args[index];if(callback==null)continue;
            Class<?> iface=ClassLoad.getClass(index==1?ClassNames.REQUEST_DELEGATE:ClassNames.REQUEST_DELEGATE_TIMESTAMP);
            if(iface==null||!iface.isInstance(callback))continue;
            p.args[index]=Proxy.newProxyInstance(iface.getClassLoader(),new Class<?>[]{iface},(proxy,method,args)->{
                if(method.getDeclaringClass()==Object.class){if(method.getName().equals("equals"))return args!=null&&args.length==1&&proxy==args[0];if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);return "TeleVip mute callback";}
                // Preserve callback arguments, timestamp, result and exceptions exactly.
                try{return method.invoke(callback,args);}catch(InvocationTargetException error){throw error.getCause();}
                finally{if(args!=null&&args.length>=2)try{pending.result(args[0],args[1]);}catch(Throwable error){Logger.e(error);}}
            });
        }
    }
    private static final class Pending {
        final int slot;final long account,target;final boolean bot;
        Boolean fresh;boolean completed,success,dispatched;Object response;
        Pending(int slot,long account,long target,boolean bot){this.slot=slot;this.account=account;this.target=target;this.bot=bot;fresh=bot?null:true;}
        synchronized void fresh(boolean value){fresh=value;dispatch();}
        synchronized void result(Object value,Object error){if(completed)return;completed=true;success=error==null&&value!=null;response=value;dispatch();}
        private void dispatch(){
            if(dispatched||!completed||fresh==null)return;dispatched=true;if(!success||!fresh)return;
            Set<Long> dialogs=new HashSet<>();
            if(bot)dialogs.add(target);
            else {
                Object updates=response;
                if(type(updates,"TL_chatInviteJoinResultOk"))updates=field(updates,"TLRPC$TL_chatInviteJoinResultOk","updates");
                if(!type(updates,"Updates"))return; // Web challenges and pending join approval are not successful membership.
                for(Object chat:(ArrayList<?>)field(updates,"TLRPC$Updates","chats")){
                    long id=((Number)field(chat,"TLRPC$Chat","id")).longValue();
                    if(id>0&&(target==0||target==-id)&&!Boolean.TRUE.equals(field(chat,"TLRPC$Chat","left")))dialogs.add(-id);
                }
            }
            AndroidUtilities.runOnUIThread(()->{for(long dialog:dialogs)mute(slot,account,dialog);});
        }
    }
    private static synchronized void mute(int slot,long account,long dialog){
        if(!enabled()||UserConfig.getInstance(slot).getClientUserId()!=account||dialog==0)return;
        String key=account+":"+dialog;if(applied().contains(key))return;
        try{
            Object notifications=XposedHelpers.callStaticMethod(ClassLoad.getClass("org.telegram.messenger.NotificationsController"),Obfuscate.getMethodName("NotificationsController","getInstance"),slot);
            XposedHelpers.callMethod(notifications,Obfuscate.getMethodName("NotificationsController","muteDialog"),dialog,0L,true);
            applied().edit().putBoolean(key,true).apply();
        }catch(Throwable error){Logger.e(error);}
    }
}
