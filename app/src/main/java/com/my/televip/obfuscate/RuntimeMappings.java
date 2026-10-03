package com.my.televip.obfuscate;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.dex.DexIndex;
import com.my.televip.obfuscate.resolve.Mapping;
import com.my.televip.obfuscate.resolve.Resolver;
import com.my.televip.obfuscate.resolve.TelegramFingerprints;
import com.my.televip.utils.Utils;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** Resolve only this installed APK. Never wait for a scan on the activity thread. */
public final class RuntimeMappings {
    private static final Object lock = new Object();
    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static final List<Runnable> listeners = new ArrayList<>();
    private static volatile Mapping active = new Mapping();
    private static volatile boolean ready;
    private static boolean started;
    private static volatile boolean verifiedLegacy;
    private static volatile String summary = "compatibility scan pending";
    private RuntimeMappings() {}
    public static Mapping active() { return active; }
    public static boolean isReady() { return ready; }
    public static boolean mayUseLegacyTable() { return verifiedLegacy; }
    public static String summary() { return summary; }

    public static void prefetch(String packageName, ClassLoader loader) {
        String text = String.valueOf(loader);
        int start = text.indexOf("/data/app/"), end = start < 0 ? -1 : text.indexOf(".apk", start);
        if (end > start) start(packageName, new File(text.substring(start, end + 4)),
                new File("/data/user/" + android.os.Process.myUid() / 100000 + "/" + packageName + "/cache"));
    }

    public static void whenReady(Context context, Runnable callback) {
        synchronized (lock) {
            if (ready) { ui.post(callback); return; }
            listeners.add(callback);
        }
        start(context.getPackageName(), new File(context.getApplicationInfo().sourceDir), context.getCacheDir());
    }

    private static void start(String pkg, File apk, File cacheDir) {
        synchronized (lock) { if (started) return; started = true; }
        Thread worker = new Thread(() -> {
            long began = System.nanoTime();
            try {
                String digest = sha256(apk);
                // Only this exact, previously audited Play Store APK may supplement missing symbols.
                verifiedLegacy = pkg.equals("org.telegram.messenger") && digest.equals(
                        "7ebd25d6bce15f7195b0b06d0c74d276182e203e6012eabf57d951daccb9b8e5");
                File dir = new File(cacheDir, "televip-compat");
                File cache = new File(dir, "mapping-" + digest + "-f" + TelegramFingerprints.VERSION + "-i1.txt");
                Mapping mapping = null;
                if (cache.isFile()) {
                    try { mapping = Mapping.deserialize(read(cache)); }
                    catch (Exception corrupt) { Logger.w("Compatibility cache rejected: " + corrupt); }
                }
                if (mapping == null) {
                    Resolver.Report report = new Resolver.Report();
                    mapping = new Resolver(DexIndex.fromApk(apk), TelegramFingerprints.owners())
                            .resolve(TelegramFingerprints.all(), report);
                    summary = "resolved=" + mapping.size() + ", ambiguous=" + report.count(Resolver.Outcome.AMBIGUOUS)
                            + ", missing=" + report.count(Resolver.Outcome.UNRESOLVED);
                    if (dir.isDirectory() || dir.mkdirs()) {
                        try {
                            write(cache, mapping.serialize());
                            File[] files = dir.listFiles();
                            if (files != null) for (File old : files)
                                if (!old.equals(cache) && old.getName().startsWith("mapping-") && old.getName().endsWith(".txt")) old.delete();
                        } catch (IOException failure) { Logger.w("Compatibility cache write failed: " + failure); }
                    }
                } else summary = "cached symbols=" + mapping.size();
                active = mapping;
                summary += ", legacy supplement=" + verifiedLegacy + ", time=" + (System.nanoTime()-began)/1000000 + " ms";
                Logger.l("Compatibility: " + summary);
            } catch (Throwable error) {
                // No stale table on an unknown build, even when the scan failed.
                active = new Mapping();
                summary = "scan failed; real names only: " + error;
                Logger.e(error);
            } finally {
                synchronized (lock) {
                    ready = true;
                    for (Runnable listener : listeners) ui.post(listener);
                    listeners.clear();
                }
            }
        }, "TeleVip-compat");
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }
    private static String read(File file) throws IOException {
        if (file.length() > 2 * 1024 * 1024) throw new IOException("Oversized mapping cache");
        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[8192]; int n;
            while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
    private static void write(File file, String value) throws IOException {
        File tmp = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(value.getBytes(StandardCharsets.UTF_8)); out.getFD().sync();
        }
        if (!tmp.renameTo(file)) { tmp.delete(); throw new IOException("Mapping cache rename failed"); }
    }
}
