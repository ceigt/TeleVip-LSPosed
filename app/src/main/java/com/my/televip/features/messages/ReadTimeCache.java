package com.my.televip.features.messages;
import java.util.*;

/** Memory-only, bounded and owned by the account; late/duplicate callbacks cannot repopulate after logout. */
public final class ReadTimeCache {
    public static final int MAX_ENTRIES=128;
    private static final class Key {
        final long account,dialog;final int id;
        Key(long a,long d,int i){account=a;dialog=d;id=i;}
        @Override public int hashCode(){return Objects.hash(account,dialog,id);}
        @Override public boolean equals(Object o){if(!(o instanceof Key))return false;Key k=(Key)o;return k.account==account&&k.dialog==dialog&&k.id==id;}
    }
    public static final class Result {public final int date;final long until;Result(int d,long u){date=d;until=u;}}
    public static final class Token {final Key key;final long deadline;Token(Key k,long d){key=k;deadline=d;}}
    private final LinkedHashMap<Key,Result> values=new LinkedHashMap<>(16,.75f,true);
    private final Map<Key,Token> pending=new HashMap<>();
    private final LinkedHashMap<Long,Long> last=new LinkedHashMap<>();
    public synchronized Result get(long account,long dialog,int id,long now){
        Key k=new Key(account,dialog,id);Result r=values.get(k);
        if(r!=null&&now>=r.until){values.remove(k);return null;}return r;
    }
    public synchronized Token begin(long account,long dialog,int id,long now){
        if(account<=0||dialog<=0||id<=0)throw new IllegalArgumentException();
        pending.values().removeIf(t->now>=t.deadline);
        Key k=new Key(account,dialog,id);
        if(pending.containsKey(k)||pending.size()>=2||last.containsKey(account)&&now-last.get(account)<1000)return null;
        last.remove(account);last.put(account,now);while(last.size()>8)last.remove(last.keySet().iterator().next());
        Token t=new Token(k,now+30000);pending.put(k,t);return t;
    }
    public synchronized boolean finish(Token t,int date,long now){
        if(t==null||pending.get(t.key)!=t)return false;
        pending.remove(t.key);if(now>=t.deadline)return false;
        values.put(t.key,new Result(Math.max(0,date),now+(date>0?300000:30000)));
        while(values.size()>MAX_ENTRIES)values.remove(values.keySet().iterator().next());return true;
    }
    public synchronized boolean cancel(Token t){if(t!=null&&pending.get(t.key)==t){pending.remove(t.key);return true;}return false;}
    public synchronized void clearAccount(long account){
        values.keySet().removeIf(k->k.account==account);pending.keySet().removeIf(k->k.account==account);last.remove(account);
    }
    public synchronized int size(){return values.size();}
}
