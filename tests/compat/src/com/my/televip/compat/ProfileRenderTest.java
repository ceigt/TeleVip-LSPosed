package com.my.televip.compat;

import com.my.televip.features.otherFeatures.ProfileStatusLine;
import com.my.televip.features.otherFeatures.ProfileTextScope;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ProfileRenderTest {
    private static int count;
    private static void check(boolean value) { count++; if (!value) throw new AssertionError("Profile rendering " + count); }
    public static int run() throws Exception {
        count = 0;
        check(ProfileStatusLine.index(4, true, "online", true, false) == 1);
        check(ProfileStatusLine.index(4, true, "online", false, true) == 3);
        check(ProfileStatusLine.index(2, true, null, true, false) == 1);
        check(ProfileStatusLine.index(1, false, null, false, false) == -1);
        check(ProfileStatusLine.withoutId("online\nID: 42").toString().equals("online"));
        check(ProfileStatusLine.withoutId("online\nID: -100123").toString().equals("online"));
        check(ProfileStatusLine.withoutId("online\nID: words").toString().equals("online\nID: words"));
        check(ProfileStatusLine.withoutId("ID: 42").length() == 0);
        check(ProfileStatusLine.withoutId(null).length() == 0);
        check(ProfileStatusLine.hasId("ID: 42", "42"));
        check(!ProfileStatusLine.hasId("online\nID: 420", "42"));
        ProfileTextScope scope = new ProfileTextScope();
        Object token = new Object(), nested = new Object(), status = new Object(), copy = new Object();
        check(scope.current() == null);
        check(scope.status(status, "online").toString().equals("online"));
        scope.enter(new ProfileTextScope.Frame(token, status, copy, status, "42", "offline"));
        try {
            check(scope.status(status, null).toString().equals("offline"));
            check(scope.status(copy, "online").toString().equals("offline"));
            check(scope.status(copy, "").length() == 0);
            check(scope.status(new Object(), "online").toString().equals("online"));
            AtomicBoolean isolated = new AtomicBoolean();
            Thread other = new Thread(() -> isolated.set(scope.current() == null && scope.status(status, "online").toString().equals("online")));
            other.start(); other.join(); check(isolated.get());
            scope.enter(new ProfileTextScope.Frame(nested, null, null, null, null, null));
            try {
                check(scope.status(status, "online").toString().equals("online"));
                throw new IllegalStateException("Synthetic rendering failure");
            } catch (IllegalStateException expected) {
                check(scope.current().token == nested);
            } finally { scope.exit(nested); }
            check(scope.status(status, "online").toString().equals("offline"));
        } finally { scope.exit(token); }
        check(scope.current() == null);
        scope.enter(new ProfileTextScope.Frame(token, status, copy, status, "42", "offline"));
        scope.enter(new ProfileTextScope.Frame(nested, null, null, null, null, null));
        scope.exit(token);
        check(scope.current() == null);
        return count;
    }
    public static void main(String[] args) throws Exception { System.out.println("PROFILE_RENDER_PASS: " + run() + " assertions"); }
}
