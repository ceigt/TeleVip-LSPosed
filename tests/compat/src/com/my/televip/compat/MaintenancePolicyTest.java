package com.my.televip.compat;

import com.my.televip.features.connections.DownloadPremiumScope;
import com.my.televip.features.otherFeatures.ProfileStatusLine;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MaintenancePolicyTest {
    public static class Operation { public boolean start(Object stream, long offset, boolean priority) { return true; } }
    public static class Invalid {
        public static boolean start(Object stream, long offset, boolean priority) { return true; }
        public boolean wrong(Object stream, boolean priority, long offset) { return true; }
        public boolean primitive(int stream, long offset, boolean priority) { return true; }
    }
    public static class Ambiguous {
        public boolean start(Object stream, long offset, boolean priority) { return true; }
        public boolean start(String stream, long offset, boolean priority) { return true; }
    }
    private static int count;
    private static void check(boolean condition) { count++; if (!condition) throw new AssertionError("Maintenance policy " + count); }

    public static void main(String[] args) throws Exception {
        check(DownloadPremiumScope.findStart(Operation.class, "start") != null);
        check(DownloadPremiumScope.findStart(Operation.class, "missing") == null);
        check(DownloadPremiumScope.findStart(Invalid.class, "start") == null);
        check(DownloadPremiumScope.findStart(Invalid.class, "wrong") == null);
        check(DownloadPremiumScope.findStart(Invalid.class, "primitive") == null);
        check(DownloadPremiumScope.findStart(Ambiguous.class, "start") == null);
        check(DownloadPremiumScope.findStart(null, "start") == null);
        check(!DownloadPremiumScope.active());
        DownloadPremiumScope.enter();
        try {
            check(DownloadPremiumScope.active());
            AtomicBoolean otherActive = new AtomicBoolean(true);
            Thread thread = new Thread(() -> {
                otherActive.set(DownloadPremiumScope.active());
                DownloadPremiumScope.enter();
                DownloadPremiumScope.exit();
            });
            thread.start(); thread.join();
            check(!otherActive.get());
            DownloadPremiumScope.enter();
            try { throw new IllegalStateException("host download failure"); }
            catch (IllegalStateException expected) { }
            finally { DownloadPremiumScope.exit(); }
            check(DownloadPremiumScope.active());
        } finally { DownloadPremiumScope.exit(); }
        check(!DownloadPremiumScope.active());
        DownloadPremiumScope.exit();
        check(!DownloadPremiumScope.active());
        check(ProfileStatusLine.index(0, false, null) == -1);
        check(ProfileStatusLine.index(2, true, null) == 1);
        check(ProfileStatusLine.index(4, true, "online") == 3);
        check(ProfileStatusLine.index(4, true, "") == 1);
        check(ProfileStatusLine.index(4, true, "  ") == 1);
        check(ProfileStatusLine.index(4, true, "ID: 42") == 1);
        check(ProfileStatusLine.index(4, false, null) == 3);
        check(ProfileStatusLine.hasId("online\nID: 42", "42"));
        check(!ProfileStatusLine.hasId("online\nID: 420", "42"));
        check(!ProfileStatusLine.hasId("online\nID: 42", "420"));
        check(!ProfileStatusLine.hasId(null, "42"));
        System.out.println("MAINTENANCE_POLICY_PASS: " + count + " assertions");
    }
}
