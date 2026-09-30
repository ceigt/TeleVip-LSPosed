package com.my.televip.features.ghostMode;

import com.my.televip.Callback.IntCallback;
import com.my.televip.compat.XC_MethodHook.MethodHookParam;
import com.my.televip.virtuals.messenger.BaseController;
import com.my.televip.virtuals.messenger.UserConfig;
import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Clients.ClientManager;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.application.AndroidUtilities;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.SQLite.SQLiteCursor;
import com.my.televip.virtuals.messenger.MessagesController;
import com.my.televip.virtuals.messenger.MessagesStorage;
import com.my.televip.virtuals.messenger.Utilities;
import com.my.televip.virtuals.tgnet.ConnectionsManager;
import com.my.televip.virtuals.tgnet.RequestDelegate;
import com.my.televip.virtuals.tgnet.TLRPC;

import com.my.televip.compat.XposedHelpers;

public class HideSeen {


    public static Object TLChannels_readHistory;
    public static Object TLMessages_readHistory;

    private static final ReadRequestPermits readPermits = new ReadRequestPermits();

    public static boolean consumeReadPermit(Object request, Object connection) {
        return readPermits.consume(request, connection);
    }

    public static void sendFakeReadResponse(Object request, Object onCompleteOrig, Object timestampCallback) {
        try {
            TLRPC.TL_messages_affectedMessages fakeRes = new TLRPC.TL_messages_affectedMessages();
            fakeRes.setPts(-1);
            fakeRes.setPtsCount(0);
            boolean affected = isTLMessagesReadHistoryRequest(request)
                    || request.getClass().equals(ClassLoad.getClass(ClassNames.TL_MESSAGES_READ_MESSAGE_CONTENTS));
            Object response = affected ? fakeRes.getTL_messages_affectedMessages()
                    : XposedHelpers.newInstance(ClassLoad.getClass("org.telegram.tgnet.TLRPC$TL_boolTrue"));
            RequestDelegate onComplete = new RequestDelegate(onCompleteOrig);
            Utilities.getStageQueue().postRunnable(() -> {
                try {
                    if (onComplete.requestDelegate != null) {
                        onComplete.run(response, null);
                    } else if (timestampCallback != null) {
                        XposedHelpers.callMethod(timestampCallback,
                                Obfuscate.getMethodName("RequestDelegateTimestamp", "run"), response, null, 0L);
                    }
                } catch (Throwable e) {
                    Logger.e(e);
                }
            });
        } catch (Throwable e) {
            Logger.e(e);
        }
    }

    public static boolean isTLMessagesReadHistoryRequest(Object object) {
        if (!ClientManager.isTgnetObfuscated()) {
            return object.getClass().getName().contains("TL_messages_readHistory");
        } else {
            return object.getClass().getName().equals(Obfuscate.getClassName(ClassNames.TL_MESSAGES_READ_HISTORY));
        }
    }

    public static boolean isTLChannelsReadHistoryRequest(Object object) {
        if (!ClientManager.isTgnetObfuscated()) {
            return object.getClass().getName().contains("TL_channels_readHistory");
        } else {
            return object.getClass().getName().equals(Obfuscate.getClassName(ClassNames.TL_CHANNELS_READ_HISTORY));
        }
    }

