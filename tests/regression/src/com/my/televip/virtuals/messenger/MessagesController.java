package com.my.televip.virtuals.messenger;
import android.util.SparseArray;
import java.util.ArrayList;
public final class MessagesController {
 public final Native owner;
 public MessagesController(Object value){owner=(Native)value;}
 public static MessagesController getInstance(int slot){return new MessagesController(com.my.televip.compat.XposedHelpers.callStaticMethod(com.my.televip.Class.ClassLoad.getClass(com.my.televip.Class.ClassNames.MESSAGES_CONTROLLER),"getInstance",slot));}
 public Object getInstanceObject(){return owner;}
 public Object getUserFull(long dialog){return com.my.televip.compat.XposedHelpers.callMethod(owner,"getUserFull",dialog);}
 public MessagesStorage getMessagesStorage(){return owner.storage;}
 public SparseArray<Object> getDialogMessagesByIds(){return owner.messages;}
 public Dialogs getDialogMessage(){return owner.dialogs;}
 public static final class Dialogs {public ArrayList<Object> get(long dialog){return null;}}
 public static class Native {
  public final MessagesStorage storage; public final SparseArray<Object> messages=new SparseArray<>(); public final Dialogs dialogs=new Dialogs(); public int nativeDeletions;
  public Native(MessagesStorage storage){this.storage=storage;}
  public void deleteMessagesByPush(long dialog,long channel,ArrayList<Integer> ids){nativeDeletions++;}
  public void deleteMessagesByPush(long dialog,ArrayList<Integer> ids,long channel){nativeDeletions++;}
  public void deleteMessages(long dialog,ArrayList<Integer> ids){nativeDeletions++;}
 }
}
