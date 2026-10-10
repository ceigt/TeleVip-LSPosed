package com.my.televip;
public final class TeleVip {public static boolean uiReady=true;public static final java.util.concurrent.atomic.AtomicInteger starts=new java.util.concurrent.atomic.AtomicInteger();
public static boolean startBackgroundHooks(){starts.incrementAndGet();return true;}public static boolean startHook(){starts.incrementAndGet();return uiReady;}}
