package com.my.televip.features.messages;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.logging.Logger;
import com.my.televip.messages.MessageStorage;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.messenger.*;
import com.my.televip.virtuals.tgnet.TLRPC;
import java.util.ArrayList;

/** MESSAGE_DELETED push is separate from processUpdateArray, including duplicate late pushes. */
public final class ServerPushDeletes {
    private static boolean installed;
    private ServerPushDeletes() {}
    public static synchronized void init() {
        if (installed) return;
        installed = HMethod.hookMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER),
                Obfuscate.getMethodName("MessagesController", "deleteMessagesByPush"),
                ArgsResolver.merge("deleteMessagesByPush", new Class[]{long.class, ArrayList.class, long.class}, new BaseMethodHook() {
                    @Override protected void beforeMethod(MethodHookParam p) {
                        if (!ConfigManager.showDeletedMessages.isEnable()) return;
                        ArrayList<Integer> ids = null;
                        long dialog = 0; boolean haveDialog = false;
                        // Reviewed R8 permutation keeps the two long arguments in source order.
                        for (Object arg : p.args) {
                            if (arg instanceof Long && !haveDialog) { dialog = (long)arg; haveDialog = true; }
                            if (arg instanceof ArrayList) ids = new ArrayList<>((ArrayList<Integer>)arg);
                        }
                        if (!haveDialog || dialog != 0 && (dialog & 0xffffffffL) == 0 || ids == null || ids.isEmpty()) return;
                        for (Integer id : ids) if (id == null || id <= 0) return;
                        MessagesController controller = new MessagesController(p.thisObject);
                        MessagesStorage storage = controller.getMessagesStorage();
                        long account = storage.getAccountUserId();
                        if (account == 0) return;
                        if (dialog >= 0) for (int id : ids) mark(controller.getDialogMessagesByIds().get(id));
                        ArrayList<Object> cached = controller.getDialogMessage().get(dialog);
                        if (cached != null) for (Object value : cached) {
                            TLRPC.Message owner = new MessageObject(value).getMessageOwner();
                            if (owner != null && ids.contains(owner.getId())) mark(value);
                        }
                        long target = dialog;
                        ArrayList<Integer> snapshot = ids;
                        storage.getStorageQueue().postRunnable(() -> {
                            try { if (storage.getAccountUserId() == account) storage.deletePushMessages(target, snapshot); }
                            catch (Throwable error) { Logger.e(error); }
                        });
                        MessageStorage.markMessagesDeleted(storage, target, snapshot);
                        // Notification removal already happens in the push parser. Avoid the later
                        // native chat/storage deletion, not the user's explicit delete/clear paths.
                        p.setResult(null);
                        Logger.l("Server push deletion retained locally");
                    }
                }));
    }
    private static void mark(Object value) {
        if (value == null) return;
        TLRPC.Message owner = new MessageObject(value).getMessageOwner();
        if (owner != null) owner.setFlags(owner.getFlags() | ShowDeletedMessages.FLAG_DELETED);
    }
}
