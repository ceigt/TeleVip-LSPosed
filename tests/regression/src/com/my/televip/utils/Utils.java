package com.my.televip.utils;
import android.app.Activity;
public final class Utils { public static String pkgName; public static ClassLoader classLoader; private static Activity activity;
public static Activity getCurrentActivity(){return activity;} public static void setCurrentActivity(Activity value){activity=value;} }