    public static boolean isReadMessageRequest(Object object) {
        boolean privateHide = ConfigManager.hideSeenPrivateChat.isEnable();
        boolean channelHide = ConfigManager.hideSeenChannel.isEnable();

        boolean readHistory;
        boolean readDiscussion;
        boolean encryptedHistory;
        boolean readMessageContents;
        boolean channelReadMessageContents;
        boolean channelReadHistory;

        if (!ClientManager.isTgnetObfuscated()) {
            String className = object.getClass().getName();

            readHistory = className.contains("TL_messages_readHistory");
            readDiscussion = className.contains("TL_messages_readDiscussion");
            encryptedHistory = className.contains("TL_messages_readEncryptedHistory");
            readMessageContents = className.contains("TL_messages_readMessageContents");
            channelReadMessageContents = className.contains("TL_channels_readMessageContents");
            channelReadHistory = className.contains("TL_channels_readHistory");
        } else {
            Class<?> objectClass = object.getClass();

            readHistory = objectClass.equals(ClassLoad.getClass(
                    Obfuscate.getClassName(ClassNames.TL_MESSAGES_READ_HISTORY)));

            readDiscussion = objectClass.equals(ClassLoad.getClass(
                    Obfuscate.getClassName(ClassNames.TL_MESSAGES_READ_DISCUSSION)));

            encryptedHistory = objectClass.equals(ClassLoad.getClass(
                    Obfuscate.getClassName(ClassNames.TL_MESSAGES_READ_ENCRYPTED_HISTORY)));

            readMessageContents = objectClass.equals(ClassLoad.getClass(
                    Obfuscate.getClassName(ClassNames.TL_MESSAGES_READ_MESSAGE_CONTENTS)));

            channelReadMessageContents = objectClass.equals(ClassLoad.getClass(
                    Obfuscate.getClassName(ClassNames.TL_CHANNELS_READ_MESSAGE_CONTENTS)));

            channelReadHistory = objectClass.equals(ClassLoad.getClass(
                    Obfuscate.getClassName(ClassNames.TL_CHANNELS_READ_HISTORY)));
        }

        if (!(readHistory || readDiscussion ||
                (privateHide && encryptedHistory) ||
                (privateHide && readMessageContents) ||
                (channelHide && channelReadMessageContents) ||
                (channelHide && channelReadHistory))) {
            return false;
        }

        if (privateHide && channelHide) {
            return true;
        }

        if (readHistory || readDiscussion) {
            String objectName;
            if (!ClientManager.isTgnetObfuscated()){
                objectName = object.getClass().getSimpleName();
            } else {
                if (readHistory)
                    objectName = "TLRPC$TL_messages_readHistory";
                else
                    objectName = "TLRPC$TL_messages_readDiscussion";
            }
            TLRPC.InputPeer inputPeer = new TLRPC.InputPeer(
                    XposedHelpers.getObjectField(object, Obfuscate.getFieldName(objectName,"peer")));

            boolean isChannelOrGroup =
                    inputPeer.getChannel_id() > 0 ||
                            inputPeer.getChat_id() > 0;

            if (privateHide && !channelHide) {
                return !isChannelOrGroup;
            }

            if (channelHide && !privateHide) {
                return isChannelOrGroup;
            }

            return false;
        }

        return true;
    }

    public static void saveReadHistory(Object object) {
        if (HideSeen.TLChannels_readHistory == null && HideSeen.isTLChannelsReadHistoryRequest(object)) {
            HideSeen.TLChannels_readHistory = object;
        } else if (HideSeen.TLMessages_readHistory == null && HideSeen.isTLMessagesReadHistoryRequest(object)) {
            HideSeen.TLMessages_readHistory = object;
        }
    }

    public static void attachReadAfterSend(MethodHookParam param) {
        if (!ConfigManager.hideSeen.isEnable() || !ConfigManager.markReadAfterSend.isEnable()) return;
        TLRPC.InputPeer peer = extractPeerFromSendObject(param.args[0]);
        if (peer == null || peer.getInputPeer() == null) return;
        boolean group = peer.getChat_id() != 0 || peer.getChannel_id() != 0;
        if (group ? !ConfigManager.hideSeenChannel.isEnable() : !ConfigManager.hideSeenPrivateChat.isEnable()) return;
        int account = new BaseController(param.thisObject).getCurrentAccount();
        long accountId = UserConfig.getInstance(account).getClientUserId();
        if (accountId == 0) return;
        // Callback-free requests use Telegram's implicit Updates handling; preserve that flow.
        if (param.args[1] == null && param.args[2] == null) return;
        // Preserve both normal and timestamp callback forms. Read only after a successful send.
        int index = param.args[1] != null ? 1 : 2;
        Class<?> callbackType = ClassLoad.getClass(index == 1
                ? ClassNames.REQUEST_DELEGATE : ClassNames.REQUEST_DELEGATE_TIMESTAMP);
        param.args[index] = RequestDelegate.afterSuccess(param.args[index], callbackType,
                () -> handleReadAfterSend(account, accountId, peer));
    }

