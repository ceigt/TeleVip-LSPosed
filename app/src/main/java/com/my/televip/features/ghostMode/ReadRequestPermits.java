package com.my.televip.features.ghostMode;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.LongSupplier;

/** A one-shot exception for an exact request on its owning connection. */
public final class ReadRequestPermits {
    private static final long LIFETIME_NS = 60_000_000_000L;
    private static final int MAX_PENDING = 128;
    private final IdentityHashMap<Object, Permit> pending = new IdentityHashMap<>();
    private final LongSupplier clock;

    public ReadRequestPermits() { this(System::nanoTime); }
    public ReadRequestPermits(LongSupplier clock) { this.clock = clock; }

    public synchronized void allow(Object request, Object connection) {
        if (request == null || connection == null) throw new IllegalArgumentException("Missing request owner");
        long now = clock.getAsLong();
        prune(now);
        // Fail closed on overload; never broaden the bypass to other requests.
        if (pending.size() >= MAX_PENDING) throw new IllegalStateException("Too many pending read requests");
        pending.put(request, new Permit(connection, now));
    }

    public synchronized boolean consume(Object request, Object connection) {
        prune(clock.getAsLong());
        Permit permit = pending.get(request);
        if (permit == null || permit.connection != connection) return false;
        pending.remove(request);
        return true;
    }

    public synchronized void revoke(Object request) { pending.remove(request); }

    private void prune(long now) {
        Iterator<Map.Entry<Object, Permit>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().created >= LIFETIME_NS) iterator.remove();
        }
    }

    private static final class Permit {
        final Object connection;
        final long created;
        Permit(Object connection, long created) { this.connection = connection; this.created = created; }
    }
}
