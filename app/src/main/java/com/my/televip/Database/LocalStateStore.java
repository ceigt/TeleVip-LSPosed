package com.my.televip.Database;

import android.content.Context;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.*;
import android.util.Base64;
import android.util.JsonReader;
import android.util.JsonWriter;
import java.nio.charset.StandardCharsets;

/** Opt-in local copies and positions. Never writes Telegram's database or sends a request. */
public final class LocalStateStore extends SQLiteOpenHelper {
    public static final int MAX_RECORDS = 10000, MAX_PAYLOAD = 1024 * 1024, MAX_TEXT = 65536;
    public static final long MAX_BYTES = 16L * 1024 * 1024, MAX_BACKUP_BYTES = 48L * 1024 * 1024;
    private final SharedPreferences cleanup;
    private final Object sessionLock = new Object();
    private final Set<Long> revoked = ConcurrentHashMap.newKeySet();
    private final Map<Long,Long> generations = new ConcurrentHashMap<>();
    private final Map<String, Position> positions = new ConcurrentHashMap<>();

    public static final class Position {
        public final int read, scroll;
        public Position(int read,int scroll) { this.read=read; this.scroll=scroll; }
    }
    public static final class Record {
        public final long account, dialog, captured; public final int id, date;
        public final String text; public final byte[] payload;
        public Record(long account,long dialog,int id,int date,long captured,String text,byte[] payload) {
            this.account=account;this.dialog=dialog;this.id=id;this.date=date;this.captured=captured;
            this.text=text;this.payload=payload.clone();
        }
    }
    public LocalStateStore(Context context,String path) {
        super(context.getApplicationContext(),path,null,1);
        cleanup=context.getApplicationContext().getSharedPreferences("TeleVipLocalCleanup",0);
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE archive(account INTEGER NOT NULL,dialog INTEGER NOT NULL,mid INTEGER NOT NULL,date INTEGER NOT NULL,captured INTEGER NOT NULL,text TEXT NOT NULL,payload BLOB NOT NULL,PRIMARY KEY(account,dialog,mid))");
        db.execSQL("CREATE TABLE positions(account INTEGER NOT NULL,dialog INTEGER NOT NULL,topic INTEGER NOT NULL,read_mid INTEGER NOT NULL,scroll_mid INTEGER NOT NULL,PRIMARY KEY(account,dialog,topic))");
        db.execSQL("CREATE TABLE forgotten(account INTEGER NOT NULL,dialog INTEGER NOT NULL,mid INTEGER NOT NULL,PRIMARY KEY(account,dialog,mid))");
        db.execSQL("CREATE TABLE cleared(account INTEGER NOT NULL,dialog INTEGER NOT NULL,before_date INTEGER NOT NULL,PRIMARY KEY(account,dialog))");
    }
    @Override public void onConfigure(SQLiteDatabase db) { try(Cursor cursor=db.rawQuery("PRAGMA secure_delete=ON",null)){cursor.moveToFirst();} }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) { throw new IllegalStateException("Unsupported local state migration"); }
    @Override public synchronized void onOpen(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS deleted_ranges(account INTEGER,dialog INTEGER,min_date INTEGER,max_date INTEGER,PRIMARY KEY(account,dialog,min_date,max_date))");
        db.execSQL("CREATE TABLE IF NOT EXISTS archive_usage(singleton INTEGER PRIMARY KEY CHECK(singleton=1),entries INTEGER,bytes INTEGER)");
        db.execSQL("INSERT OR REPLACE INTO archive_usage SELECT 1,COUNT(*),COALESCE(SUM(length(payload)+length(CAST(text AS BLOB))),0) FROM archive");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS archive_added AFTER INSERT ON archive BEGIN UPDATE archive_usage SET entries=entries+1,bytes=bytes+length(NEW.payload)+length(CAST(NEW.text AS BLOB)) WHERE singleton=1; END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS archive_removed AFTER DELETE ON archive BEGIN UPDATE archive_usage SET entries=entries-1,bytes=bytes-length(OLD.payload)-length(CAST(OLD.text AS BLOB)) WHERE singleton=1; END");
        for(String key:cleanup.getAll().keySet()) if(key.startsWith("account_")) {
            long account=Long.parseLong(key.substring(8));
            clearAccountRows(db,account,false);
            synchronized(sessionLock){
                // Recover prior-process markers, but never finish a live clearConfig().
                // Revoke uses the same lock, so a new revocation cannot lose its marker.
                if(!revoked.contains(account)&&!cleanup.edit().remove(key).commit())
                    throw new IllegalStateException("Cannot recover local cleanup");
            }
        }
    }
    private static String key(long account,long dialog,long topic) { return account+":"+dialog+":"+topic; }
    private static String[] args(long... values) { String[] a=new String[values.length];for(int i=0;i<a.length;i++)a[i]=Long.toString(values[i]);return a; }
    public static final class Session {
        public final long account;
        private final long generation;
        private Session(long account,long generation){this.account=account;this.generation=generation;}
    }
    public Session session(java.util.function.LongSupplier owner){synchronized(sessionLock){return session(owner.getAsLong());}}
    public Session session(long account){synchronized(sessionLock){return new Session(account,current(account)?generations.getOrDefault(account,0L):-1L);}}
    public boolean isCurrent(Session session){synchronized(sessionLock){return session!=null&&current(session.account)&&session.generation==generations.getOrDefault(session.account,0L);}}
    private boolean current(long account) { return account>0&&!revoked.contains(account)&&!cleanup.contains("account_"+account); }
    public synchronized boolean save(Session session,Record record){return isCurrent(session)&&session.account==record.account&&save(record);}
    public synchronized void savePosition(Session session,long dialog,long topic,int read,int scroll){if(isCurrent(session))savePosition(session.account,dialog,topic,read,scroll);}
    public synchronized void loadPositions() {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT account,dialog,topic,read_mid,scroll_mid FROM positions LIMIT 5000",null)) {
            while(c.moveToNext()) if(current(c.getLong(0))) positions.put(key(c.getLong(0),c.getLong(1),c.getLong(2)),new Position(c.getInt(3),c.getInt(4)));
        }
    }
    public Position position(long account,long dialog,long topic) { return current(account)?positions.get(key(account,dialog,topic)):null; }
    public synchronized void savePosition(long account,long dialog,long topic,int read,int scroll) {
        if(!current(account)||dialog==0||topic<0||read<=0||scroll<=0)return;
        String key=key(account,dialog,topic);
        if(!positions.containsKey(key)&&positions.size()>=5000)throw new IllegalStateException("Local reading position limit reached");
        Position old=positions.get(key);int high=old==null?read:Math.max(old.read,read);
        ContentValues v=new ContentValues();v.put("account",account);v.put("dialog",dialog);v.put("topic",topic);v.put("read_mid",high);v.put("scroll_mid",scroll);
        getWritableDatabase().insertWithOnConflict("positions",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        positions.put(key,new Position(high,scroll));
    }
    private static void validate(Record r) {
        if(r.account<=0||r.dialog==0||r.id<=0||r.date<0||r.captured<=0||r.text==null||r.text.length()>MAX_TEXT||r.payload.length<8||r.payload.length>MAX_PAYLOAD)
            throw new IllegalArgumentException("Invalid archived message");
    }
    private static long scalar(SQLiteDatabase db,String sql,String[] args) { try(Cursor c=db.rawQuery(sql,args)) {return c.moveToFirst()?c.getLong(0):0;} }
    private boolean forgotten(SQLiteDatabase db,Record r) {

        if(scalar(db,"SELECT COUNT(*) FROM deleted_ranges WHERE account=? AND dialog=? AND min_date<=? AND max_date>=?",args(r.account,r.dialog,r.date,r.date))!=0)return true;
        return scalar(db,"SELECT COUNT(*) FROM forgotten WHERE account=? AND dialog=? AND mid=?",args(r.account,r.dialog,r.id))!=0
            || scalar(db,"SELECT COUNT(*) FROM cleared WHERE account=? AND dialog=? AND before_date>=?",args(r.account,r.dialog,r.date))!=0;
    }
    public synchronized boolean save(Record record) {
        validate(record);SQLiteDatabase db=getWritableDatabase();if(!current(record.account))return false;
        if(forgotten(db,record)||scalar(db,"SELECT COUNT(*) FROM archive WHERE account=? AND dialog=? AND mid=?",args(record.account,record.dialog,record.id))!=0)return false;
        long size=scalar(db,"SELECT bytes FROM archive_usage WHERE singleton=1",null);
        if(scalar(db,"SELECT entries FROM archive_usage WHERE singleton=1",null)>=MAX_RECORDS||size+record.payload.length+record.text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES)
            throw new IllegalStateException("Archive full; export and remove unwanted copies before saving more");
        ContentValues v=new ContentValues();v.put("account",record.account);v.put("dialog",record.dialog);v.put("mid",record.id);v.put("date",record.date);
        v.put("captured",record.captured);v.put("text",record.text);v.put("payload",record.payload);
        return db.insertOrThrow("archive",null,v)!=-1;
    }
    public synchronized List<Record> records(long account) {
        List<Record> rows=new ArrayList<>();SQLiteDatabase db=getReadableDatabase();if(!current(account))return rows;
        try(Cursor c=db.rawQuery("SELECT dialog,mid,date,captured,text,payload FROM archive WHERE account=? ORDER BY captured DESC,mid DESC LIMIT "+MAX_RECORDS,args(account))) {
            while(c.moveToNext())rows.add(new Record(account,c.getLong(0),c.getInt(1),c.getInt(2),c.getLong(3),c.getString(4),c.getBlob(5)));
        }return rows;
    }
    public static final class Preview {
        public final long account,dialog,captured;public final int id;public final String text;
        Preview(long a,long d,int i,long c,String t){account=a;dialog=d;id=i;captured=c;text=t;}
    }
    public synchronized List<Preview> browse(long account,String query,int offset,int limit){
        List<Preview> rows=new ArrayList<>();if(!current(account))return rows;
        String q=query.toLowerCase(Locale.ROOT);int matched=0;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT dialog,mid,captured,text FROM archive WHERE account=? ORDER BY captured DESC,mid DESC",args(account))){
            while(c.moveToNext()){
                String text=c.getString(3);if(!text.toLowerCase(Locale.ROOT).contains(q))continue;
                if(matched++<offset)continue;
                rows.add(new Preview(account,c.getLong(0),c.getInt(1),c.getLong(2),text));
                if(rows.size()>=Math.min(100,Math.max(1,limit)))break;
            }
        }return rows;
    }
    public synchronized boolean hasLocalData() {
        SQLiteDatabase db=getReadableDatabase();
        for(String table:new String[]{"archive","positions","forgotten","cleared"})
            if(scalar(db,"SELECT EXISTS(SELECT 1 FROM "+table+" LIMIT 1)",null)!=0)return true;
        return false;
    }
    public synchronized void forgetRange(long account,long dialog,int minDate,int maxDate) {
        if(account<=0||dialog==0||minDate<0||maxDate<minDate)throw new IllegalArgumentException("Invalid deletion range");
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();try{
            db.execSQL("INSERT OR IGNORE INTO deleted_ranges VALUES(?,?,?,?)",new Object[]{account,dialog,minDate,maxDate});
            db.delete("archive","account=? AND dialog=? AND date>=? AND date<=?",args(account,dialog,minDate,maxDate));
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public synchronized void forget(long account,long dialog,Collection<Integer> ids) {
        if(account<=0||dialog==0)return;SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {for(int id:ids)if(id>0){
            ContentValues v=new ContentValues();v.put("account",account);v.put("dialog",dialog);v.put("mid",id);
            db.insertWithOnConflict("forgotten",null,v,SQLiteDatabase.CONFLICT_IGNORE);
            db.delete("archive","account=? AND dialog=? AND mid=?",args(account,dialog,id));
        }db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    public synchronized void clearDialog(long account,long dialog,int beforeDate) {
        if(account<=0||dialog==0)return;SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            ContentValues v=new ContentValues();v.put("account",account);v.put("dialog",dialog);
            v.put("before_date",Math.max(beforeDate,scalar(db,"SELECT before_date FROM cleared WHERE account=? AND dialog=?",args(account,dialog))));
            db.insertWithOnConflict("cleared",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            db.delete("archive","account=? AND dialog=?",args(account,dialog));
            db.delete("positions","account=? AND dialog=?",args(account,dialog));
            positions.keySet().removeIf(k->k.startsWith(account+":"+dialog+":"));
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    /** Revoke immediately, including work already waiting on Telegram's storage queue. */
    public void revokeAccount(long account) {
        if(account<=0)return;
        synchronized(sessionLock){
            generations.merge(account,1L,Long::sum);
            revoked.add(account);
            positions.keySet().removeIf(k->k.startsWith(account+":"));
            if(!cleanup.edit().putBoolean("account_"+account,true).commit())throw new IllegalStateException("Cannot persist local cleanup");
        }
    }
    public synchronized void finishAccountCleanup(long account) { finishAccountCleanup(account,true); }
    public synchronized void finishAccountCleanup(long account,boolean unblock) { clearAccountRows(getWritableDatabase(),account,unblock); }
    private void clearAccountRows(SQLiteDatabase db,long account,boolean unblock) {
        db.beginTransaction();try {

            for(String table:new String[]{"archive","positions","forgotten","cleared","deleted_ranges"})db.delete(table,"account=?",args(account));
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        positions.keySet().removeIf(k->k.startsWith(account+":"));
        if(!unblock)return;
        synchronized(sessionLock){
            if(!cleanup.edit().remove("account_"+account).commit())throw new IllegalStateException("Cannot complete local cleanup");
            revoked.remove(account);
        }
        // Generation tokens reject old queued writes while a fresh login can use a new token.
    }
    /** Parse one row at a time; every failure rolls back the entire import. */
    public synchronized int importBackup(InputStream in,long account) throws Exception {return importBackup(in,session(account));}
    public synchronized int importBackup(InputStream in,Session session) throws Exception {
        if(!isCurrent(session))throw new IllegalStateException("Account unavailable");
        long account=session.account;
        InputStream bounded=new FilterInputStream(in){
            long read;
            private void count(int n){if(n>0&&(read+=n)>MAX_BACKUP_BYTES)throw new IllegalArgumentException("Backup too large");}
            @Override public int read() throws IOException {int value=super.read();if(value>=0)count(1);return value;}
            @Override public int read(byte[] b,int o,int n) throws IOException {int value=in.read(b,o,n);count(value);return value;}
        };
        JsonReader reader=new JsonReader(new BoundedJsonInput(new InputStreamReader(bounded,StandardCharsets.UTF_8.newDecoder()),MAX_PAYLOAD*4/3+8));
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        int count=0,rows=0,ownRows=0;long size=0;boolean format=false,version=false,messages=false;
        Set<String> top=new HashSet<>();
        try {
            reader.beginObject();
            while(reader.hasNext()){
                String name=reader.nextName();if(!top.add(name))throw new IllegalArgumentException("Duplicate archive field");
                if(name.equals("format")){format="TeleVipArchive".equals(reader.nextString());}
                else if(name.equals("version")){version=number(reader.nextString())==1;}
                else if(name.equals("messages")){
                    messages=true;reader.beginArray();
                    while(reader.hasNext()){
                        if(++rows>MAX_RECORDS)throw new IllegalArgumentException("Too many messages");
                        Map<String,String> values=new HashMap<>();reader.beginObject();
                        while(reader.hasNext()){
                            String key=reader.nextName();if(values.containsKey(key))throw new IllegalArgumentException("Duplicate message field");
                            if(!Arrays.asList("account","dialog","mid","date","captured","text","payload").contains(key))throw new IllegalArgumentException("Unknown message field");
                            if((key.equals("text")||key.equals("payload"))&&reader.peek()!=android.util.JsonToken.STRING)throw new IllegalArgumentException("Invalid message text/payload");
                            String value=reader.nextString();
                            if(value.length()>(key.equals("payload")?MAX_PAYLOAD*4/3+8:MAX_TEXT))throw new IllegalArgumentException("Message too large");
                            values.put(key,value);
                        }reader.endObject();
                        if(values.size()!=7)throw new IllegalArgumentException("Incomplete message");
                        long mid=number(values.get("mid")),date=number(values.get("date"));
                        if(mid<=0||mid>Integer.MAX_VALUE||date<0||date>Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid message ID/date");
                        Record row=new Record(number(values.get("account")),number(values.get("dialog")),(int)mid,(int)date,number(values.get("captured")),values.get("text"),Base64.decode(values.get("payload"),Base64.NO_WRAP));
                        validate(row);size+=row.payload.length+row.text.getBytes(StandardCharsets.UTF_8).length;
                        if(size>MAX_BYTES)throw new IllegalArgumentException("Backup contents too large");
                        if(row.account==account){ownRows++;if(save(session,row))count++;}
                    }reader.endArray();
                }else throw new IllegalArgumentException("Unknown archive field");
            }reader.endObject();
            if(reader.peek()!=android.util.JsonToken.END_DOCUMENT||!format||!version||!messages||rows>0&&ownRows==0)
                throw new IllegalArgumentException("Not a supported archive for this account");
            if(!isCurrent(session))throw new IllegalStateException("Account unavailable");
            db.setTransactionSuccessful();return count;
        }finally{db.endTransaction();}
    }
    private static long number(String value) {
        if(value==null||!value.matches("-?[0-9]{1,19}"))throw new IllegalArgumentException("Invalid numeric identity");
        return Long.parseLong(value);
    }
    public synchronized void exportBackup(OutputStream out,long account) throws Exception {
        Session token=session(account);
        if(!isCurrent(token))throw new IllegalStateException("Account unavailable");
        JsonWriter writer=new JsonWriter(new OutputStreamWriter(out,StandardCharsets.UTF_8));
        writer.beginObject().name("format").value("TeleVipArchive").name("version").value(1).name("messages").beginArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT dialog,mid,date,captured,text,payload FROM archive WHERE account=? ORDER BY captured DESC,mid DESC",args(account))){
            while(c.moveToNext()){
                if(!isCurrent(token))throw new IllegalStateException("Account unavailable");
                writer.beginObject().name("account").value(account).name("dialog").value(c.getLong(0)).name("mid").value(c.getInt(1))
                    .name("date").value(c.getInt(2)).name("captured").value(c.getLong(3)).name("text").value(c.getString(4))
                    .name("payload").value(Base64.encodeToString(c.getBlob(5),Base64.NO_WRAP)).endObject();
            }
        }
        writer.endArray().endObject();writer.flush();
    }
}