    private static boolean accountStillActive(int account, long accountId) {
        return accountId != 0 && UserConfig.getInstance(account).getClientUserId() == accountId;
    }

    private static void handleReadAfterSend(int account, long accountId, TLRPC.InputPeer peer) {
        if (!accountStillActive(account, accountId)
                || !ConfigManager.hideSeen.isEnable() || !ConfigManager.markReadAfterSend.isEnable()) return;
        MessagesStorage messagesStorage = MessagesStorage.getInstance(account);
        getDialogMaxMessageId(messagesStorage, getDialogId(peer), messageId -> {
            try {
                if (accountStillActive(account, accountId))
                    markReadOnServer(account, accountId, messageId, peer);
            } catch (Throwable error) { Logger.e(error); }
        });
    }

    public static void getDialogMaxMessageId(MessagesStorage messagesStorage, long dialogId, IntCallback callback) {
        messagesStorage.getStorageQueue().postRunnable(() -> {
            SQLiteCursor cursor = null;
            int max = 0;
            try {
                cursor = messagesStorage.getDatabase().queryFinalized(
                        "SELECT MAX(mid) FROM messages_v2 WHERE uid = " + dialogId, new Object[0]);
                if (cursor.next()) max = cursor.intValue(0);
            } catch (Throwable error) { Logger.e(error); }
            finally {
                try { if (cursor != null) cursor.dispose(); }
                catch (Throwable error) { Logger.e(error); }
            }
            final int messageId = max;
            AndroidUtilities.runOnUIThread(() -> {
                try { if (messageId > 0) callback.run(messageId); }
                catch (Throwable error) { Logger.e(error); }
            });
        });
    }

    private static void markReadOnServer(int account, long accountId, int messageId, TLRPC.InputPeer peer) {
        Object requestObject = null;
        try {
            if (messageId <= 0 || !accountStillActive(account, accountId)
                    || !ConfigManager.hideSeen.isEnable() || !ConfigManager.markReadAfterSend.isEnable()) return;
            boolean group = peer.getChat_id() != 0 || peer.getChannel_id() != 0;
            if (group ? !ConfigManager.hideSeenChannel.isEnable() : !ConfigManager.hideSeenPrivateChat.isEnable()) return;
            if (peer.getChannel_id() != 0) {
                TLRPC.TL_channels_readHistory request;
                if (!ClientManager.is(ClientManager.Client.Nagram)) {
                    request = new TLRPC.TL_channels_readHistory();
                    request.setChannel(MessagesController.getInputChannel(peer));
                } else {
                    if (TLChannels_readHistory == null) return;
                    request = new TLRPC.TL_channels_readHistory(XposedHelpers.newInstance(TLChannels_readHistory.getClass()));
                    request.setChannel(MessagesController.getInstance(account).getInputChannelForAccount(peer.getChannel_id()));
                }
                request.setMax_id(messageId);
                requestObject = request.getTL_channels_readHistory();
            } else {
                TLRPC.TL_messages_readHistory request;
                if (!ClientManager.is(ClientManager.Client.Nagram)) {
                    request = new TLRPC.TL_messages_readHistory();
                } else {
                    if (TLMessages_readHistory == null) return;
                    request = new TLRPC.TL_messages_readHistory(XposedHelpers.newInstance(TLMessages_readHistory.getClass()));
                }
                request.setPeer(peer);
                request.setMax_id(messageId);
                requestObject = request.getTL_messages_readHistory();
            }
            ConnectionsManager connection = ConnectionsManager.getInstance(account);
            readPermits.allow(requestObject, connection.getInstanceObject());
            connection.sendRequest(requestObject, RequestDelegate.run((response, error) -> {
                try {
                    if (error == null && response != null && accountStillActive(account, accountId)
                            && ClassLoad.getClass(ClassNames.TL_MESSAGES_AFFECTED).isInstance(response)) {
                        TLRPC.TL_messages_affectedMessages affected = new TLRPC.TL_messages_affectedMessages(response);
                        MessagesController controller = MessagesController.getInstance(account);
                        if (!ClientManager.is(ClientManager.Client.Nagram))
                            controller.processNewDifferenceParams(-1, affected.getPts(), -1, affected.getPtsCount());
                        else controller.processNewDifferenceParams(affected.getPts(), -1, affected.getPtsCount());
                    }
                } catch (Throwable failure) { Logger.e(failure); }
            }));
        } catch (Throwable error) {
            if (requestObject != null) readPermits.revoke(requestObject);
            Logger.e(error);
        }
    }

