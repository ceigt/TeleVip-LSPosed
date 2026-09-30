package com.my.televip.features.messages;

import android.util.SparseArray;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Clients.ClientManager;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.logging.Logger;
import com.my.televip.messages.MessageStorage;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.androidx.LongSparseArray;
import com.my.televip.virtuals.messenger.MessageObject;
import com.my.televip.virtuals.messenger.MessagesController;
import com.my.televip.virtuals.messenger.MessagesStorage;
import com.my.televip.virtuals.tgnet.TLRPC;

import java.util.ArrayList;

public class ShowDeletedMessages {

    public static final int FLAG_DELETED = 1 << 31;

    public static volatile boolean isEnable = false;

    public static void markMessagesDeletedForController(MessagesStorage messagesStorage, long dialogId, ArrayList<Integer> delMsg) {
        MessageStorage.markMessagesDeleted(messagesStorage, dialogId, delMsg);
    }

    private static void processDeletedMessage(MessagesController messagesController, Object item, boolean updateDeleteChannelMessages, boolean updateDeleteMessages) {

        if (updateDeleteChannelMessages) {
            TLRPC.TL_updateDeleteChannelMessages channelMessages = new TLRPC.TL_updateDeleteChannelMessages(item);

            LongSparseArray dialogMessage = messagesController.getDialogMessage();

            ArrayList<Object> dialogMessages = dialogMessage.get(-channelMessages.getChannelID());
            if (dialogMessages != null) {
                for (final Object msgObj : dialogMessages) {
                    TLRPC.Message owner = new MessageObject(msgObj).getMessageOwner();
                    if (channelMessages.getMessages().contains(owner.getId())) {
                        owner.setFlags(owner.getFlags() | FLAG_DELETED);
                    }
                }
            }

            markMessagesDeletedForController(messagesController.getMessagesStorage(), -channelMessages.getChannelID(), channelMessages.getMessages());
        }

        if (updateDeleteMessages) {
            ArrayList<Integer> messages = new TLRPC.TL_updateDeleteMessages(item).getMessages();
            SparseArray<Object> dialogMessages = messagesController.getDialogMessagesByIds();
            for (int id : messages) {
                Object msgObj = dialogMessages.get(id);
                if (msgObj == null) {
                    continue;
                } else {
                    TLRPC.Message owner = new MessageObject(msgObj).getMessageOwner();
                    owner.setFlags(owner.getFlags() | FLAG_DELETED);
                }
            }
            markMessagesDeletedForController(messagesController.getMessagesStorage(), 0, messages);
        }
    }

    private static boolean initProcessUpdateArray() {
        try {
            return HMethod.hookMethod(
                    ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER),
                    Obfuscate.getMethodName("MessagesController", "processUpdateArray"),
                    ArgsResolver.merge("processUpdateArray", new Class[]{ArrayList.class, ArrayList.class, ArrayList.class, boolean.class, int.class},
                            new BaseMethodHook() {
                                @Override
                                protected void beforeMethod(MethodHookParam param) {

                                    try {
                                        if (!ConfigManager.showDeletedMessages.isEnable()) return;
                                        ArrayList<Object> updates = (ArrayList<Object>) param.args[0];
                                        MessagesController messagesController = new MessagesController(param.thisObject);
                                        if (updates == null || updates.isEmpty()) {
                                            return;
                                        }

                                        ArrayList<Object> result = new ArrayList<>();

                                        for (Object update : updates) {

                                            String name = update.getClass().getName();

                                            boolean updateDeleteChannelMessages;
                                            boolean updateDeleteMessages;

                                            if (!ClientManager.isTgnetObfuscated()) {
                                                updateDeleteChannelMessages = name.contains("TL_updateDeleteChannelMessages");
                                                updateDeleteMessages = name.contains("TL_updateDeleteMessages");
                                            } else {
                                                updateDeleteChannelMessages = update.getClass().equals(ClassLoad.getClass(ClassNames.TL_UPDATE_DELETE_CHANNEL_MESSAGES));
                                                updateDeleteMessages = update.getClass().equals(ClassLoad.getClass(ClassNames.TL_UPDATE_DELETE_MESSAGES));
                                            }

                                            if (updateDeleteChannelMessages || updateDeleteMessages) {
                                                processDeletedMessage(messagesController, update,
                                                        updateDeleteChannelMessages, updateDeleteMessages);
                                                continue;
                                            }

                                            result.add(update);
                                        }

                                        param.args[0] = result;

                                    } catch (Throwable e) {
                                        Logger.e(e);
                                    }
                                }
                            }));
        } catch (Throwable e) {
            Logger.e(e);
            return false;
        }
    }

    public static synchronized void init() {
        if (!isEnable) {
            // Only server update objects are intercepted. User-initiated deletion,
            // logout and cache cleanup continue through Telegram's native flow.
            isEnable = initProcessUpdateArray();
            if (isEnable) Logger.l("Server deletion hook installed");
        }
        if (ConfigManager.showDeletedMessages.isEnable() && !MessageTimeModifier.loaded)
            MessageTimeModifier.init();
    }
}
