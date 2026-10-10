package com.my.televip.virtuals.messenger;
import android.os.*;
public final class Utilities {private static final HandlerThread thread=new HandlerThread("Fake Telegram stage"); static {thread.start();}
private static final DispatchQueue queue=new DispatchQueue(new Handler(thread.getLooper())); public static DispatchQueue getStageQueue(){return queue;}}
