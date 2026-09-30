package com.my.televip.virtuals.messenger;
import android.os.Handler;
public final class DispatchQueue {
    private final Handler handler;
    DispatchQueue(Handler handler) { this.handler = handler; }
    public void postRunnable(Runnable task) { handler.post(task); }
}
