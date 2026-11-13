package com.my.televip.features.messages;

import android.content.Context;
import android.os.CancellationSignal;
import java.io.*;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Staging ciphertext has a process-only key. Cancel removes its name before closing remote I/O. */
public final class ArchiveTransfers {
    private static final Set<Job> jobs=new HashSet<>();
    private static File directory;
    private ArchiveTransfers(){}
    public static synchronized void initialize(Context context)throws IOException{
        if(directory!=null)return;
        File cache=context.getCacheDir(),dir=new File(cache,"televip-archive-private");
        if(!dir.isDirectory()&&!dir.mkdir())throw new IOException("Cannot create archive staging");
        // These exact namespaces are owned only by the module, including the previous plaintext format.
        File[] old=cache.listFiles((d,n)->n.startsWith("televip-archive-")&&n.endsWith(".json"));
        if(old==null)throw new IOException("Cannot inspect old staging");
        for(File f:old)if(f.isFile())erase(f);
        File[] stale=dir.listFiles();if(stale==null)throw new IOException("Cannot inspect staging");
        for(File f:stale)if(f.isFile())erase(f);
        directory=dir;
    }
    private static void erase(File file)throws IOException{if(file.exists()&&!file.delete())throw new IOException("Cannot remove archive staging");}
    public static synchronized Job begin(Context context,long account)throws IOException,GeneralSecurityException{
        initialize(context);Job job=new Job(account,File.createTempFile("account-"+account+"-",".enc",directory));jobs.add(job);return job;
    }
    public static void cancelAccount(long account){
        List<Job> cancel;
        synchronized(ArchiveTransfers.class){cancel=new ArrayList<>(jobs);}
        for(Job job:cancel)if(account==0||job.account==account)job.cancel();
    }
    public static final class Job implements Closeable {
        public final long account;
        public final CancellationSignal signal=new CancellationSignal();
        private final File file;
        private final byte[] key=new byte[32],iv=new byte[12];
        private final AtomicBoolean cancelled=new AtomicBoolean();
        private final Set<Closeable> streams=new HashSet<>();
        private Job(long account,File file){this.account=account;this.file=file;SecureRandom random=new SecureRandom();random.nextBytes(key);random.nextBytes(iv);}
        public void check()throws IOException{if(cancelled.get())throw new IOException("Archive operation cancelled");}
        private Cipher cipher(int mode)throws GeneralSecurityException{
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));return cipher;
        }
        public OutputStream writer()throws IOException,GeneralSecurityException{
            synchronized(this){check();Cipher c=cipher(Cipher.ENCRYPT_MODE);return new CipherOutputStream(new FileOutputStream(file),c);}
        }
        public InputStream reader()throws IOException,GeneralSecurityException{
            synchronized(this){check();Cipher c=cipher(Cipher.DECRYPT_MODE);return new CipherInputStream(new FileInputStream(file),c);}
        }
        public synchronized <T extends Closeable>T track(T stream)throws IOException{
            if(cancelled.get()){stream.close();check();}streams.add(stream);return stream;
        }
        public void cancel(){
            if(!cancelled.compareAndSet(false,true))return;
            List<Closeable> close;synchronized(this){
                try{erase(file);}catch(IOException error){LocalData.issue(error);}
                close=new ArrayList<>(streams);streams.clear();
            }
            // Provider close/cancel may call another process; never block the logout/UI thread.
            if(!close.isEmpty())new Thread(()->{for(Closeable stream:close)try{stream.close();}catch(IOException ignored){}signal.cancel();},"TeleVip - Cancel documents").start();
            else new Thread(signal::cancel,"TeleVip - Cancel document open").start();
        }
        @Override public void close(){
            cancel();synchronized(ArchiveTransfers.class){jobs.remove(this);}
            synchronized(this){Arrays.fill(key,(byte)0);}
        }
    }
}
