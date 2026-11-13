package com.my.televip.features.messages;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Database.LocalStateStore;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.compat.XposedHelpers;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.messages.MessageStorage;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.messenger.BaseController;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/** Range completion Runnables also run on failure. Only the final successful RPC owns cleanup. */
public final class RangeDeletionCleanup {
    private static boolean installed;
    private static final ThreadLocal<ArrayDeque<Frame>> scopes=ThreadLocal.withInitial(ArrayDeque::new);
    private static final class Frame {final Object call;Range range;Frame(Object call){this.call=call;}}
    private static final class Range {
        final Object owner;final int slot,min,max;final long dialog;final LocalStateStore.Session token;
        final AtomicBoolean completed=new AtomicBoolean();
        Range(Object owner,long dialog,int min,int max){
            this.owner=owner;this.dialog=dialog;this.min=min;this.max=max;
            BaseController controller=new BaseController(owner);slot=controller.getCurrentAccount();
            token=LocalData.store().session(()->controller.getUserConfig().getClientUserId());
        }
    }
    private RangeDeletionCleanup(){}
    public static synchronized void init(){
        if(installed)return;
        try(HookInstallation attempt=HookInstallation.begin()){
            HMethod.hookMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER),Obfuscate.getMethodName("MessagesController","deleteMessagesRange"),
                ArgsResolver.merge("deleteMessagesRange",new Class[]{long.class,long.class,int.class,int.class,boolean.class,Runnable.class},new BaseMethodHook(){
                    @Override protected void beforeMethod(MethodHookParam p){
                        Frame frame=new Frame(p);scopes.get().push(frame);
                        try{frame.range=new Range(p.thisObject,(long)p.args[0],(int)p.args[2],(int)p.args[3]);}catch(Throwable error){LocalData.issue(error);}
                    }
                    @Override protected void afterMethod(MethodHookParam p){
                        ArrayDeque<Frame> stack=scopes.get();if(!stack.isEmpty()&&stack.peek().call==p)stack.pop();else stack.clear();
                        if(stack.isEmpty())scopes.remove();
                    }
                }));
            HMethod.hookMethod(ClassLoad.getClass(ClassNames.CONNECTIONS_MANAGER),Obfuscate.getMethodName("ConnectionsManager","sendRequestInternal"),
                ArgsResolver.merge("sendRequestInternal",new Class[]{ClassLoad.getClass(ClassNames.TL_OBJECT),ClassLoad.getClass(ClassNames.REQUEST_DELEGATE),ClassLoad.getClass(ClassNames.REQUEST_DELEGATE_TIMESTAMP),ClassLoad.getClass(ClassNames.QUICK_ACK_DELEGATE),ClassLoad.getClass(ClassNames.WRITE_TO_SOCKET_DELEGATE),int.class,int.class,int.class,boolean.class,int.class},new BaseMethodHook(){
                    @Override protected void beforeMethod(MethodHookParam p){
                        ArrayDeque<Frame> stack=scopes.get();if(stack.isEmpty()){scopes.remove();return;}
                        if(stack.peek().range!=null)try{wrap(p,stack.peek().range);}catch(Throwable error){LocalData.issue(error);}
                    }
                }));
            installed=attempt.isComplete();
        }
    }
    private static void wrap(BaseMethodHook.MethodHookParam p,Range range){
        if(!LocalData.store().isCurrent(range.token)||new BaseController(p.thisObject).getCurrentAccount()!=range.slot)return;
        Object request=p.args[0];
        if(!ClassLoad.getClass("org.telegram.tgnet.TLRPC$TL_messages_deleteHistory").isInstance(request)
                ||XposedHelpers.getIntField(request,Obfuscate.getFieldName("TLRPC$TL_messages_deleteHistory","min_date"))!=range.min
                ||XposedHelpers.getIntField(request,Obfuscate.getFieldName("TLRPC$TL_messages_deleteHistory","max_date"))!=range.max)return;
        for(int i:new int[]{1,2}){
            Object original=p.args[i];if(original==null)continue;
            Class<?> type=ClassLoad.getClass(i==1?ClassNames.REQUEST_DELEGATE:ClassNames.REQUEST_DELEGATE_TIMESTAMP);
            if(type==null||!type.isInstance(original))continue;
            p.args[i]=Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
                if(method.getDeclaringClass()==Object.class){
                    if(method.getName().equals("equals"))return proxy==args[0];
                    if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                    return "TeleVip range callback";
                }
                try{return method.invoke(original,args);}catch(InvocationTargetException error){throw error.getCause();}
                finally{try{confirmed(range,args);}catch(Throwable error){LocalData.issue(error);}}
            });
        }
    }
    private static void confirmed(Range range,Object[] args){
        if(args==null||args.length<2||args[1]!=null||!ClassLoad.getClass("org.telegram.tgnet.TLRPC$TL_messages_affectedHistory").isInstance(args[0]))return;
        if(XposedHelpers.getIntField(args[0],Obfuscate.getFieldName("TLRPC$TL_messages_affectedHistory","offset"))!=0||!range.completed.compareAndSet(false,true))return;
        MessageStorage.post(()->{try{
            if(LocalData.store().isCurrent(range.token)&&new BaseController(range.owner).getUserConfig().getClientUserId()==range.token.account)
                LocalData.store().forgetRange(range.token.account,range.dialog,range.min,range.max);
        }catch(Throwable error){LocalData.issue(error);}});
    }
}
