package com.my.televip.features.messages;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.*;
import android.text.Editable;
import android.text.TextWatcher;
import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.Database.LocalStateStore;
import com.my.televip.language.Translator;
import com.my.televip.messages.MessageStorage;
import com.my.televip.application.AndroidUtilities;
import com.my.televip.virtuals.messenger.UserConfig;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** Uses Android's user-selected documents; no path access, upload or all-account import. */
public final class ArchiveUi {
    private static final int EXPORT=31771, IMPORT=31772;
    private static boolean installed;
    private static Pending pending;
    private static final class Pending {
        final long account;final int request;final WeakReference<Activity> activity;
        Pending(Activity activity,long account,int request){this.account=account;this.request=request;this.activity=new WeakReference<>(activity);}
    }
    private ArchiveUi() {}
    public static synchronized void init() {
        if(installed)return;
        installed=HMethod.hookMethod(ClassLoad.getClass(ClassNames.LAUNCH_ACTIVITY),"onActivityResult",int.class,int.class,Intent.class,new BaseMethodHook(){
            @Override protected void beforeMethod(MethodHookParam p){
                Pending task;
                synchronized(ArchiveUi.class){task=pending;if(task==null||task.request!=(int)p.args[0]||task.activity.get()!=p.thisObject)return;pending=null;}
                p.setResult(null);
                if((int)p.args[1]!=Activity.RESULT_OK)return;
                Activity activity=task.activity.get();Intent result=(Intent)p.args[2];Uri uri=result==null?null:result.getData();
                if(activity==null||uri==null)return;
                if(task.account!=UserConfig.getUserConfig().getClientUserId()){toast(activity,"ArchiveAccountChanged");return;}
                MessageStorage.post(()->{
                    try {
                        if(task.account!=UserConfig.getUserConfig().getClientUserId())throw new IllegalStateException("Account changed");
                        int count=-1;
                        if(task.request==EXPORT)try(OutputStream out=activity.getContentResolver().openOutputStream(uri,"wt")){
                            if(out==null)throw new IOException("Cannot open destination");LocalData.store().exportBackup(out,task.account);
                        }else try(InputStream in=activity.getContentResolver().openInputStream(uri)){
                            if(in==null)throw new IOException("Cannot open backup");count=LocalData.store().importBackup(in,task.account);
                        }
                        int imported=count;
                        AndroidUtilities.runOnUIThread(()->{if(alive(activity))new AlertDialog.Builder(activity).setTitle(Translator.get("ArchiveComplete"))
                                .setMessage(imported<0?Translator.get("ArchiveExported"):Translator.get("ArchiveImported",imported))
                                .setPositiveButton(android.R.string.ok,null).show();});
                    }catch(Throwable error){LocalData.issue(error);toast(activity,error instanceof IllegalArgumentException?"InvalidArchive":"ArchiveFailed");}
                });
            }
        });
    }
    private static boolean alive(Activity activity){return !activity.isFinishing()&&!activity.isDestroyed();}
    private static void toast(Activity activity,String key){AndroidUtilities.runOnUIThread(()->{if(alive(activity))Toast.makeText(activity,Translator.get(key),Toast.LENGTH_LONG).show();});}
    public static void chooseExport(Activity activity){choose(activity,EXPORT);}
    public static void chooseImport(Activity activity){choose(activity,IMPORT);}
    private static void choose(Activity activity,int request){
        init();if(!installed){toast(activity,"ArchiveFailed");return;}
        new AlertDialog.Builder(activity).setTitle(Translator.get(request==EXPORT?"ExportArchive":"ImportArchive"))
                .setMessage(Translator.get(request==EXPORT?"ArchiveExportNotice":"ArchiveImportNotice"))
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(dialog,which)->{
                    synchronized(ArchiveUi.class){if(pending!=null&&pending.activity.get()!=null){toast(activity,"ArchiveBusy");return;}
                        pending=new Pending(activity,UserConfig.getUserConfig().getClientUserId(),request);}
                    Intent intent=new Intent(request==EXPORT?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/json");
                    if(request==EXPORT)intent.putExtra(Intent.EXTRA_TITLE,"TeleVip-archive-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date())+".json");
                    try{activity.startActivityForResult(intent,request);}catch(Throwable error){synchronized(ArchiveUi.class){pending=null;}LocalData.issue(error);toast(activity,"ArchiveFailed");}
                }).show();
    }
    public static void show(Activity activity){
        long account=UserConfig.getUserConfig().getClientUserId();
        MessageStorage.post(()->{
            try {
                List<LocalStateStore.Record> rows=LocalData.store().records(account);
                AndroidUtilities.runOnUIThread(()->{if(alive(activity)&&account==UserConfig.getUserConfig().getClientUserId())showRows(activity,rows);});
            }catch(Throwable error){LocalData.issue(error);toast(activity,"ArchiveFailed");}
        });
    }
    private static void showRows(Activity activity,List<LocalStateStore.Record> rows){
        LinearLayout outer=new LinearLayout(activity);outer.setOrientation(LinearLayout.VERTICAL);
        TextView notice=new TextView(activity);notice.setText(Translator.get("ArchiveViewerNotice"));outer.addView(notice);
        EditText search=new EditText(activity);search.setSingleLine(true);search.setHint(Translator.get("SearchArchive"));outer.addView(search);
        ScrollView scroll=new ScrollView(activity);LinearLayout list=new LinearLayout(activity);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        outer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));int[] limit={50};
        Runnable render=()->{
            list.removeAllViews();String q=search.getText().toString().toLowerCase(Locale.ROOT);int count=0;
            for(LocalStateStore.Record row:rows){
                if(!row.text.toLowerCase(Locale.ROOT).contains(q))continue;
                if(++count>limit[0])break;
                Button item=new Button(activity);item.setAllCaps(false);
                String preview=row.text.isEmpty()?Translator.get("ArchivedMedia"):row.text.substring(0,Math.min(160,row.text.length()));
                item.setText(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(new Date(row.captured))+"\n"+preview);
                item.setOnClickListener(v->details(activity,row,()->{rows.remove(row);search.setText(search.getText().toString());}));list.addView(item);
            }
            if(count==0){TextView empty=new TextView(activity);empty.setText(Translator.get("ArchiveEmpty"));list.addView(empty);}
        };
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void afterTextChanged(Editable e){}
            public void onTextChanged(CharSequence s,int st,int b,int c){limit[0]=50;render.run();}});
        Button more=new Button(activity);more.setText(Translator.get("MoreArchive"));more.setOnClickListener(v->{limit[0]+=50;render.run();});outer.addView(more);
        render.run();new AlertDialog.Builder(activity).setTitle(Translator.get("ArchivedMessages")).setView(outer).setPositiveButton(android.R.string.ok,null).show();
    }
    private static void details(Activity activity,LocalStateStore.Record row,Runnable removed){
        TextView text=new TextView(activity);text.setTextIsSelectable(true);text.setPadding(24,16,24,16);
        text.setText((row.text.isEmpty()?Translator.get("ArchivedMedia"):row.text)+"\n\n"+Translator.get("ArchiveIdentity",row.dialog,row.id));
        ScrollView scroll=new ScrollView(activity);scroll.addView(text);
        new AlertDialog.Builder(activity).setTitle(Translator.get("ArchivedMessages")).setView(scroll).setPositiveButton(android.R.string.ok,null)
            .setNeutralButton(Translator.get("ForgetArchive"),(d,w)->new AlertDialog.Builder(activity).setMessage(Translator.get("ForgetArchiveNotice"))
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(confirm,which)->MessageStorage.post(()->{
                    try{LocalData.store().forget(row.account,row.dialog,Collections.singletonList(row.id));
                        AndroidUtilities.runOnUIThread(()->{if(alive(activity)&&row.account==UserConfig.getUserConfig().getClientUserId())removed.run();});
                        toast(activity,"ArchiveCopyRemoved");}catch(Throwable error){LocalData.issue(error);toast(activity,"ArchiveFailed");}
                })).show()).show();
    }
}
