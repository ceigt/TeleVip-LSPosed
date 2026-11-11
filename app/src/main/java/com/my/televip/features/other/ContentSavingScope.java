package com.my.televip.features.other;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.compat.XposedHelpers;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.messenger.MessageObject;
import com.my.televip.virtuals.messenger.MessagesController;
import com.my.televip.virtuals.tgnet.TLRPC;
import com.my.televip.virtuals.ui.ChatActivity;
import com.my.televip.logging.Logger;
import java.util.ArrayList;
import java.util.List;

/** Local menu/viewer scopes only; never synthesizes or sends a forwarded message. */
public final class ContentSavingScope {
    private static boolean installed;
    private static final BooleanFieldScope scope=new BooleanFieldScope();
    private ContentSavingScope() {}
    private static boolean enabled(){return ConfigManager.extendedContentSaving!=null&&ConfigManager.extendedContentSaving.isEnable()
            &&ConfigManager.removesContentSaving!=null&&ConfigManager.removesContentSaving.isEnable();}
    private static void message(Object value) throws ReflectiveOperationException {
        if(value==null||!ClassLoad.getClass(ClassNames.MESSAGE_OBJECT).isInstance(value))return;
        MessageObject wrapper=new MessageObject(value);TLRPC.Message owner=wrapper.getMessageOwner();
        // Existing secret/expiring-media policy remains in charge of these messages.
        if(owner==null||owner.getTtl()!=0||owner.getId()<=0)return;
        Object media=XposedHelpers.getObjectField(owner.get_Message(),Obfuscate.getFieldName("TLRPC$Message","media"));
        if(media!=null&&XposedHelpers.getIntField(media,Obfuscate.getFieldName("TLRPC$MessageMedia","ttl_seconds"))!=0)return;
        scope.clear(owner.get_Message(),Obfuscate.getFieldName("TLRPC$Message","noforwards"));
        int account=XposedHelpers.getIntField(value,Obfuscate.getFieldName("MessageObject","currentAccount"));
        peer(account,wrapper.getDialogId());
    }
    private static void peer(int account,long dialog) throws ReflectiveOperationException {
        if(dialog<=0)return;
        Object full=MessagesController.getInstance(account).getUserFull(dialog);
        if(full!=null){scope.clear(full,Obfuscate.getFieldName("TLRPC$UserFull","noforwards_peer_enabled"));scope.clear(full,Obfuscate.getFieldName("TLRPC$UserFull","noforwards_my_enabled"));}
    }
    private static BaseMethodHook menu(boolean selection){return new BaseMethodHook(){
        @Override protected void beforeMethod(MethodHookParam p){scope.enter(p);try{
            if(!enabled())return;
            Object selected=selection?new ChatActivity(p.thisObject).getSelectedObject():null;
            if(selected instanceof MessageObject)message(((MessageObject)selected).getMessageObject());else if(p.args.length>0)message(p.args[0]);
            android.os.Bundle args=(android.os.Bundle)XposedHelpers.getObjectField(p.thisObject,Obfuscate.getFieldName("BaseFragment","arguments"));
            if(args!=null&&args.getInt("enc_id",0)==0)peer(XposedHelpers.getIntField(p.thisObject,Obfuscate.getFieldName("BaseFragment","currentAccount")),args.getLong("user_id",0));
        }catch(Throwable error){Logger.e(error);}}
        @Override protected void afterMethod(MethodHookParam p){scope.exit(p);}
    };}
    public static synchronized void init(){
        if(installed)return;
        try(HookInstallation attempt=HookInstallation.begin()){
            HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_ACTIVITY),Obfuscate.getMethodName("ChatActivity","fillMessageMenu"),
                    ArgsResolver.merge("fillMessageMenu",new Class[]{ClassLoad.getClass(ClassNames.MESSAGE_OBJECT),ArrayList.class,ArrayList.class,ArrayList.class},menu(false)));
            HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_ACTIVITY),Obfuscate.getMethodName("ChatActivity","processSelectedOption"),int.class,menu(true));
            HMethod.hookMethod(ClassLoad.getClass(ClassNames.PHOTO_VIEWER),Obfuscate.getMethodName("PhotoViewer","onPhotoShow"),
                    ClassLoad.getClass(ClassNames.MESSAGE_OBJECT),ClassLoad.getClass("org.telegram.tgnet.TLRPC$FileLocation"),ClassLoad.getClass("org.telegram.messenger.ImageLocation"),ClassLoad.getClass("org.telegram.messenger.ImageLocation"),ArrayList.class,ArrayList.class,List.class,int.class,ClassLoad.getClass("org.telegram.ui.PhotoViewer$PlaceProviderObject"),new BaseMethodHook(){
                        @Override protected void beforeMethod(MethodHookParam p){scope.enter(p);try{if(enabled())message(p.args[0]);}catch(Throwable error){Logger.e(error);}}
                        @Override protected void afterMethod(MethodHookParam p){scope.exit(p);}
                    });
            HMethod.hookMethod(ClassLoad.getClass(ClassNames.PHOTO_VIEWER),Obfuscate.getMethodName("PhotoViewer","setIsAboutToSwitchToIndexIZZZ"),int.class,boolean.class,boolean.class,boolean.class,new BaseMethodHook(){
                @Override protected void beforeMethod(MethodHookParam p){scope.enter(p);try{
                    if(!enabled())return;List<?> messages=(List<?>)XposedHelpers.getObjectField(p.thisObject,Obfuscate.getFieldName("PhotoViewer","imagesArr"));int index=(int)p.args[0];
                    if(messages!=null&&index>=0&&index<messages.size())message(messages.get(index));
                }catch(Throwable error){Logger.e(error);}}
                @Override protected void afterMethod(MethodHookParam p){scope.exit(p);}
            });
            HMethod.hookMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER),Obfuscate.getMethodName("MessagesController","isUserNoForwards"),ClassLoad.getClass("org.telegram.tgnet.TLRPC$UserFull"),new BaseMethodHook(){
                @Override protected void beforeMethod(MethodHookParam p){if(enabled())p.setResult(false);}
            });
            installed=attempt.isComplete();
        }catch(Throwable error){Logger.e(error);}
    }
}
