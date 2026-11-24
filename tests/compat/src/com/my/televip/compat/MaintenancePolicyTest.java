package com.my.televip.compat;

import com.my.televip.features.connections.DownloadPremiumScope;
import com.my.televip.features.connections.DownloadTuning;
import com.my.televip.features.otherFeatures.ProfileStatusLine;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MaintenancePolicyTest {
    public static class Operation { public boolean start(Object stream, long offset, boolean priority) { return true; } }
    public static class NewOperation extends Operation { private void budget() {} }
    public static class BadBudget extends Operation { public boolean budget() { return true; } }
    public static class StaticBudget extends Operation { public static void budget() {} }
    public static class OverloadedBudget extends Operation { public void budget() {} public void budget(int ignored) {} }
    public static class Invalid {
        public static boolean start(Object stream, long offset, boolean priority) { return true; }
        public boolean wrong(Object stream, boolean priority, long offset) { return true; }
        public boolean primitive(int stream, long offset, boolean priority) { return true; }
    }
    public static class Ambiguous {
        public boolean start(Object stream, long offset, boolean priority) { return true; }
        public boolean start(String stream, long offset, boolean priority) { return true; }
    }
    public static class IdentityFields {
        public static int staticId = 4;
        public static String staticHash = "original";
        public int requestId = 4;
        public String requestHash = "original";
        public static final String readOnlyHash = "original";
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
        check(DownloadPremiumScope.findPolicy(Operation.class, "budget", "start").getName().equals("start"));
        check(DownloadPremiumScope.findPolicy(NewOperation.class, "budget", "start").getName().equals("budget"));
        check(DownloadPremiumScope.findPolicy(BadBudget.class, "budget", "start") == null);
        check(DownloadPremiumScope.findPolicy(StaticBudget.class, "budget", "start") == null);
        check(DownloadPremiumScope.findPolicy(OverloadedBudget.class, "budget", "start") == null);
        check(DownloadPremiumScope.findPolicy(null, "budget", "start") == null);
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
        check(com.my.televip.features.telegram.ApiIdentity.valid("123", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        check(!com.my.televip.features.telegram.ApiIdentity.valid("0", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        check(!com.my.televip.features.telegram.ApiIdentity.valid("2147483648", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        check(!com.my.televip.features.telegram.ApiIdentity.valid("123", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"));
        check(!com.my.televip.features.telegram.ApiIdentity.valid(null, null));
        Class<IdentityFields> identityType = IdentityFields.class;
        java.lang.reflect.Field staticId = identityType.getField("staticId");
        java.lang.reflect.Field staticHash = identityType.getField("staticHash");
        String testHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        check(com.my.televip.features.telegram.ApiIdentity.apply(null, staticId, staticHash, "123", testHash));
        check(IdentityFields.staticId == 123 && IdentityFields.staticHash.equals(testHash));
        check(!com.my.televip.features.telegram.ApiIdentity.apply(null, staticId, staticHash, "0", testHash));
        check(IdentityFields.staticId == 123 && IdentityFields.staticHash.equals(testHash));
        IdentityFields request = new IdentityFields();
        check(com.my.televip.features.telegram.ApiIdentity.apply(request, identityType.getField("requestId"),
                identityType.getField("requestHash"), "123", testHash));
        check(request.requestId == IdentityFields.staticId && request.requestHash.equals(IdentityFields.staticHash));
        boolean rejectedIdentity = false;
        try {
            com.my.televip.features.telegram.ApiIdentity.apply(null, staticId,
                    identityType.getField("readOnlyHash"), "456", testHash);
        } catch (IllegalArgumentException expected) { rejectedIdentity = true; }
        check(rejectedIdentity && IdentityFields.staticId == 123);
        check(ProfileStatusLine.hasId("online\nID: 42, DC: 5", "42"));
        check(!ProfileStatusLine.hasId("ID: 420, DC: 5", "42"));
        check(ProfileStatusLine.withoutId("online\nID: 42, DC: 5").toString().equals("online"));
        int mib = 1024 * 1024;
        check(DownloadTuning.widenConnections(-1, true, false, false, 32L * mib, 2, 128 * 1024, 16));
        check(DownloadTuning.widenConnections(-1, true, false, false, 32L * mib, 2, 512 * 1024, 4));
        check(!DownloadTuning.widenConnections(2, true, false, false, 32L * mib, 2, 128 * 1024, 16));
        check(!DownloadTuning.widenConnections(-1, false, false, false, 32L * mib, 2, 128 * 1024, 16));
        check(!DownloadTuning.widenConnections(-1, true, true, false, 32L * mib, 2, 128 * 1024, 16));
        check(!DownloadTuning.widenConnections(-1, true, false, true, 32L * mib, 2, 128 * 1024, 16));
        check(!DownloadTuning.widenConnections(-1, true, false, false, 32L * mib - 1, 2, 128 * 1024, 16));
        check(!DownloadTuning.widenConnections(-1, true, false, false, 32L * mib, 8, 512 * 1024, 32));
        check(!DownloadTuning.widenConnections(-1, true, false, false, 32L * mib, 2, 512 * 1024, 5));
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
