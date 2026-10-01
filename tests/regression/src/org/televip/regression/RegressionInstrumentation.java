package org.televip.regression;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import com.my.televip.Configs.ConfigItem;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.Configs.ConfigPreferences;
import com.my.televip.Database.MessageDatabase;
import com.my.televip.application.ApplicationLoaderHook;
import com.my.televip.calendar.CalendarDate;
import com.my.televip.calendar.ConverterCalendar;
import com.my.televip.features.ghostMode.ReadRequestPermits;
import com.my.televip.features.ghostMode.PhoneDisplayMask;
import com.my.televip.features.otherFeatures.FeatureStateManager;
import com.my.televip.features.ui.DisableChannelSwipeBack;
import com.my.televip.features.ui.HijriDate;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.compat.XposedBridge;
import com.my.televip.logging.Logger;
import com.my.televip.messages.MessageStorage;
import com.my.televip.utils.MessageIdParser;
import com.my.televip.virtuals.messenger.MessagesStorage;
import com.my.televip.settings.Android16Switch;
import com.my.televip.settings.TelegramSettingsCompat;
import org.telegram.ui.Components.SyntheticSettingsTypes.*;
import java.io.File;
import java.util.Arrays;
import java.util.Calendar;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class RegressionInstrumentation extends Instrumentation {
    private final AtomicInteger checks = new AtomicInteger();

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            ApplicationLoaderHook.context = getTargetContext();
            ConfigPreferences.init();
            parser();
            permits();
            switches();
            calendars();
            background();
            database();
            nativeResources();
            initializationAndDisplay();
            settingsCompatibility();
            result.putString("regression", "PASS");
            result.putString("stream", "TELEVIP_REGRESSION_PASS: 9 regression groups passed; " + checks.get() + " assertions\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("regression", "FAIL");
            result.putString("stream", android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void check(boolean condition, String message) {
        checks.incrementAndGet();
        if (!condition) throw new AssertionError(message);
    }

    public static final class OldSettings { public static void f0(OldSettings owner, OldRow row) {} }
    public static final class NewSettings { public static void f0(NewSettings owner, NewRow row) {} }
    public static final class OldFactory {
        public static OldRow a(int id, int c1, int c2, int icon, CharSequence t, CharSequence v, CharSequence sub) { return new OldRow(); }
    }
    public static final class NewFactory {
        public static NewRow a(int id, int c1, int c2, int icon, CharSequence t, CharSequence v, CharSequence sub) { return new NewRow(); }
    }
    public static final class OldUserInfo { public void U(java.util.ArrayList<?> rows, OldAdapter adapter) {} }
    public static final class NewUserInfo { public void U(java.util.ArrayList<?> rows, NewAdapter adapter) {} }
    public static final class AmbiguousUserInfo {
        public void U(java.util.ArrayList<?> rows, OldAdapter adapter) {}
        public void U(java.util.ArrayList<?> rows, NewAdapter adapter) {}
    }
    public static final class WrongUserInfo { public static void U(java.util.ArrayList<?> rows, NewAdapter adapter) {} }

    private void settingsCompatibility() throws Throwable {
        check(TelegramSettingsCompat.settingsClick(OldSettings.class, OldFactory.class).getParameterTypes()[1] == OldRow.class,
                "old Telegram row was not resolved from its factory");
        check(TelegramSettingsCompat.settingsClick(NewSettings.class, NewFactory.class).getParameterTypes()[1] == NewRow.class,
                "renamed Telegram row was not resolved from its factory");
        check(TelegramSettingsCompat.userInfoRows(OldUserInfo.class).getParameterTypes()[1] == OldAdapter.class,
                "old user info adapter not resolved");
        check(TelegramSettingsCompat.userInfoRows(NewUserInfo.class).getParameterTypes()[1] == NewAdapter.class,
                "renamed user info adapter not resolved");
        for (Class<?> invalid : new Class<?>[]{AmbiguousUserInfo.class, WrongUserInfo.class, HookTargets.class}) {
            boolean rejected = false;
            try { TelegramSettingsCompat.userInfoRows(invalid); }
            catch (NoSuchMethodException expected) { rejected = true; }
            check(rejected, "unsafe user info signature accepted: " + invalid.getName());
        }
        boolean rejected = false;
        try { TelegramSettingsCompat.settingsClick(NewSettings.class, OldFactory.class); }
        catch (NoSuchMethodException expected) { rejected = true; }
        check(rejected, "mismatched settings and row factory accepted");

        Throwable[] failure = new Throwable[1];
        runOnMainSync(() -> {
            try {
                for (boolean dark : new boolean[]{false, true}) {
                    Android16Switch toggle = new Android16Switch(getTargetContext(), dark);
                    toggle.setLayoutParams(new android.widget.LinearLayout.LayoutParams(-1, -2));
                    check(toggle.isClickable() && toggle.isFocusable(), "switch cannot receive touch or keyboard input");
                    toggle.setText("A long settings label for layout and accessibility checks");
                    toggle.setChecked(true);
                    AtomicInteger changes = new AtomicInteger();
                    toggle.setOnCheckedChangeListener((button, enabled) -> changes.incrementAndGet());
                    toggle.performClick();
                    check(!toggle.isChecked() && changes.get() == 1, "switch click did not toggle once");
                    toggle.performClick();
                    check(toggle.isChecked() && changes.get() == 2, "switch could not toggle back");
                    toggle.setChecked(true);
                    check(changes.get() == 2, "unchanged switch triggered persistence callback");
                    android.view.accessibility.AccessibilityNodeInfo node = android.view.accessibility.AccessibilityNodeInfo.obtain();
                    toggle.onInitializeAccessibilityNodeInfo(node);
                    check(node.isCheckable() && node.isChecked(), "switch accessibility lost checked state");
                    check("android.widget.Switch".contentEquals(toggle.getAccessibilityClassName()), "switch accessibility role incorrect");
                    node.recycle();
                    int width = Math.round(getTargetContext().getResources().getDisplayMetrics().density * 320);
                    for (int direction : new int[]{android.view.View.LAYOUT_DIRECTION_LTR, android.view.View.LAYOUT_DIRECTION_RTL}) {
                        toggle.setLayoutDirection(direction);
                        toggle.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
                        toggle.layout(0, 0, width, toggle.getMeasuredHeight());
                        toggle.jumpDrawablesToCurrentState();
                        check(toggle.getHeight() >= getTargetContext().getResources().getDisplayMetrics().density * 48,
                                "switch touch target too small");
                        check(direction == android.view.View.LAYOUT_DIRECTION_RTL
                                ? toggle.getCompoundPaddingLeft() > toggle.getCompoundPaddingRight()
                                : toggle.getCompoundPaddingRight() > toggle.getCompoundPaddingLeft(), "switch overlaps label in text direction");
                        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, toggle.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
                        toggle.draw(new android.graphics.Canvas(bitmap));
                        bitmap.recycle();
                    }
                }
            } catch (Throwable error) { failure[0] = error; }
        });
        if (failure[0] != null) throw failure[0];
    }

    private void parser() {
        check(MessageIdParser.parse("1") == 1, "minimum ID");
        check(MessageIdParser.parse("2147483647") == Integer.MAX_VALUE, "maximum ID");
        check(MessageIdParser.parse(" 42 ") == 42, "trimmed ID");
        for (String invalid : new String[]{null, "", " ", "0", "-1", "+1", "2147483648",
                "99999999999999999999999999", "12.3", "1e3", "１２３", "1\n2", "abc"})
            check(MessageIdParser.parse(invalid) == null, "invalid ID accepted: " + invalid);
    }

    public static final class HookTargets {
        public void first() {}
        public void second() {}
    }

    public static final class HostChat {
        public ChatType e;
        public boolean isSwipeBackEnabled(android.view.MotionEvent event) { return true; }
    }

    public static final class ChatType {
        public boolean broadcast, megagroup;
    }

    private void initializationAndDisplay() throws Throwable {
        BaseMethodHook first = new BaseMethodHook() {};
        BaseMethodHook second = new BaseMethodHook() {};
        int initial = XposedBridge.installations.get();
        try (HookInstallation attempt = HookInstallation.begin()) {
            check(HMethod.hookMethod(HookTargets.class, "first", first), "first hook failed");
            check(!HMethod.hookMethod((Class<?>) null, "second", second), "missing class installed");
            check(!attempt.isComplete(), "partial feature marked initialized");
        }
        try (HookInstallation attempt = HookInstallation.begin()) {
            HMethod.hookMethod(HookTargets.class, "first", first);
            HMethod.hookMethod(HookTargets.class, "second", second);
            check(attempt.isComplete(), "partial installation did not recover");
        }
        check(XposedBridge.installations.get() == initial + 2, "retry duplicated successful hook");
        ExecutorService threads = Executors.newFixedThreadPool(4);
        try {
            Future<?>[] jobs = new Future<?>[4];
            for (int n = 0; n < jobs.length; n++) jobs[n] = threads.submit(() -> {
                for (int i = 0; i < 25; i++) HMethod.hookMethod(HookTargets.class, "first", first);
            });
            for (Future<?> job : jobs) job.get(10, TimeUnit.SECONDS);
        } finally { threads.shutdownNow(); }
        check(XposedBridge.installations.get() == initial + 2, "concurrent installation duplicated hook");
        java.lang.reflect.Method member = HookTargets.class.getDeclaredMethod("first");
        try (HookInstallation attempt = HookInstallation.begin()) {
            try {
                HookInstallation.install(member, String.class, () -> { throw new IllegalStateException("Injected install failure"); });
            } catch (IllegalStateException expected) {}
            check(!attempt.isComplete(), "failed installation marked complete");
        }
        AtomicInteger recovered = new AtomicInteger();
        try (HookInstallation attempt = HookInstallation.begin()) {
            HookInstallation.install(member, String.class, recovered::incrementAndGet);
            check(attempt.isComplete() && recovered.get() == 1, "failed install was cached");
            check(!attempt.require(null) && !attempt.isComplete(), "missing required class ignored");
        }
        try (HookInstallation attempt = HookInstallation.begin()) {
            check(!attempt.isComplete(), "empty initialization marked complete");
        }

        FeatureStateManager.saveChat("synthetic.ChatListener");
        FeatureStateManager.saveProfile("synthetic.ProfileListener");
        FeatureStateManager.resetChat();
        check(!FeatureStateManager.isChatEnabled() && FeatureStateManager.isProfileEnabled(), "chat failure reset profile");
        FeatureStateManager.saveChat("synthetic.ChatListener");
        FeatureStateManager.resetProfile();
        check(FeatureStateManager.isChatEnabled() && !FeatureStateManager.isProfileEnabled(), "profile failure reset chat");
        FeatureStateManager.reset();
        check(!FeatureStateManager.isChatEnabled() && !FeatureStateManager.isProfileEnabled(), "reset did not finish");

        PhoneDisplayMask mask = new PhoneDisplayMask();
        String syntheticPhone = "12025550123";
        Object outer = new Object(), inner = new Object();
        check(mask.replacement("+" + syntheticPhone) == null, "network phone masked without UI scope");
        mask.enter(outer, syntheticPhone);
        check("••••••".equals(mask.replacement("+1 (202) 555-0123")), "own displayed phone not masked");
        check(mask.replacement("+12025550124") == null, "different phone masked");
        check(mask.replacement("12025550123text") == null, "non-phone text masked");
        mask.enter(inner, "12025550124");
        check(mask.replacement(syntheticPhone) == null, "outer account leaked into inner scope");
        mask.exit(inner);
        check(mask.replacement(syntheticPhone) != null, "outer display scope lost");
        AtomicInteger leaked = new AtomicInteger();
        Thread network = new Thread(() -> { if (mask.replacement(syntheticPhone) != null) leaked.incrementAndGet(); });
        network.start(); network.join();
        check(leaked.get() == 0, "UI scope leaked to network thread");
        try { throw new IllegalStateException("Synthetic UI failure"); }
        catch (IllegalStateException expected) {}
        finally { mask.exit(outer); }
        check(mask.replacement(syntheticPhone) == null && syntheticPhone.equals("12025550123"), "UI cleanup or source preservation failed");
        mask.enter(outer, syntheticPhone); mask.enter(inner, null);
        check(mask.replacement(syntheticPhone) == null, "disabled nested scope masked phone");
        mask.exit(outer);
        check(mask.replacement(syntheticPhone) == null, "mismatched exit retained scope");

        ClassLoad.classes.put(ClassNames.CHAT_ACTIVITY, HostChat.class);
        DisableChannelSwipeBack.init();
        check(DisableChannelSwipeBack.isEnable, "channel hook failed to initialize");
        int channelHooks = XposedBridge.installations.get();
        DisableChannelSwipeBack.init();
        check(XposedBridge.installations.get() == channelHooks, "channel hook initialized twice");
        HostChat chat = new HostChat();
        java.lang.reflect.Method swipe = HostChat.class.getDeclaredMethod("isSwipeBackEnabled", android.view.MotionEvent.class);
        check(Boolean.TRUE.equals(XposedBridge.invoke(swipe, chat, (Object) null)), "private chat swipe blocked");
        chat.e = new ChatType();
        check(Boolean.TRUE.equals(XposedBridge.invoke(swipe, chat, (Object) null)), "ordinary group swipe blocked");
        chat.e.broadcast = true; chat.e.megagroup = true;
        check(Boolean.TRUE.equals(XposedBridge.invoke(swipe, chat, (Object) null)), "supergroup swipe blocked");
        chat.e.megagroup = false;
        check(Boolean.FALSE.equals(XposedBridge.invoke(swipe, chat, (Object) null)), "broadcast channel swipe allowed");
        ConfigManager.disableChannelSwipeBack.setEnable(false);
        check(Boolean.TRUE.equals(XposedBridge.invoke(swipe, chat, (Object) null)), "disabled channel switch still blocked swipe");

        Calendar value = Calendar.getInstance();
        value.set(2027, Calendar.JANUARY, 23, 10, 15, 0);
        String numeric = HijriDate.formatDate(value, "2027.1.23");
        check("2027.1.23".equals(numeric), "Calendar formatting failed");
        String withTime = HijriDate.formatDate(value, "January 23 2027 10:15");
        check(withTime != null && withTime.contains("2027") && withTime.contains("10:15"), "Calendar time formatting failed");
    }

    private void permits() throws Exception {
        AtomicLong clock = new AtomicLong();
        ReadRequestPermits registry = new ReadRequestPermits(clock::get);
        Object request = new EqualObject(), equivalent = new EqualObject(), account = new Object(), otherAccount = new Object();
        registry.allow(request, account);
        check(!registry.consume(equivalent, account), "equal object bypassed identity");
        check(!registry.consume(request, otherAccount), "wrong account consumed permit");
        check(registry.consume(request, account), "owner could not consume");
        check(!registry.consume(request, account), "permit consumed twice");
        registry.allow(request, account);
        clock.set(60_000_000_000L);
        check(!registry.consume(request, account), "expired permit accepted");
        registry.allow(request, account);
        registry.revoke(request);
        check(!registry.consume(request, account), "revoked permit accepted");

        ExecutorService threads = Executors.newFixedThreadPool(8);
        try {
            Future<?>[] futures = new Future<?>[8];
            for (int i = 0; i < futures.length; i++) futures[i] = threads.submit(() -> {
                for (int n = 0; n < 100; n++) {
                    Object exact = new Object(), owner = new Object();
                    registry.allow(exact, owner);
                    check(!registry.consume(new Object(), owner), "unrelated request accepted");
                    check(registry.consume(exact, owner), "concurrent permit lost");
                    check(!registry.consume(exact, owner), "concurrent permit reused");
                }
            });
            for (Future<?> future : futures) future.get(15, TimeUnit.SECONDS);
        } finally { threads.shutdownNow(); }
        ReadRequestPermits bounded = new ReadRequestPermits();
        for (int n = 0; n < 128; n++) bounded.allow(new Object(), account);
        try { bounded.allow(new Object(), account); throw new AssertionError("unbounded permit registry"); }
        catch (IllegalStateException expected) { check(true, "bounded"); }
    }

    private void switches() {
        AtomicInteger starts = new AtomicInteger();
        ConfigItem privateRead = new ConfigItem(ConfigItem.SWITCH, "privateRead", false, starts::incrementAndGet);
        ConfigItem channelRead = new ConfigItem(ConfigItem.SWITCH, "channelRead", false, starts::incrementAndGet);
        ConfigItem parent = new ConfigItem(ConfigItem.EXPANDABLE_SWITCH, "read", Arrays.asList(privateRead, channelRead));
        check(!parent.isEnable(), "disabled children enabled parent");
        privateRead.setEnable(true);
        check(parent.isEnable(), "parent stale after enabling child");
        parent.runEnabledFeatures();
        check(starts.get() == 1, "private-only cold initialization skipped");
        privateRead.setEnable(false);
        channelRead.setEnable(true);
        check(parent.isEnable(), "channel-only state lost");
        parent.runEnabledFeatures();
        check(starts.get() == 2, "channel-only cold initialization skipped");
        channelRead.setEnable(false);
        check(!parent.isEnable(), "parent stale after disabling children");
        parent.runEnabledFeatures();
        check(starts.get() == 2, "disabled children initialized");
        check(!ConfigPreferences.getBoolean("channelRead"), "child preference not persisted");
    }

    private void calendars() {
        Calendar january = Calendar.getInstance();
        january.set(2027, Calendar.JANUARY, 23);
        CalendarDate date = new CalendarDate(january);
        check(date.getYear() == 2027 && date.getMonth() == 1 && date.getDay() == 23, "Gregorian fields incorrect");
        check(date.getMonthName() != null, "January failed");
        Calendar old = Calendar.getInstance();
        old.add(Calendar.MONTH, -1);
        String formatted = ConverterCalendar.formatDate(old.getTimeInMillis());
        check(formatted != null && !formatted.startsWith("TodayAt"), "previous month labeled today");
        check(ConverterCalendar.formatDate(System.currentTimeMillis()).startsWith("TodayAt"), "today format failed");
    }

    private void background() throws Exception {
        int errors = Logger.errors.get();
        MessageStorage.post(() -> { throw new IllegalStateException("Injected background failure"); });
        drain();
        check(Logger.errors.get() == errors + 1, "background failure was not contained");
    }

    private void database() throws Exception {
        File path = new File(getTargetContext().getFilesDir(), "history-v1.db");
        try (android.database.sqlite.SQLiteDatabase old = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path, null)) {
            old.execSQL("CREATE TABLE messages (id LONG,msg_id INTEGER,msg_count INTEGER,message TEXT,message_date LONG)");
            old.execSQL("INSERT INTO messages VALUES(999,7,1,'legacy-only',1)");
            old.setVersion(1);
        }
        MessageDatabase db = new MessageDatabase(getTargetContext(), path.getAbsolutePath());
        try {
            db.getWritableDatabase();
            try (android.database.Cursor cursor = db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM legacy_messages_unattributed", null)) {
                cursor.moveToFirst();
                check(cursor.getInt(0) == 1, "legacy history not preserved");
            }
            MessageDatabase.AccountSession a = db.session(101), b = db.session(202);
            check(!db.hasHistory(a, 999, 7), "legacy history exposed");
            db.addMessage(a, -100, 7, "A", 1);
            db.addMessage(a, -100, 7, "B", 2);
            db.addMessage(a, -100, 7, "A", 3);
            db.addMessage(b, -100, 7, "other-account", 4);
            db.addMessage(a, -200, 7, "other-chat", 5);
            drain();
            check(db.getHistory(a, -100, 7).size() == 3, "repeated edit version missing");
            check(db.getHistory(a, -100, 7).get(2).text.equals("A"), "edit order incorrect");
            check(db.getHistory(b, -100, 7).size() == 1, "account history mixed");
            check(db.getHistory(a, -200, 7).size() == 1, "dialog history mixed");
            check(db.getHistory(a, 100, 7).isEmpty(), "dialog sign lost");

            db.clearAccount(101);
            check(!db.isCurrent(a), "logout did not revoke session immediately");
            db.addMessage(a, -100, 7, "late-write", 6);
            MessageDatabase.AccountSession relogin = db.session(101);
            db.addMessage(relogin, -100, 7, "new-session", 7);
            drain();
            check(db.getHistory(relogin, -100, 7).size() == 1, "logout allowed stale writes");
            check(db.getHistory(relogin, -100, 7).get(0).text.equals("new-session"), "new session write lost");
            check(db.getHistory(relogin, -200, 7).isEmpty(), "logout left attributed history");
            check(db.getHistory(b, -100, 7).size() == 1, "logout deleted another account");

            CountDownLatch blocked = new CountDownLatch(1), resume = new CountDownLatch(1);
            MessageStorage.post(() -> {
                blocked.countDown();
                try { resume.await(10, TimeUnit.SECONDS); } catch (InterruptedException error) { throw new RuntimeException(error); }
            });
            check(blocked.await(5, TimeUnit.SECONDS), "worker barrier failed");
            try {
                db.clearAccount(202);
                check(getTargetContext().getSharedPreferences("TeleVipHistoryCleanup", 0).contains("pending_202"),
                        "logout tombstone not persisted");
                MessageDatabase restarted = new MessageDatabase(getTargetContext(), path.getAbsolutePath());
                try {
                    restarted.getWritableDatabase();
                    check(restarted.getHistory(restarted.session(202), -100, 7).isEmpty(),
                            "process restart did not complete logout cleanup");
                } finally { restarted.close(); }
            } finally { resume.countDown(); }
            drain();

            int errors = Logger.errors.get();
            db.getWritableDatabase().execSQL("DROP TABLE messages");
            db.addMessage(relogin, -100, 7, "fail-insert", 8);
            drain();
            check(Logger.errors.get() == errors + 1, "SQLite failure escaped worker");
        } finally { db.close(); }
    }

    private void nativeResources() throws Exception {
        HandlerThread thread = new HandlerThread("Fake Telegram storage");
        thread.start();
        Handler queue = new Handler(thread.getLooper());
        try {
            MessagesStorage normal = new MessagesStorage(queue);
            MessageStorage.markMessagesDeleted(normal, -100, new java.util.ArrayList<>(Arrays.asList(7)));
            drain(queue);
            check(normal.database.cursors.size() == 2, "both tables not processed");
            for (com.my.televip.virtuals.SQLite.SQLiteCursor cursor : normal.database.cursors) {
                check(cursor.disposed && cursor.data.reused, "native cursor or buffer leaked");
                check(cursor.data.flags == Integer.MIN_VALUE, "deleted flag not written");
            }
            for (com.my.televip.virtuals.SQLite.SQLitePreparedStatement statement : normal.database.statements)
                check(statement.disposed && statement.steps == 1, "statement leaked or executed twice");

            MessagesStorage empty = new MessagesStorage(queue);
            empty.database.empty = true;
            MessageStorage.markMessagesDeleted(empty, -100, new java.util.ArrayList<>(Arrays.asList(7)));
            drain(queue);
            for (com.my.televip.virtuals.SQLite.SQLitePreparedStatement statement : empty.database.statements)
                check(statement.disposed && statement.steps == 0, "empty query executed update");

            MessagesStorage failing = new MessagesStorage(queue);
            failing.database.failStep = true;
            int errors = Logger.errors.get();
            MessageStorage.markMessagesDeleted(failing, -100, new java.util.ArrayList<>(Arrays.asList(7)));
            drain(queue);
            check(Logger.errors.get() == errors + 1, "native failure escaped account queue");
            check(failing.database.cursors.get(0).disposed && failing.database.cursors.get(0).data.reused, "failure leaked cursor/buffer");
            check(failing.database.statements.get(0).disposed, "failure leaked statement");

            ConfigManager.showDeletedMessages.setEnable(false);
            MessagesStorage disabled = new MessagesStorage(queue);
            MessageStorage.markMessagesDeleted(disabled, -100, new java.util.ArrayList<>(Arrays.asList(7)));
            drain(queue);
            check(disabled.database.cursors.isEmpty(), "disabled feature changed database");
            ConfigManager.showDeletedMessages.setEnable(true);
        } finally { thread.quitSafely(); }
    }

    private void drain() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        MessageStorage.post(done::countDown);
        check(done.await(10, TimeUnit.SECONDS), "storage worker stopped");
    }

    private void drain(Handler handler) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        handler.post(done::countDown);
        check(done.await(10, TimeUnit.SECONDS), "account worker stopped");
    }

    private static final class EqualObject {
        @Override public boolean equals(Object other) { return other instanceof EqualObject; }
        @Override public int hashCode() { return 1; }
    }
}
