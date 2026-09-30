package com.my.televip.hooks;

import java.lang.reflect.Executable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Tracks a feature's complete installation and preserves successful hooks on retry. */
public final class HookInstallation implements AutoCloseable {
    private static final ThreadLocal<HookInstallation> current = new ThreadLocal<>();
    private static final Map<Executable, Set<Class<?>>> installed = new HashMap<>();
    private final HookInstallation previous;
    private int successes;
    private boolean failed;

    private HookInstallation() {
        previous = current.get();
        current.set(this);
    }

    public static HookInstallation begin() { return new HookInstallation(); }

    public boolean require(Class<?> type) {
        if (type == null) failed = true;
        return type != null;
    }

    public boolean isComplete() { return successes > 0 && !failed; }

    public static void failure() {
        HookInstallation attempt = current.get();
        if (attempt != null) attempt.failed = true;
    }

    /** Callback class identifies the source installation site, not a callback instance. */
    public static synchronized void install(Executable member, Class<?> callbackType, Runnable installer) {
        if (member == null || callbackType == null) {
            failure();
            throw new IllegalArgumentException("Missing hook target or callback");
        }
        Set<Class<?>> callbacks = installed.get(member);
        if (callbacks == null || !callbacks.contains(callbackType)) {
            try { installer.run(); }
            catch (RuntimeException | Error error) { failure(); throw error; }
            if (callbacks == null) {
                callbacks = new HashSet<>();
                installed.put(member, callbacks);
            }
            callbacks.add(callbackType);
        }
        HookInstallation attempt = current.get();
        if (attempt != null) attempt.successes++;
    }

    @Override public void close() {
        if (previous == null) current.remove();
        else current.set(previous);
    }
}
