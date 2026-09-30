package com.my.televip.features.messages;

import android.app.Activity;
import android.widget.ScrollView;
import android.widget.TextView;
import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Clients.ClientManager;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.Database.MessageDatabase;
import com.my.televip.Database.MessageDatabase.AccountSession;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.calendar.ConverterCalendar;
import com.my.televip.hooks.HMethod;
import com.my.televip.language.Keys;
import com.my.televip.language.Translator;
import com.my.televip.logging.Logger;
import com.my.televip.messages.MessageStorage;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.application.AndroidUtilities;
import com.my.televip.application.ApplicationLoaderHook;
import com.my.televip.ui.ThemeColors;
import com.my.televip.utils.Utils;
import com.my.televip.virtuals.ActionBar.AlertDialog;
import com.my.televip.virtuals.SQLite.SQLiteCursor;
import com.my.televip.virtuals.SQLite.SQLiteDatabase;
import com.my.televip.virtuals.SettingsIconResolver;
import com.my.televip.virtuals.messenger.BaseController;
import com.my.televip.virtuals.messenger.MessageObject;
import com.my.televip.virtuals.messenger.MessagesStorage;
import com.my.televip.virtuals.messenger.UserConfig;
import com.my.televip.virtuals.tgnet.NativeByteBuffer;
import com.my.televip.virtuals.tgnet.TLRPC;
import com.my.televip.virtuals.ui.BaseFragment;
import com.my.televip.virtuals.ui.ChatActivity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SaveEditsHistory {
    private static final int HISTORY_OPTION = 8353847;
    private static MessageDatabase database;
    private static boolean cleanupHook, menuHook, clickHook, storageHook;
    public static volatile boolean isEnable;

    /** Logout cleanup must remain installed even when history recording is disabled. */
    public static synchronized void initAccountCleanup() {
        if (database == null) {
            database = new MessageDatabase(ApplicationLoaderHook.getApplicationContext());
            MessageStorage.post(() -> database.getWritableDatabase());
        }
        if (!cleanupHook) {
            cleanupHook = HMethod.hookMethod(ClassLoad.getClass(ClassNames.USER_CONFIG),
                    Obfuscate.getMethodName("UserConfig", "clearConfig"), new BaseMethodHook() {
                        @Override protected void beforeMethod(MethodHookParam param) {
                            database.clearAccount(new UserConfig(param.thisObject).getClientUserId());
                        }
                    });
            if (cleanupHook) Logger.l("History logout cleanup installed");
        }
    }

    public static synchronized void init() {
        initAccountCleanup();
        if (!menuHook) menuHook = HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_ACTIVITY),
                Obfuscate.getMethodName("ChatActivity", "fillMessageMenu"),
                ArgsResolver.merge("fillMessageMenu", new Class[]{ClassLoad.getClass(ClassNames.MESSAGE_OBJECT),
                        ArrayList.class, ArrayList.class, ArrayList.class}, new BaseMethodHook() {
                    @Override protected void afterMethod(MethodHookParam param) {
                        if (!ConfigManager.saveEditsHistory.isEnable()) return;
                        ChatActivity chat = new ChatActivity(param.thisObject);
                        MessageObject selected = chat.getSelectedObject();
                        if (selected == null || selected.getMessageOwner() == null) return;
                        long dialogId = selected.getDialogId();
                        int messageId = selected.getMessageOwner().getId();
                        AccountSession session = database.session(
                                new BaseFragment(param.thisObject).getUserConfig().getClientUserId());
                        if (!database.hasHistory(session, dialogId, messageId)) return;
                        int offset = ClientManager.is(ClientManager.Client.Telegraph) ? 1 : 0;
                        ArrayList<Integer> icons = (ArrayList<Integer>) param.args[1 + offset];
                        ArrayList<CharSequence> labels = (ArrayList<CharSequence>) param.args[2 + offset];
                        ArrayList<Integer> options = (ArrayList<Integer>) param.args[3 + offset];
                        if (options.contains(HISTORY_OPTION)) return;
                        labels.add(Translator.get(Keys.EditsHistory));
                        options.add(HISTORY_OPTION);
                        if (!ClientManager.is(ClientManager.Client.Nagram)) icons.add(SettingsIconResolver.getIconSettings());
                    }
                }));
        if (!clickHook) clickHook = HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_ACTIVITY),
                Obfuscate.getMethodName("ChatActivity", "processSelectedOption"),
                ArgsResolver.merge("processSelectedOption", new Class[]{int.class}, new BaseMethodHook() {
                    @Override protected void beforeMethod(MethodHookParam param) {
                        if (!ConfigManager.saveEditsHistory.isEnable() || (int) param.args[0] != HISTORY_OPTION) return;
                        ChatActivity chat = new ChatActivity(param.thisObject);
                        MessageObject selected = chat.getSelectedObject();
                        if (selected == null || selected.getMessageOwner() == null) return;
                        long dialogId = selected.getDialogId();
                        int messageId = selected.getMessageOwner().getId();
                        AccountSession session = database.session(
                                new BaseFragment(param.thisObject).getUserConfig().getClientUserId());
                        Activity activity = Utils.getCurrentActivity();
                        if (activity == null) return;
                        param.setResult(null);
                        MessageStorage.post(() -> {
                            List<MessageDatabase.Edit> edits = database.getHistory(session, dialogId, messageId);
                            StringBuilder text = new StringBuilder();
                            for (MessageDatabase.Edit edit : edits) {
                                String date = ConverterCalendar.formatDate(edit.capturedAt);
                                text.append(Translator.get(Keys.Message)).append(edit.number).append(" ");
                                if (date != null) text.append(date);
                                text.append('\n').append(edit.text).append("\n\n");
                            }
                            AndroidUtilities.runOnUIThread(() -> {
                                try {
                                    if (activity.isFinishing() || activity.isDestroyed() || !database.isCurrent(session)
                                            || !ConfigManager.saveEditsHistory.isEnable()
                                            || new BaseFragment(param.thisObject).getUserConfig().getClientUserId() != session.accountId
                                            || edits.isEmpty()) return;
                                    showHistory(activity, text.toString());
                                } catch (Throwable error) { Logger.e(error); }
                            });
                        });
                    }
                }));
        if (!storageHook) storageHook = HMethod.hookMethod(ClassLoad.getClass(ClassNames.MESSAGES_STORAGE),
                Obfuscate.getMethodName("MessagesStorage", "putMessages"),
                ArgsResolver.merge("putMessages", new Class[]{ClassLoad.getClass(ClassNames.TL_MESSAGES_MESSAGES),
                        long.class, int.class, int.class, boolean.class, int.class, long.class}, new BaseMethodHook() {
                    @Override protected void beforeMethod(MethodHookParam param) {
                        if (!ConfigManager.saveEditsHistory.isEnable()) return;
                        boolean nagram = ClientManager.is(ClientManager.Client.Nagram);
                        if ((int) param.args[nagram ? 0 : 2] != -2) return;
                        Object incoming = param.args[nagram ? 5 : 0];
                        if (incoming == null) return;
                        MessagesStorage storage = new MessagesStorage(param.thisObject);
                        AccountSession session = database.session(storage.getAccountUserId());
                        if (!database.isCurrent(session)) return;
                        List<PendingEdit> pending = new ArrayList<>();
                        long capturedAt = System.currentTimeMillis();
                        for (Object item : new TLRPC.messages_Messages(incoming).getMessages()) {
                            TLRPC.Message message = new TLRPC.Message(item);
                            long dialogId = MessageObject.getDialogId(message);
                            if (message.getId() > 0 && dialogId != 0 && message.getMessage() != null)
                                pending.add(new PendingEdit(dialogId, message.getId(), message.getMessage(), capturedAt));
                        }
                        // Enqueue before Telegram enqueues its write, so the previous version is still available.
                        storage.getStorageQueue().postRunnable(() -> {
                            try { capturePreviousVersions(storage, session, pending); }
                            catch (Throwable error) { Logger.e(error); }
                        });
                    }
                }));
        isEnable = menuHook && clickHook && storageHook && cleanupHook;
        Logger.l("History hooks: menu=" + menuHook + ", click=" + clickHook
                + ", storage=" + storageHook + ", logout=" + cleanupHook);
    }

    private static void capturePreviousVersions(MessagesStorage storage, AccountSession session, List<PendingEdit> pending) {
        if (!database.isCurrent(session) || !ConfigManager.saveEditsHistory.isEnable()
                || storage.getAccountUserId() != session.accountId) return;
        SQLiteDatabase hostDb = storage.getDatabase();
        for (PendingEdit edit : pending) {
            SQLiteCursor cursor = null;
            NativeByteBuffer data = null;
            try {
                cursor = hostDb.queryFinalized(String.format(Locale.US,
                        "SELECT data FROM messages_v2 WHERE mid=%d AND uid=%d", edit.messageId, edit.dialogId), new Object[0]);
                if (!cursor.next()) continue;
                data = cursor.byteBufferValue(0);
                if (data == null || data.nativeByteBuffer == null) continue;
                TLRPC.Message previous = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                if (previous == null || previous.get_Message() == null) continue;
                previous.readAttachPath(data, session.accountId);
                String oldText = previous.getMessage();
                if (oldText != null && !oldText.equals(edit.newText))
                    database.addMessage(session, edit.dialogId, edit.messageId, oldText, edit.capturedAt);
            } catch (Throwable error) { Logger.e(error); }
            finally {
                try { if (data != null && data.nativeByteBuffer != null) data.reuse(); }
                finally { if (cursor != null) cursor.dispose(); }
            }
        }
    }

    private static void showHistory(Activity activity, String history) {
        AlertDialog dialog = new AlertDialog(activity);
        dialog.setTitle(Translator.get(Keys.EditsHistory));
        TextView text = new TextView(activity);
        text.setText(history);
        text.setPadding(32, 32, 32, 32);
        text.setTextSize(16);
        text.setTextColor(ThemeColors.getTextColor());
        text.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(text);
        dialog.setView(scroll);
        dialog.setPositiveButton(Translator.get(Keys.Done), null);
        dialog.show();
    }

    private static final class PendingEdit {
        final long dialogId, capturedAt;
        final int messageId;
        final String newText;
        PendingEdit(long dialogId, int messageId, String newText, long capturedAt) {
            this.dialogId = dialogId; this.messageId = messageId;
            this.newText = newText; this.capturedAt = capturedAt;
        }
    }
}
