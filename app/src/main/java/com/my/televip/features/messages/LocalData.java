package com.my.televip.features.messages;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Rect;
import com.my.televip.Database.LocalStateStore;
import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.application.ApplicationLoaderHook;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.compat.XposedHelpers;
import com.my.televip.hooks.HMethod;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.messages.MessageStorage;
import com.my.televip.virtuals.messenger.*;
import com.my.televip.virtuals.ui.BaseFragment;
import com.my.televip.virtuals.tgnet.NativeByteBuffer;
import com.my.televip.virtuals.tgnet.TLRPC;
import com.my.televip.logging.Logger;
import java.io.File;
import java.nio.ByteBuffer;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.ArrayList;

public final class LocalData {
    private static LocalStateStore database;
    private static boolean cleanup, deletes, clears, views, pause, restore;
    private static final Map<Object,WeakReference<View>> roots=Collections.synchronizedMap(new WeakHashMap<>());
    public static volatile String lastIssue;
    private LocalData() {}
    public static synchronized LocalStateStore store() {
        if(database==null)database=new LocalStateStore(ApplicationLoaderHook.getApplicationContext(),new File(MessageStorage.getStorageFile(),"local-state.db").getAbsolutePath());
        return database;
    }
    public static boolean archiving() {return ConfigManager.durableDeletedMessages!=null&&ConfigManager.durableDeletedMessages.isEnable();}
    private static boolean remembering() {return ConfigManager.localReadPosition!=null&&ConfigManager.localReadPosition.isEnable();}
    public static void issue(Throwable error) {lastIssue=error instanceof IllegalArgumentException?"InvalidArchive": "LocalDataIssue";Logger.e(error);}
    public static synchronized void initCleanup() {
        if(!cleanup){
            MessageStorage.post(()->{try{store().loadPositions();}catch(Throwable error){issue(error);}});
            cleanup=HMethod.hookMethod(ClassLoad.getClass(ClassNames.USER_CONFIG),Obfuscate.getMethodName("UserConfig","clearConfig"),new BaseMethodHook(){
            @Override protected void beforeMethod(MethodHookParam p){
                long account=new UserConfig(p.thisObject).getClientUserId();store().revokeAccount(account);
                MessageStorage.post(()->{try{store().finishAccountCleanup(account);}catch(Throwable error){issue(error);}});
            }
            });
        }
        installDeletionCleanup();
    }
    public static synchronized void initArchive() {
        initCleanup();ShowDeletedMessages.init();ArchiveUi.init();
    }
    private static void installDeletionCleanup() {
        if(!deletes)deletes=HMethod.hookMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER),Obfuscate.getMethodName("MessagesController","deleteMessages"),
                ArgsResolver.merge("deleteMessages",new Class[]{ArrayList.class,ArrayList.class,ClassLoad.getClass(ClassNames.TLRPC_ENCRYPTED_CHAT),long.class,boolean.class,int.class,boolean.class,long.class,ClassLoad.getClass("org.telegram.tgnet.TLObject"),int.class,boolean.class,int.class},new BaseMethodHook(){
                    @Override protected void beforeMethod(MethodHookParam p){
                        // Native user deletion must never be undone by capture or an old backup.
                        long account=new BaseController(p.thisObject).getUserConfig().getClientUserId();long dialog=(long)p.args[3];
                        List<Integer> ids=p.args[0]==null?Collections.emptyList():new ArrayList<>((ArrayList<Integer>)p.args[0]);
                        if(Boolean.TRUE.equals(p.args[6]))return;
                        MessageStorage.post(()->{try{if(archiving()||store().hasLocalData())store().forget(account,dialog,ids);}catch(Throwable error){issue(error);}});
                    }
                }));
        if(!clears)clears=HMethod.hookMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER),Obfuscate.getMethodName("MessagesController","deleteDialogLocal"),long.class,int.class,int.class,int.class,boolean.class,ClassLoad.getClass("org.telegram.tgnet.TLRPC$InputPeer"),long.class,new BaseMethodHook(){
            @Override protected void beforeMethod(MethodHookParam p){
                int mode=(int)p.args[2];if(mode!=0&&mode!=1)return; // 2 is cache cleanup; 3 is internal empty-dialog cleanup.
                long account=new BaseController(p.thisObject).getUserConfig().getClientUserId();long dialog=(long)p.args[0];int cutoff=(int)(System.currentTimeMillis()/1000);
                MessageStorage.post(()->{try{if(remembering()||archiving()||store().hasLocalData())store().clearDialog(account,dialog,cutoff);}catch(Throwable error){issue(error);}});
            }
        });
    }
    /** Called on the owning host storage queue while its original serialized row is available. */
    public static void capture(MessagesStorage storage,long dialog,int id,NativeByteBuffer data) {
        if(!archiving()||ConfigManager.showDeletedMessages==null||!ConfigManager.showDeletedMessages.isEnable())return;
        try {
            long account=storage.getAccountUserId();
            LocalStateStore.Session session=store().session(account);
            ByteBuffer buffer=((ByteBuffer)XposedHelpers.getObjectField(data.nativeByteBuffer,Obfuscate.getFieldName("NativeByteBuffer","buffer"))).duplicate();
            buffer.position(0);if(buffer.remaining()<8||buffer.remaining()>LocalStateStore.MAX_PAYLOAD)throw new IllegalArgumentException("Unsupported message size");
            byte[] payload=new byte[buffer.remaining()];buffer.get(payload);
            data.position(0);TLRPC.Message message=TLRPC.Message.TLdeserialize(data,data.readInt32(false),false);data.position(0);
            if(message==null||message.get_Message()==null)return;
            if((dialog&0xffffffffL)==0||message.getTtl()!=0)return;
            Object media=XposedHelpers.getObjectField(message.get_Message(),Obfuscate.getFieldName("TLRPC$Message","media"));
            if(media!=null&&XposedHelpers.getIntField(media,Obfuscate.getFieldName("TLRPC$MessageMedia","ttl_seconds"))!=0)return;
            String text=message.getMessage();int date=XposedHelpers.getIntField(message.get_Message(),Obfuscate.getFieldName("TLRPC$Message","date"));
            LocalStateStore.Record row=new LocalStateStore.Record(account,dialog,id,date,System.currentTimeMillis(),text==null?"":text,payload);
            MessageStorage.post(()->{try{store().save(session,row);}catch(Throwable error){issue(error);}});
        }catch(Throwable error){issue(error);}
    }
    private static final class ChatKey {
        final long account,dialog,topic;
        ChatKey(long account,long dialog,long topic){this.account=account;this.dialog=dialog;this.topic=topic;}
    }
    private static ChatKey chat(Object fragment) {
        Bundle args=(Bundle)XposedHelpers.getObjectField(fragment,Obfuscate.getFieldName("BaseFragment","arguments"));
        if(args==null||args.getInt("enc_id",0)!=0||args.getInt("chatMode",0)!=0)return null;
        long dialog=args.getLong("user_id",0);if(dialog==0)dialog=-args.getLong("chat_id",0);
        if(dialog==0)return null;
        boolean hidden=dialog>0?ConfigManager.hideSeenPrivateChat.isEnable():ConfigManager.hideSeenChannel.isEnable();if(!hidden)return null;
        long account=new BaseFragment(fragment).getUserConfig().getClientUserId();
        long topic=XposedHelpers.getLongField(fragment,Obfuscate.getFieldName("ChatActivity","threadMessageId"));
        return new ChatKey(account,dialog,Math.max(0,topic));
    }
    public static synchronized void initReading() {
        initCleanup();
        if(!restore)restore=HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_ACTIVITY),Obfuscate.getMethodName("ChatActivity","onFragmentCreate"),new BaseMethodHook(){
            @Override protected void beforeMethod(MethodHookParam p){
                if(!remembering())return;ChatKey key=chat(p.thisObject);if(key==null)return;
                Bundle args=(Bundle)XposedHelpers.getObjectField(p.thisObject,Obfuscate.getFieldName("BaseFragment","arguments"));
                if(args.containsKey("message_id")||args.containsKey("start_from_date")||args.containsKey("reply_to")||args.containsKey("poll_option_id"))return;
                LocalStateStore.Position pos=store().position(key.account,key.dialog,key.topic);
                if(pos!=null)args.putInt("message_id",pos.scroll);
            }
        });
        if(!views)views=HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_ACTIVITY),Obfuscate.getMethodName("ChatActivity","createView"),android.content.Context.class,new BaseMethodHook(){
            @Override protected void afterMethod(MethodHookParam p){if(p.getResult() instanceof View)roots.put(p.thisObject,new WeakReference<>((View)p.getResult()));}
        });
        if(!pause)pause=HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_ACTIVITY),Obfuscate.getMethodName("ChatActivity","onPause"),new BaseMethodHook(){
            @Override protected void beforeMethod(MethodHookParam p){
                if(!remembering())return;ChatKey key=chat(p.thisObject);if(key==null)return;
                WeakReference<View> root=roots.get(p.thisObject);View view=root==null?null:root.get();if(view==null)return;
                int[] state={0,0,Integer.MAX_VALUE,0};scan(view,key,state,ClassLoad.getClass(ClassNames.CHAT_MESSAGE_CELL));
                if(state[0]>0&&state[1]>0){
                    LocalStateStore.Session session=store().session(key.account);
                    MessageStorage.post(()->{try{store().savePosition(session,key.dialog,key.topic,state[0],state[1]);}catch(Throwable error){issue(error);}});
                }
            }
        });
    }
    private static void scan(View view,ChatKey key,int[] state,Class<?> cell) {
        if(++state[3]>2000||!view.isShown())return;
        if(cell.isInstance(view)) {
            Rect rect=new Rect();if(!view.getGlobalVisibleRect(rect)||rect.height()<view.getHeight()/2)return;
            MessageObject message=new MessageObject(XposedHelpers.callMethod(view,Obfuscate.getMethodName("ChatMessageCell","getMessageObject")));
            if(message.getMessageOwner()==null||message.getDialogId()!=key.dialog)return;int id=message.getMessageOwner().getId();if(id<=0)return;
            state[0]=Math.max(state[0],id);if(rect.top<state[2]){state[1]=id;state[2]=rect.top;}return;
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)scan(group.getChildAt(i),key,state,cell);}
    }
}
