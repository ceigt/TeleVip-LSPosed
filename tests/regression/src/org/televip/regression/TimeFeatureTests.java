package org.televip.regression;
import java.util.*;
import com.my.televip.features.messages.*;
public final class TimeFeatureTests {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static class NativeFormatter {
  public String formatJ(long millis)throws Throwable{return (String)com.my.televip.compat.XposedBridge.invoke(NativeFormatter.class.getMethod("formatD",java.util.Date.class),this,new java.util.Date(millis));}
  public String formatD(java.util.Date date){return "3:04 PM";}
 }
 public static class NativeCell {
  public static String timeString(Upstream15Tests.HostMessage current,Upstream15Tests.HostMessage message,boolean edited,int editDate)throws Throwable{
   return (String)com.my.televip.compat.XposedBridge.invoke(NativeFormatter.class.getMethod("formatJ",long.class),new NativeFormatter(),23000L);
  }
 }
 public static int run()throws Throwable{
  check(TimeSeconds.add("3:04 PM",23000).equals("3:04:23 PM"),"12-hour marker changed");
  check(TimeSeconds.add("15:04",23000).equals("15:04:23"),"24-hour seconds absent");
  check(TimeSeconds.add("edited 15:04",23000).equals("edited 15:04:23"),"edited label changed");
  check(TimeSeconds.add("15:04:23",23000).equals("15:04:23"),"seconds duplicated");
  check(TimeSeconds.add("15.04",23000).equals("15.04.23"),"localized time separator changed");
  check(TimeSeconds.add("11.11.2026",23000).equals("11.11.2026"),"date treated as a time");
  check(TimeSeconds.add("",23000).equals(""),"hidden/sponsored time invented");
  check(TimeSeconds.add(null,23000)==null,"null formatter result changed");
  check(TimeSeconds.add("٣:٠٤ م",23000).equals("٣:٠٤:٢٣ م"),"localized digits lost");
  ReadTimeCache c=new ReadTimeCache();ReadTimeCache.Token first=c.begin(101,200,7,1000);
  check(first!=null&&c.begin(101,200,7,2000)==null,"same query not coalesced");
  check(c.begin(101,200,8,1001)==null,"per-account rate limit bypassed");
  ReadTimeCache.Token second=c.begin(102,200,7,1001);check(second!=null,"accounts share rate limit");
  check(c.begin(103,200,7,1002)==null,"in-flight bound exceeded");
  check(c.finish(first,1234,1500),"valid query rejected");
  check(!c.finish(first,9999,1600)&&c.get(101,200,7,1600).date==1234,"duplicate callback changed answer");
  check(c.get(102,200,7,1600)==null,"cached date leaked across accounts");
  check(c.get(101,201,7,1600)==null&&c.get(101,200,8,1600)==null,"cached date leaked across messages");
  c.clearAccount(102);check(!c.finish(second,5555,1600),"logout callback restored data");
  check(c.get(101,200,7,301500)==null,"positive date never expired");
  ReadTimeCache.Token negative=c.begin(101,200,9,302000);check(c.finish(negative,0,302100),"negative result missing");
  check(c.get(101,200,9,302101).date==0&&c.get(101,200,9,332100)==null,"negative result expiry wrong");
  ReadTimeCache.Token old=c.begin(101,200,10,333000);c.clearAccount(101);ReadTimeCache.Token newer=c.begin(101,200,10,334000);
  check(!c.finish(old,777,334100)&&c.finish(newer,888,334200),"old session replaced newer query");
  ReadTimeCache.Token timeout=c.begin(101,200,11,335000);check(!c.finish(timeout,123,365000),"expired network response accepted");
  ReadTimeCache.Token canceled=c.begin(101,200,12,366000);check(c.cancel(canceled)&&!c.cancel(canceled)&&!c.finish(canceled,123,366100),"dismissed query completed");
  long clock=400000;for(int i=1;i<=150;i++){ReadTimeCache.Token t=c.begin(101,200,i,clock);check(t!=null&&c.finish(t,1000+i,clock+10),"bounded-cache fixture failed");clock+=1000;}
  check(c.size()==ReadTimeCache.MAX_ENTRIES&&c.get(101,200,1,clock)==null&&c.get(101,200,150,clock)!=null,"LRU retention bound wrong");
  c.clearAccount(101);check(c.size()==0,"logout cache retained dates");
  try{c.begin(0,200,1,0);throw new AssertionError("invalid account accepted");}catch(IllegalArgumentException expected){checks++;}
  java.util.Map<String,Class<?>> previous=new java.util.HashMap<>();
  String[] names={com.my.televip.Class.ClassNames.CHAT_MESSAGE_CELL,com.my.televip.Class.ClassNames.FAST_DATE_FORMAT,com.my.televip.Class.ClassNames.MESSAGE_OBJECT};
  Class<?>[] fixtures={NativeCell.class,NativeFormatter.class,Upstream15Tests.HostMessage.class};
  for(int i=0;i<names.length;i++)previous.put(names[i],com.my.televip.Class.ClassLoad.classes.put(names[i],fixtures[i]));
  boolean seconds=com.my.televip.Configs.ConfigManager.showSeconds.isEnable();
  try{
   com.my.televip.Configs.ConfigManager.showSeconds.setEnable(true);SecondsDisplay.init();
   java.lang.reflect.Method time=NativeCell.class.getMethod("timeString",Upstream15Tests.HostMessage.class,Upstream15Tests.HostMessage.class,boolean.class,int.class);
   java.lang.reflect.Method formatter=NativeFormatter.class.getMethod("formatJ",long.class);
   check(com.my.televip.compat.XposedBridge.invoke(formatter,new NativeFormatter(),23000L).equals("3:04 PM"),"other-screen formatting changed");
   Object value=com.my.televip.compat.XposedBridge.invoke(time,null,new Upstream15Tests.HostMessage(),new Upstream15Tests.HostMessage(),false,0);
   check(value.equals("3:04:23 PM"),"scoped native seconds path missing or duplicated by nested formatters");
   check(com.my.televip.compat.XposedBridge.invoke(formatter,new NativeFormatter(),23000L).equals("3:04 PM"),"formatting scope leaked");
   com.my.televip.Configs.ConfigManager.showSeconds.setEnable(false);
   check(com.my.televip.compat.XposedBridge.invoke(time,null,new Upstream15Tests.HostMessage(),new Upstream15Tests.HostMessage(),false,0).equals("3:04 PM"),"disabled seconds changed time");
  }finally{com.my.televip.Configs.ConfigManager.showSeconds.setEnable(seconds);for(String name:names)if(previous.get(name)==null)com.my.televip.Class.ClassLoad.classes.remove(name);else com.my.televip.Class.ClassLoad.classes.put(name,previous.get(name));}
  return checks;
 }
}