    private static TLRPC.InputPeer extractPeerFromSendObject(Object object) {
        if (!ClientManager.isTgnetObfuscated()) {
        String className = object.getClass().getName();
        if (className.contains("TL_messages_sendMessage") ||
                className.contains("TL_messages_sendMedia") ||
                className.contains("TL_messages_sendMultiMedia"))
            return new TLRPC.InputPeer(getPeer(object));
        } else {
            Class<?> objectClass = object.getClass();
            if (objectClass.equals(ClassLoad.getClass(Obfuscate.getClassName(ClassNames.TL_MESSAGES_SEND_MESSAGE))) ||
                    objectClass.equals(ClassLoad.getClass(Obfuscate.getClassName(ClassNames.TL_MESSAGES_SEND_MEDIA))) ||
                            objectClass.equals(ClassLoad.getClass(Obfuscate.getClassName(ClassNames.TL_MESSAGES_SEND_MULTI_MEDIA))))
                return new TLRPC.InputPeer(getPeer(object));
        }
        return null;
    }

    private static Object getPeer(Object msg) {
        Class<?> objectClass = msg.getClass();
        String msgName = null;
        if (ClientManager.isTgnetObfuscated()) {
            if (objectClass.equals(ClassLoad.getClass(Obfuscate.getClassName(ClassNames.TL_MESSAGES_SEND_MESSAGE))))
                msgName = "TLRPC$TL_messages_sendMessage";
            if (objectClass.equals(ClassLoad.getClass(Obfuscate.getClassName(ClassNames.TL_MESSAGES_SEND_MEDIA))))
                msgName = "TLRPC$TL_messages_sendMedia";
            if (objectClass.equals(ClassLoad.getClass(Obfuscate.getClassName(ClassNames.TL_MESSAGES_SEND_REACTION))))
                msgName = "TLRPC$TL_messages_sendReaction";
            if (objectClass.equals(ClassLoad.getClass(Obfuscate.getClassName(ClassNames.TL_MESSAGES_SEND_PAID_REACTION))))
                msgName = "TLRPC$TL_messages_sendPaidReaction";
            if (objectClass.equals(ClassLoad.getClass(Obfuscate.getClassName(ClassNames.TL_MESSAGES_SEND_MULTI_MEDIA))))
                msgName = "TLRPC$TL_messages_sendMultiMedia";
            return XposedHelpers.getObjectField(msg, Obfuscate.getFieldName(msgName, "peer"));
        } else {
            return XposedHelpers.getObjectField(msg, "peer");
        }
    }

    public static Long getDialogId(TLRPC.InputPeer peer) {
        long dialogId;
        if (peer.getChat_id() != 0) {
            dialogId = -peer.getChat_id();
        } else if (peer.getChannel_id() != 0) {
            dialogId = -peer.getChannel_id();
        } else {
            dialogId = peer.getUser_id();
        }

        return dialogId;
    }

}
