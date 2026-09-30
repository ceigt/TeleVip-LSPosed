package com.my.televip.logging;
import java.util.concurrent.atomic.AtomicInteger;
public final class Logger {
    public static final AtomicInteger errors = new AtomicInteger();
    public static void e(Throwable error) { errors.incrementAndGet(); android.util.Log.e("TeleVipRegression", "Expected or test failure", error); }
    public static void l(String text) { android.util.Log.i("TeleVipRegression", text); }
    public static void w(String text) { android.util.Log.w("TeleVipRegression", text); }
}
