package com.my.televip.application;
import android.content.Context;
public final class ApplicationLoaderHook {
    public static Context context;
    public static void setApplicationContext(Context value) { context = value; }
    public static Context getApplicationContext() { return context; }
}
