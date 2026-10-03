package com.my.televip.features.ui;

import android.content.SharedPreferences;
import com.my.televip.Class.*;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.application.AndroidUtilities;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.compat.XposedHelpers;
import com.my.televip.hooks.*;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.*;
import com.my.televip.virtuals.messenger.*;
import com.my.televip.virtuals.tgnet.ConnectionsManager;
import java.lang.reflect.Method;
import java.util.*;

/** Complete ad requests with empty protocol responses instead of leaving callers pending. */
public final class AdBlock {
    private static volatile boolean installed;
    private static final Map<Class<?>,Class<?>> responses=new HashMap<>();
    private static final Set<Class<?>> reported=Collections.synchronizedSet(new HashSet<>());
    private static final Map<Integer,SharedPreferences> settings=new HashMap<>();
    private static final Set<Object> queued=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final String[] PROMO_KEYS={"proxy_dialog","proxyDialogAddress","nextPromoInfoCheckTime","promo_dialog_type","promo_psa_message","promo_psa_type"};
    private AdBlock() {}
    public static synchronized void init() {
        if(installed) return;
        try(HookInstallation attempt=HookInstallation.begin()) {
            Class<?> manager=ClassLoad.getClass(ClassNames.CONNECTIONS_MANAGER);
            String[] requests={"TL_messages_getSponsoredMessages","TL_contacts_getSponsoredPeers","TL_help_getPromoData"};
            String[] empty={"TL_messages_sponsoredMessagesEmpty","TL_contacts_sponsoredPeersEmpty","TL_help_promoDataEmpty"};
            for(int i=0;i<requests.length;i++) {
                Class<?> req=ClassLoad.getClass("org.telegram.tgnet.TLRPC$"+requests[i]);
                Class<?> res=ClassLoad.getClass("org.telegram.tgnet.TLRPC$"+empty[i]);
                if(attempt.require(req) && attempt.require(res)) responses.put(req,res);
            }
            HMethod.hookMethod(manager,Obfuscate.getMethodName("ConnectionsManager","sendRequestInternal"),
                ArgsResolver.merge("sendRequestInternal",new Class[]{ClassLoad.getClass(ClassNames.TL_OBJECT),ClassLoad.getClass(ClassNames.REQUEST_DELEGATE),ClassLoad.getClass(ClassNames.REQUEST_DELEGATE_TIMESTAMP),ClassLoad.getClass(ClassNames.QUICK_ACK_DELEGATE),ClassLoad.getClass(ClassNames.WRITE_TO_SOCKET_DELEGATE),int.class,int.class,int.class,boolean.class,int.class},new BaseMethodHook() {
                    @Override protected void beforeMethod(MethodHookParam param) {
                        if(!enabled() || param.args[0]==null) return;
                        Class<?> response=responses.get(param.args[0].getClass());
                        if(response==null) return;
                        try {
                            Object result=XposedHelpers.newInstance(response);
                            if(response.getName().equals(Obfuscate.getClassName("org.telegram.tgnet.TLRPC$TL_help_promoDataEmpty")))
                                XposedHelpers.setIntField(result,Obfuscate.getFieldName("TLRPC$TL_help_promoDataEmpty","expires"),new ConnectionsManager(param.thisObject).getCurrentTime()+3600);
                            Object callback=param.args[1]!=null ? param.args[1] : param.args[2];
                            if(callback!=null) {
                                Method method=AdResponseCallbacks.find(callback,result,param.args[1]==null);
                                Utilities.getStageQueue().postRunnable(()-> {
                                    try {AdResponseCallbacks.invoke(method,callback,result);} catch(Throwable error) {Logger.e(error);}
                                });
                            }
                            if(reported.add(param.args[0].getClass())) Logger.l("Ad request completed locally: "+param.args[0].getClass().getName());
                            param.setResult(null);
                        } catch(Throwable error) {Logger.e(error);} // Unknown response shape: keep host behavior.
                    }
                }));
            HMethod.hookMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER),Obfuscate.getMethodName("MessagesController","checkPromoInfoInternal"),boolean.class,new BaseMethodHook() {
                @Override protected void beforeMethod(MethodHookParam param) {
                    if(!enabled()) return;
                    final Object controller=param.thisObject;
                    try {
                        forgetPromo(new BaseController(controller).getCurrentAccount());
                        synchronized(queued) {if(!queued.add(controller)) {param.setResult(null);return;}}
                        AndroidUtilities.runOnUIThread(()-> {
                            try {new MessagesController(controller).removePromoDialog();}
                            catch(Throwable error) {Logger.e(error);}
                            finally {synchronized(queued) {queued.remove(controller);}}
                        });
                        param.setResult(null);
                    } catch(Throwable error) {Logger.e(error);}
                }
            });
            installed=attempt.isComplete();
            if(installed) Logger.l("Ad blocking installed: sponsored messages, search ads and proxy promo; callbacks completed");
        } catch(Throwable error) {Logger.e(error);}
    }
    private static boolean enabled() {return ConfigManager.blockAds!=null && ConfigManager.blockAds.isEnable();}
    private static synchronized void forgetPromo(int account) {
        SharedPreferences preferences=settings.get(account);
        if(preferences==null) {
            preferences=(SharedPreferences)XposedHelpers.callStaticMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER),Obfuscate.getMethodName("MessagesController","getMainSettings"),account);
            settings.put(account,preferences);
        }
        boolean saved=false;
        for(String key:PROMO_KEYS) saved |= preferences.contains(key);
        if(saved) {
            SharedPreferences.Editor editor=preferences.edit();
            for(String key:PROMO_KEYS) editor.remove(key);
            editor.apply();
        }
    }
}
