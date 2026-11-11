package org.televip.regression;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.*;
import com.my.televip.Class.*;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.compat.XposedBridge;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.features.messages.ServerPushDeletes;
import com.my.televip.virtuals.messenger.*;
import com.my.televip.virtuals.tgnet.TLRPC;
public final class ServerPushTests {
 private static int checks;
 private static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 private static void drain(Handler h)throws Exception{CountDownLatch c=new CountDownLatch(1);h.post(c::countDown);check(c.await(10,TimeUnit.SECONDS),"queue did not finish");}
 public static int run()throws Throwable{
  Class<?> old=ClassLoad.classes.put(ClassNames.MESSAGES_CONTROLLER,MessagesController.Native.class);
  HandlerThread t=new HandlerThread("Push delete fixture");t.start();Handler h=new Handler(t.getLooper());
  boolean enabled=ConfigManager.showDeletedMessages.isEnable();
  try{
   for(boolean grouped:new boolean[]{false,true}){
    ArgsResolver.groupedPush=grouped;
    Field installed=ServerPushDeletes.class.getDeclaredField("installed");installed.setAccessible(true);installed.setBoolean(null,false);
    ServerPushDeletes.init();check(installed.getBoolean(null),"production push hook not installed");
    Method push=grouped?MessagesController.Native.class.getMethod("deleteMessagesByPush",long.class,long.class,ArrayList.class):MessagesController.Native.class.getMethod("deleteMessagesByPush",long.class,ArrayList.class,long.class);
    MessagesStorage storage=new MessagesStorage(h);MessagesController.Native nativeController=new MessagesController.Native(storage);
    Upstream15Tests.HostMessage msg=new Upstream15Tests.HostMessage();msg.messageOwner=new TLRPC.MessageData(7,-100,0,"disposable");nativeController.messages.put(7,msg);
    ArrayList<Integer> ids=new ArrayList<>(Arrays.asList(7));Object[] args=grouped?new Object[]{0L,0L,ids}:new Object[]{0L,ids,0L};
    ConfigManager.showDeletedMessages.setEnable(true);XposedBridge.invoke(push,nativeController,args);drain(h);
    check(nativeController.nativeDeletions==0,"native push deletion was not stopped");
    check(storage.pushCleanup==1,"native unread-push cleanup lost");
    check(storage.database.cursors.size()==2,"cache retention was not queued on owning storage");
    check(msg.messageOwner.flags==Integer.MIN_VALUE,"cached message missing deletion flag");
    check(ids.equals(Arrays.asList(7)),"caller update IDs mutated");
    XposedBridge.invoke(MessagesController.Native.class.getMethod("deleteMessages",long.class,ArrayList.class),nativeController,-100L,ids);
    check(nativeController.nativeDeletions==1,"explicit user delete intercepted");
    ConfigManager.showDeletedMessages.setEnable(false);XposedBridge.invoke(push,nativeController,args);drain(h);
    check(nativeController.nativeDeletions==2&&storage.pushCleanup==1,"disabled push behavior changed");
    ConfigManager.showDeletedMessages.setEnable(true);storage.accountId=0;XposedBridge.invoke(push,nativeController,args);drain(h);
    check(nativeController.nativeDeletions==3,"logged-out account retained messages");
    storage.accountId=101;Object[] secret=grouped?new Object[]{1L<<32,0L,ids}:new Object[]{1L<<32,ids,0L};XposedBridge.invoke(push,nativeController,secret);drain(h);
    check(nativeController.nativeDeletions==4,"secret dialog captured");
   }
  }finally{ConfigManager.showDeletedMessages.setEnable(enabled);ArgsResolver.groupedPush=false;t.quitSafely();if(old==null)ClassLoad.classes.remove(ClassNames.MESSAGES_CONTROLLER);else ClassLoad.classes.put(ClassNames.MESSAGES_CONTROLLER,old);}
  return checks;
 }
}
