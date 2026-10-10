package com.my.televip.features.ghostMode;
import com.my.televip.compat.XC_MethodHook.MethodHookParam;
public final class HideSeen {public static final ReadRequestPermits permits=new ReadRequestPermits(); public static int attached;
public static void saveReadHistory(Object request){} public static boolean consumeReadPermit(Object r,Object c){return permits.consume(r,c);} public static void attachReadAfterSend(MethodHookParam p){attached++;}}
