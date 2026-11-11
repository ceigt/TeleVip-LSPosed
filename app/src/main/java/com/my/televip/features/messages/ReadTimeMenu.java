package com.my.televip.features.messages;

import android.app.*;
import android.os.*;
import android.widget.*;
import com.my.televip.Class.*;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.compat.XposedHelpers;
import com.my.televip.hooks.HMethod;
import com.my.televip.language.Translator;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.*;
import com.my.televip.application.AndroidUtilities;
import com.my.televip.utils.Utils;
import com.my.televip.virtuals.SettingsIconResolver;
import com.my.televip.virtuals.messenger.*;
import com.my.televip.virtuals.tgnet.*;
import com.my.televip.virtuals.ui.BaseFragment;
import com.my.televip.virtuals.ui.ChatActivity;
import java.lang.ref.WeakReference;
import java.lang.reflect.Proxy;
import java.text.DateFormat;
import java.util.*;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicit user query only; never sends a read receipt or changes either account's privacy. */
public final class ReadTimeMenu {
    private static final int OPTION=8353848;
    private static boolean cleanup,menu,click;
    private static final ReadTimeCache cache=new ReadTimeCache();
    private ReadTimeMenu(){}
    private static boolean enabled(){return ConfigManager.showReadTime!=null&&ConfigManager.showReadTime.isEnable();}
    private static Object field(Object o,String owner,String key){return XposedHelpers.getObjectField(o,Obfuscate.getFieldName(owner,key));}
    public static synchronized void initCleanup(){
        if(!cleanup)cleanup=HMethod.hookMethod(ClassLoad.getClass(ClassNames.USER_CONFIG),Obfuscate.getMethodName("UserConfig","clearConfig"),new BaseMethodHook(){
            @Override protected void beforeMethod(MethodHookParam p){cache.clearAccount(new UserConfig(p.thisObject).getClientUserId());}
        });
    }
    private static MessageObject eligible(Object fragment){
        if(!enabled())return null;
        MessageObject selected=new ChatActivity(fragment).getSelectedObject();
        if(selected==null||selected.getMessageOwner()==null)return null;
        com.my.televip.virtuals.tgnet.TLRPC.Message owner=selected.getMessageOwner();Object raw=owner.get_Message();
        Bundle args=(Bundle)field(fragment,"BaseFragment","arguments");
        long dialog=selected.getDialogId();int slot=((Number)field(fragment,"BaseFragment","currentAccount")).intValue();
        long account=UserConfig.getInstance(slot).getClientUserId();
        if(account==0||UserConfig.getSelectedAccount()!=slot)return null;
        if(args==null||args.getInt("enc_id",0)!=0||args.getInt("chatMode",0)!=0||dialog<=0||dialog==account||owner.getId()<=0||owner.getTtl()!=0)return null;
        if(!Boolean.TRUE.equals(field(raw,"TLRPC$Message","out"))||Boolean.TRUE.equals(field(raw,"TLRPC$Message","unread"))||Boolean.TRUE.equals(field(raw,"TLRPC$Message","media_unread"))
                ||((Number)field(raw,"TLRPC$Message","send_state")).intValue()!=0||field(raw,"TLRPC$Message","action")!=null)return null;
        Object media=field(raw,"TLRPC$Message","media");if(media!=null&&((Number)field(media,"TLRPC$MessageMedia","ttl_seconds")).intValue()!=0)return null;
        Object controller=MessagesController.getInstance(slot).getInstanceObject();
        Object user=XposedHelpers.callMethod(controller,Obfuscate.getMethodName("MessagesController","getUser"),Long.valueOf(dialog));
        if(user==null||Boolean.TRUE.equals(field(user,"TLRPC$User","bot")))return null;
        Object full=MessagesController.getInstance(slot).getUserFull(dialog);if(full!=null&&Boolean.TRUE.equals(field(full,"TLRPC$UserFull","read_dates_private")))return null;
        int sent=((Number)field(raw,"TLRPC$Message","date")).intValue();
        int period=((Number)field(controller,"MessagesController","pmReadDateExpirePeriod")).intValue();
        long age=(long)ConnectionsManager.getInstance(slot).getCurrentTime()-sent;
        return sent>0&&period>0&&age>=0&&age<period?selected:null;
    }
    public static synchronized void init(){
        initCleanup();
        if(!menu)menu=HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_ACTIVITY),Obfuscate.getMethodName("ChatActivity","fillMessageMenu"),
                ArgsResolver.merge("fillMessageMenu",new Class[]{ClassLoad.getClass(ClassNames.MESSAGE_OBJECT),ArrayList.class,ArrayList.class,ArrayList.class},new BaseMethodHook(){
                    @Override protected void afterMethod(MethodHookParam p){
                        if(eligible(p.thisObject)==null)return;
                        int offset=com.my.televip.Clients.ClientManager.is(com.my.televip.Clients.ClientManager.Client.Telegraph)?1:0;
                        ArrayList<Integer> options=(ArrayList<Integer>)p.args[3+offset];if(options.contains(OPTION))return;
                        ((ArrayList<CharSequence>)p.args[2+offset]).add(Translator.get("QueryReadTime"));options.add(OPTION);
                        if(!com.my.televip.Clients.ClientManager.is(com.my.televip.Clients.ClientManager.Client.Nagram))((ArrayList<Integer>)p.args[1+offset]).add(SettingsIconResolver.getIconSettings());
                    }
                }));
        if(!click)click=HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_ACTIVITY),Obfuscate.getMethodName("ChatActivity","processSelectedOption"),int.class,new BaseMethodHook(){
            @Override protected void beforeMethod(MethodHookParam p){
                if((int)p.args[0]!=OPTION)return;
                MessageObject message=eligible(p.thisObject);if(message==null){p.setResult(null);return;}
                Activity activity=Utils.getCurrentActivity();if(activity==null)return;
                p.setResult(null);XposedHelpers.callMethod(p.thisObject,Obfuscate.getMethodName("ChatActivity","closeMessageMenu"),true);
                query(activity,p.thisObject,message);
            }
        });
    }
    private static void query(Activity activity,Object fragment,MessageObject message){
        int slot=((Number)field(fragment,"BaseFragment","currentAccount")).intValue();long account=UserConfig.getInstance(slot).getClientUserId();
        if(account==0||UserConfig.getSelectedAccount()!=slot)return;
        long dialog=message.getDialogId();int id=message.getMessageOwner().getId();long now=SystemClock.elapsedRealtime();
        ReadTimeCache.Result cached=cache.get(account,dialog,id,now);
        TextView label=new TextView(activity);label.setTextIsSelectable(true);label.setPadding(32,24,32,24);
        AlertDialog box=new AlertDialog.Builder(activity).setTitle(Translator.get("QueryReadTime")).setView(label).setPositiveButton(android.R.string.ok,null).create();
        if(cached!=null){label.setText(result(cached.date));box.show();return;}
        ReadTimeCache.Token token=cache.begin(account,dialog,id,now);
        if(token==null){Toast.makeText(activity,Translator.get("ReadTimeRateLimited"),Toast.LENGTH_SHORT).show();return;}
        label.setText(Translator.get("ReadTimeLoading"));box.show();
        WeakReference<AlertDialog> window=new WeakReference<>(box);WeakReference<TextView> output=new WeakReference<>(label);WeakReference<Object> owner=new WeakReference<>(fragment);
        ConnectionsManager connection=ConnectionsManager.getInstance(slot);AtomicInteger requestId=new AtomicInteger();
        Runnable cancel=()->{cache.cancel(token);int rid=requestId.getAndSet(0);if(rid!=0)try{connection.cancelRequest(rid);}catch(Throwable error){Logger.e(error);}};
        box.setOnDismissListener(d->cancel.run());
        try{
            Object request=XposedHelpers.newInstance(ClassLoad.getClass("org.telegram.tgnet.TLRPC$TL_messages_getOutboxReadDate"));
            Object peer=XposedHelpers.callMethod(MessagesController.getInstance(slot).getInstanceObject(),Obfuscate.getMethodName("MessagesController","getInputPeerJ"),dialog);
            XposedHelpers.setObjectField(request,Obfuscate.getFieldName("TLRPC$TL_messages_getOutboxReadDate","peer"),peer);
            XposedHelpers.setIntField(request,Obfuscate.getFieldName("TLRPC$TL_messages_getOutboxReadDate","msg_id"),id);
            int sent=((Number)field(message.getMessageOwner().get_Message(),"TLRPC$Message","date")).intValue();
            Class<?> delegate=ClassLoad.getClass(ClassNames.REQUEST_DELEGATE);
            Object callback=Proxy.newProxyInstance(delegate.getClassLoader(),new Class<?>[]{delegate},(proxy,method,args)->{
                if(method.getDeclaringClass()==Object.class){if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);if(method.getName().equals("equals"))return proxy==args[0];return "TeleVipReadTimeDelegate";}
                int date=0;
                try { if(args!=null&&args.length==2&&args[1]==null&&ClassLoad.getClass("org.telegram.tgnet.TLRPC$TL_outboxReadDate").isInstance(args[0])){
                    int value=((Number)field(args[0],"TLRPC$TL_outboxReadDate","date")).intValue();
                    if(value>=sent&&value<=(long)connection.getCurrentTime()+300)date=value;
                }
                } catch(Throwable error){Logger.e(error);}
                int answer=date;
                AndroidUtilities.runOnUIThread(()->{
                    if(UserConfig.getInstance(slot).getClientUserId()!=account){cache.cancel(token);return;}
                    if(!cache.finish(token,answer,SystemClock.elapsedRealtime()))return;
                    requestId.set(0);
                    if(visible(window,owner,slot,account)&&output.get()!=null)output.get().setText(result(answer));
                });return null;
            });
            requestId.set(connection.sendRequestTracked(request,callback));
            AndroidUtilities.applicationHandler.postDelayed(()->{
                if(cache.cancel(token)){
                    if(visible(window,owner,slot,account)&&output.get()!=null)output.get().setText(result(0));cancel.run();
                }else cancel.run();
            },30000);
        }catch(Throwable error){cache.cancel(token);label.setText(result(0));Logger.e(error);}
    }
    private static boolean visible(WeakReference<AlertDialog> box,WeakReference<Object> fragment,int slot,long account){
        AlertDialog view=box.get();Object owner=fragment.get();
        return enabled()&&view!=null&&view.isShowing()&&owner!=null&&UserConfig.getSelectedAccount()==slot&&new BaseFragment(owner).getUserConfig().getClientUserId()==account;
    }
    private static String result(int date){return date>0?DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.MEDIUM).format(new Date(date*1000L)):Translator.get("ReadTimeUnavailable");}
}
