package com.my.televip.features.media;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;

import com.my.televip.Class.ClassNames;
import com.my.televip.Class.ClassLoad;
import com.my.televip.Clients.ClientManager;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.messenger.MessageObject;
import com.my.televip.virtuals.tgnet.TLRPC;
import com.my.televip.virtuals.ui.Cells.ChatMessageCell;
import com.my.televip.virtuals.ui.PhotoViewer;
import com.my.televip.virtuals.ui.SecretMediaViewer;

import java.io.File;

public class SecretMediaSave {

    public static volatile boolean isEnable = false;

    private static final MediaPathOverride paths = new MediaPathOverride();

    public static void rememberPath(Object message, File path) {paths.remember(message, path);}
    private static final MediaOpenScope openingInRegularViewer = new MediaOpenScope();

    public static synchronized void init() {
        try (HookInstallation attempt = HookInstallation.begin()) {
            if (!isEnable) {
                if (attempt.require(ClassLoad.getClass(ClassNames.MESSAGE_OBJECT))) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.MESSAGE_OBJECT), Obfuscate.getMethodName("MessageObject", "isSecret"), new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (ConfigManager.secretMediaSave.isEnable() && openingInRegularViewer.contains(param.thisObject)) param.setResult(false);
                        }
                    });
                    if (ClientManager.is(ClientManager.Client.Telegram)) {
                        HMethod.hookMethod(ClassLoad.getClass(ClassNames.MESSAGE_OBJECT), "needDrawBluredPreview", new BaseMethodHook() {
                            @Override
                            protected void beforeMethod(MethodHookParam param) {
                                if (ConfigManager.secretMediaSave.isEnable() && openingInRegularViewer.contains(param.thisObject))
                                    param.setResult(false);
                            }
                        });
                    }
                }
                if (attempt.require(ClassLoad.getClass(ClassNames.CHAT_MESSAGE_CELL_DELEGATE))) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_MESSAGE_CELL_DELEGATE), Obfuscate.getMethodName("ChatActivity$ChatMessageCellDelegate", "didPressImage"), ArgsResolver.merge("didPressImage", new Class[]{ClassLoad.getClass(ClassNames.CHAT_MESSAGE_CELL), float.class, float.class, boolean.class}, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            try {
                                if (ConfigManager.secretMediaSave.isEnable() && param.args[0] != null) {
                                    ChatMessageCell messageCell = new ChatMessageCell(param.args[0]);
                                    if (messageCell.getChatMessageCell() != null) {
                                        MessageObject messageObject = messageCell.getMessageObject();
                                        if (messageObject.getMessageObject() != null) {
                                            TLRPC.Message message = messageObject.getMessageOwner();
                                            if (message != null) openingInRegularViewer.enter(param, messageObject.getMessageObject(), message::getTtl, message::setTtl);
                                        }
                                        if (!ClientManager.is(ClientManager.Client.Telegram)) bindPhotoViewerToActivity(messageCell);
                                    }
                                }
                            } catch (Throwable e) {
                                Logger.e(e);
                            }
                        }
                        @Override
                        protected void afterMethod(MethodHookParam param) {
                            openingInRegularViewer.exit(param);
                        }
                    }));
                }

                if (attempt.require(ClassLoad.getClass(ClassNames.FILE_LOADER))) {
                    BaseMethodHook pathHook = new BaseMethodHook() {
                        @Override protected void beforeMethod(MethodHookParam param) {
                            if (!ConfigManager.secretMediaSave.isEnable()) return;
                            File path = paths.find(param.args[0]);
                            if (path != null && path.isFile()) param.setResult(path);
                        }
                    };
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.FILE_LOADER), Obfuscate.getMethodName("FileLoader", "getPathToMessage"),
                            ClassLoad.getClass(ClassNames.MESSAGE), pathHook);
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.FILE_LOADER), Obfuscate.getMethodName("FileLoader", "getPathToMessageOZ"),
                            ClassLoad.getClass(ClassNames.MESSAGE), boolean.class, pathHook);
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.FILE_LOADER), Obfuscate.getMethodName("FileLoader", "getPathToMessageOZZ"),
                            ClassLoad.getClass(ClassNames.MESSAGE), boolean.class, boolean.class, pathHook);
                }


                if (ConfigManager.secretMediaSave.isEnable() && !ClientManager.is(ClientManager.Client.Telegram))
                    SecretMediaViewer.openMedia();
                isEnable = attempt.isComplete();
            }
        } catch (Throwable e){
            Logger.e(e);
        }
    }

    private static void bindPhotoViewerToActivity(ChatMessageCell cell) {
        try {
            if (!(cell.getChatMessageCell() instanceof View)) return;
            View view = (View) cell.getChatMessageCell();
            Context context = view.getContext();
            Activity activity = extractActivityFromContext(context);
            if (activity == null) return;

            PhotoViewer.getInstance().setParentActivity(activity);
        } catch (Throwable ignored) {}
    }

    private static Activity extractActivityFromContext(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
