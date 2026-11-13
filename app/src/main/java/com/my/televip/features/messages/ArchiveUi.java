package com.my.televip.features.messages;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.*;
import android.text.Editable;
import android.text.TextWatcher;
import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.Database.LocalStateStore;
import com.my.televip.language.Translator;
import com.my.televip.messages.MessageStorage;
import com.my.televip.application.AndroidUtilities;
import com.my.televip.application.ApplicationLoaderHook;
import com.my.televip.virtuals.messenger.UserConfig;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Account-bound previews and user-selected documents; external providers never occupy the storage queue. */
public final class ArchiveUi {
    private static boolean installed;
    private static final AtomicBoolean ioBusy=new AtomicBoolean();
    private static final Map<AlertDialog,Long> windows=Collections.synchronizedMap(new WeakHashMap<>());
    private static int liveRequest;
    private ArchiveUi(){}
    private static SharedPreferences picker(){return ApplicationLoaderHook.getApplicationContext().getSharedPreferences("TeleVipArchivePicker",0);}
    public static synchronized void init(){
        try{ArchiveTransfers.initialize(ApplicationLoaderHook.getApplicationContext());}catch(IOException error){LocalData.issue(error);}
        if(installed)return;
        try(HookInstallation attempt=HookInstallation.begin()){
            HMethod.hookMethod(ClassLoad.getClass(ClassNames.LAUNCH_ACTIVITY),"onActivityResult",int.class,int.class,Intent.class,new BaseMethodHook(){
                @Override protected void beforeMethod(MethodHookParam p){
                    if(handleResult((Activity)p.thisObject,(int)p.args[0],(int)p.args[1],(Intent)p.args[2]))p.setResult(null);
                }
            });
            // External notifications/deep links can switch accounts without replacing the Activity.
            HMethod.hookMethod(ClassLoad.getClass(ClassNames.LAUNCH_ACTIVITY),"onNewIntent",Intent.class,new BaseMethodHook(){
                @Override protected void beforeMethod(MethodHookParam p){dismissAccount(0);ArchiveTransfers.cancelAccount(0);}
            });
            installed=attempt.isComplete();
            if(installed)drainEarlyResult();
        }
    }
    private static final class EarlyResult {
        final WeakReference<Activity> activity;final int request,result;final Intent data;
        EarlyResult(Activity a,int q,int r,Intent d){activity=new WeakReference<>(a);request=q;result=r;data=d==null?null:new Intent(d);}
    }
    private static EarlyResult earlyResult;
    /** Framework entry is available before asynchronous host mapping/config initialization. */
    public static synchronized boolean captureEarlyResult(Activity activity,int request,int result,Intent data){
        if(activity.getSharedPreferences("TeleVipArchivePicker",0).getInt("request",0)!=request||request==0)return false;
        earlyResult=new EarlyResult(activity,request,result,data);
        if(installed)drainEarlyResult();
        return true;
    }
    private static void drainEarlyResult(){
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
            EarlyResult task;
            synchronized(ArchiveUi.class){if(!installed)return;task=earlyResult;earlyResult=null;}
            Activity a=task==null?null:task.activity.get();
            if(a!=null&&alive(a))handleResult(a,task.request,task.result,task.data);
        });
    }
    private static boolean handleResult(Activity activity,int request,int result,Intent data){
        SharedPreferences prefs=picker();if(request==0||prefs.getInt("request",0)!=request)return false;
        long account=prefs.getLong("account",0);boolean export=prefs.getBoolean("export",false);
        prefs.edit().remove("request").remove("account").remove("export").commit();liveRequest=0;
        if(result!=Activity.RESULT_OK)return true;
        Uri uri=data==null?null:data.getData();if(uri==null)return true;
        if(account!=selected()){toast(activity,"ArchiveAccountChanged");return true;}
        transfer(activity,uri,account,export);return true;
    }
    public static void dismissAccount(long account){
        AndroidUtilities.runOnUIThread(()->{
            List<AlertDialog> close=new ArrayList<>();
            synchronized(windows){for(Map.Entry<AlertDialog,Long> e:windows.entrySet())if(account==0||e.getValue()==account)close.add(e.getKey());}
            for(AlertDialog box:close)box.dismiss();
        });
    }
    public static synchronized void revokeAccount(long account){
        ArchiveTransfers.cancelAccount(account);dismissAccount(account);
        SharedPreferences prefs=picker();
        if(prefs.getLong("account",0)==account){
            prefs.edit().remove("request").remove("account").remove("export").commit();
            liveRequest=0;earlyResult=null;
        }
    }
    private static long selected(){return UserConfig.getUserConfig().getClientUserId();}
    private static boolean alive(Activity a){return !a.isFinishing()&&!a.isDestroyed();}
    private static void toast(Activity a,String key){AndroidUtilities.runOnUIThread(()->{if(alive(a))Toast.makeText(a,Translator.get(key),Toast.LENGTH_LONG).show();});}
    private static boolean valid(Activity a,long account,LocalStateStore.Session token){return alive(a)&&selected()==account&&LocalData.store().isCurrent(token);}
    private static void showBound(AlertDialog box,Activity activity,long account){showBound(box,activity,account,()->{});}
    public static void showBound(AlertDialog box,Activity activity,long account,Runnable dismissed){
        LocalStateStore.Session token=LocalData.store().session(account);
        if(!valid(activity,account,token))return;
        windows.put(box,account);box.setOnDismissListener(d->{windows.remove(box);dismissed.run();});box.show();
        WeakReference<AlertDialog> ref=new WeakReference<>(box);
        AndroidUtilities.applicationHandler.postDelayed(new Runnable(){public void run(){
            AlertDialog window=ref.get();if(window==null||!window.isShowing())return;
            if(!valid(activity,account,token)){window.dismiss();return;}
            AndroidUtilities.applicationHandler.postDelayed(this,200);
        }},200);
    }
    public static void chooseExport(Activity activity){choose(activity,true);}
    public static void chooseImport(Activity activity){choose(activity,false);}
    private static void choose(Activity activity,boolean export){
        init();if(!installed){toast(activity,"ArchiveFailed");return;}
        long account=selected();
        AlertDialog confirm=new AlertDialog.Builder(activity).setTitle(Translator.get(export?"ExportArchive":"ImportArchive"))
            .setMessage(Translator.get(export?"ArchiveExportNotice":"ArchiveImportNotice"))
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->{
                if(account!=selected()){toast(activity,"ArchiveAccountChanged");return;}
                if(ioBusy.get()||liveRequest!=0){toast(activity,"ArchiveBusy");return;}
                SharedPreferences prefs=picker();int code=32000+(prefs.getInt("sequence",0)+1)%30000;
                if(!prefs.edit().putInt("sequence",code-32000).putInt("request",code).putLong("account",account).putBoolean("export",export).commit()){
                    toast(activity,"ArchiveFailed");return;
                }
                liveRequest=code;
                Intent intent=new Intent(export?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/json");
                if(export)intent.putExtra(Intent.EXTRA_TITLE,"TeleVip-archive-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date())+".json");
                try{activity.startActivityForResult(intent,code);}catch(Throwable error){liveRequest=0;prefs.edit().remove("request").commit();LocalData.issue(error);toast(activity,"ArchiveFailed");}
            }).create();
        showBound(confirm,activity,account);
    }
    private static void copy(InputStream in,OutputStream out,LocalStateStore.Session token,long account,ArchiveTransfers.Job job)throws IOException{
        byte[] buffer=new byte[32768];long total=0;int n;
        while((n=in.read(buffer))!=-1){
            job.check();
            if(selected()!=account||!LocalData.store().isCurrent(token))throw new IOException("Account changed");
            if((total+=n)>LocalStateStore.MAX_BACKUP_BYTES)throw new IOException("Backup too large");
            out.write(buffer,0,n);
        }
    }
    private static void transfer(Activity activity,Uri uri,long account,boolean export){
        if(!ioBusy.compareAndSet(false,true)){toast(activity,"ArchiveBusy");return;}
        Context context=activity.getApplicationContext();WeakReference<Activity> owner=new WeakReference<>(activity);
        LocalStateStore.Session token=LocalData.store().session(account);
        new Thread(()->{
            ArchiveTransfers.Job job=null;
            try{
                if(selected()!=account||!LocalData.store().isCurrent(token))throw new IOException("Account changed");
                job=ArchiveTransfers.begin(context,account);int count=-1;
                if(selected()!=account||!LocalData.store().isCurrent(token))throw new IOException("Account changed");
                ArchiveTransfers.Job running=job;
                AndroidUtilities.applicationHandler.postDelayed(new Runnable(){public void run(){
                    try{running.check();}catch(IOException closed){return;}
                    if(selected()!=account||!LocalData.store().isCurrent(token)){running.cancel();return;}
                    AndroidUtilities.applicationHandler.postDelayed(this,200);
                }},200);
                if(export){
                    try(OutputStream out=job.writer()){LocalData.store().exportBackup(out,account);}
                    job.check();
                    try(InputStream in=job.reader();android.os.ParcelFileDescriptor fd=context.getContentResolver().openFileDescriptor(uri,"wt",job.signal)){
                        if(fd==null)throw new IOException("Cannot open destination");
                        try(OutputStream out=job.track(new android.os.ParcelFileDescriptor.AutoCloseOutputStream(fd))){copy(in,out,token,account,job);}
                    }
                }else{
                    try(android.os.ParcelFileDescriptor fd=context.getContentResolver().openFileDescriptor(uri,"r",job.signal)){
                        if(fd==null)throw new IOException("Cannot open backup");
                        try(InputStream in=job.track(new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd));OutputStream out=job.writer()){copy(in,out,token,account,job);}
                    }
                    if(selected()!=account||!LocalData.store().isCurrent(token))throw new IOException("Account changed");
                    job.check();try(InputStream in=job.reader()){count=LocalData.store().importBackup(in,token);}
                }
                job.check();if(selected()!=account||!LocalData.store().isCurrent(token))throw new IOException("Account changed");
                int imported=count;
                AndroidUtilities.runOnUIThread(()->{Activity a=owner.get();if(a!=null&&valid(a,account,token))showBound(new AlertDialog.Builder(a).setTitle(Translator.get("ArchiveComplete"))
                    .setMessage(imported<0?Translator.get("ArchiveExported"):Translator.get("ArchiveImported",imported)).setPositiveButton(android.R.string.ok,null).create(),a,account);});
            }catch(Throwable error){LocalData.issue(error);Activity a=owner.get();if(a!=null&&valid(a,account,token))toast(a,"ArchiveFailed");}
            finally{if(job!=null)job.close();ioBusy.set(false);}
        },"TeleVip - Documents").start();
    }
    public static void show(Activity activity){
        long account=selected();LocalStateStore.Session token=LocalData.store().session(account);
        LinearLayout outer=new LinearLayout(activity);outer.setOrientation(LinearLayout.VERTICAL);
        TextView notice=new TextView(activity);notice.setText(Translator.get("ArchiveViewerNotice"));outer.addView(notice);
        EditText search=new EditText(activity);search.setSingleLine(true);search.setHint(Translator.get("SearchArchive"));outer.addView(search);
        ScrollView scroll=new ScrollView(activity);LinearLayout list=new LinearLayout(activity);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);outer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout navigation=new LinearLayout(activity);Button previous=new Button(activity),next=new Button(activity);
        previous.setText("‹");previous.setContentDescription(Translator.get("PreviousArchive"));next.setText("›");next.setContentDescription(Translator.get("MoreArchive"));navigation.addView(previous);navigation.addView(next);outer.addView(navigation);
        int[] page={0},revision={0};AlertDialog box=new AlertDialog.Builder(activity).setTitle(Translator.get("ArchivedMessages")).setView(outer).setPositiveButton(android.R.string.ok,null).create();
        Runnable load=()->{
            int generation=++revision[0],offset=page[0]*50;String query=search.getText().toString();
            AndroidUtilities.applicationHandler.postDelayed(()->{
                if(generation!=revision[0]||!box.isShowing()||!valid(activity,account,token))return;
                MessageStorage.post(()->{try{
                    List<LocalStateStore.Preview> rows=LocalData.store().browse(account,query,offset,51);
                    AndroidUtilities.runOnUIThread(()->{
                        if(generation!=revision[0]||!box.isShowing()||!valid(activity,account,token))return;
                        list.removeAllViews();previous.setEnabled(offset>0);next.setEnabled(rows.size()>50);
                        if(rows.isEmpty()){TextView empty=new TextView(activity);empty.setText(Translator.get("ArchiveEmpty"));list.addView(empty);}
                        for(LocalStateStore.Preview row:rows.subList(0,Math.min(50,rows.size()))){
                            Button item=new Button(activity);item.setAllCaps(false);
                            item.setText(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(new Date(row.captured))+"\n"+(row.text.isEmpty()?Translator.get("ArchivedMedia"):row.text.substring(0,Math.min(160,row.text.length()))));
                            item.setOnClickListener(v->{if(valid(activity,account,token))details(activity,row,()->search.setText(search.getText().toString()));});list.addView(item);
                        }
                    });
                }catch(Throwable error){LocalData.issue(error);toast(activity,"ArchiveFailed");}});
            },200);
        };
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int st,int b,int c){page[0]=0;load.run();}});
        previous.setOnClickListener(v->{page[0]=Math.max(0,page[0]-1);load.run();});next.setOnClickListener(v->{page[0]++;load.run();});showBound(box,activity,account);load.run();
    }
    private static void details(Activity activity,LocalStateStore.Preview row,Runnable removed){
        TextView text=new TextView(activity);text.setTextIsSelectable(true);text.setPadding(24,16,24,16);
        text.setText((row.text.isEmpty()?Translator.get("ArchivedMedia"):row.text)+"\n\n"+Translator.get("ArchiveIdentity",row.dialog,row.id));ScrollView scroll=new ScrollView(activity);scroll.addView(text);
        AlertDialog detail=new AlertDialog.Builder(activity).setTitle(Translator.get("ArchivedMessages")).setView(scroll).setPositiveButton(android.R.string.ok,null)
            .setNeutralButton(Translator.get("ForgetArchive"),(d,w)->{
                LocalStateStore.Session token=LocalData.store().session(row.account);
                AlertDialog confirm=new AlertDialog.Builder(activity).setMessage(Translator.get("ForgetArchiveNotice")).setNegativeButton(android.R.string.cancel,null)
                    .setPositiveButton(android.R.string.ok,(c,n)->{if(!valid(activity,row.account,token))return;MessageStorage.post(()->{
                        try{if(!LocalData.store().isCurrent(token))return;LocalData.store().forget(row.account,row.dialog,Collections.singletonList(row.id));AndroidUtilities.runOnUIThread(()->{if(valid(activity,row.account,token))removed.run();});}
                        catch(Throwable error){LocalData.issue(error);toast(activity,"ArchiveFailed");}
                    });}).create();showBound(confirm,activity,row.account);
            }).create();showBound(detail,activity,row.account);
    }
}
