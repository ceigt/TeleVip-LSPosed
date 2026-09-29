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

    public static boolean isEnable = false;

    public static long id;
    public static File pathImage;
    private static final ThreadLocal<Boolean> openingInRegularViewer = new ThreadLocal<>();

    public static void init() {
        try {
            if (!isEnable) {
                isEnable = true;
                if (ClassLoad.getClass(ClassNames.MESSAGE_OBJECT) != null) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.MESSAGE_OBJECT), Obfuscate.getMethodName("MessageObject", "isSecret"), new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (ConfigManager.secretMediaSave.isEnable()) param.setResult(false);
                        }
                    });
                    if (ClientManager.is(ClientManager.Client.Telegram)) {
                        HMethod.hookMethod(ClassLoad.getClass(ClassNames.MESSAGE_OBJECT), "needDrawBluredPreview", new BaseMethodHook() {
                            @Override
                            protected void beforeMethod(MethodHookParam param) {
                                if (ConfigManager.secretMediaSave.isEnable() && Boolean.TRUE.equals(openingInRegularViewer.get()))
                                    param.setResult(false);
                            }
                        });
                    }
                }
                if (ClassLoad.getClass(ClassNames.CHAT_MESSAGE_CELL_DELEGATE) != null) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_MESSAGE_CELL_DELEGATE), Obfuscate.getMethodName("ChatActivity$ChatMessageCellDelegate", "didPressImage"), ArgsResolver.merge("didPressImage", new Class[]{ClassLoad.getClass(ClassNames.CHAT_MESSAGE_CELL), float.class, float.class, boolean.class}, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            try {
                                if (ConfigManager.secretMediaSave.isEnable() && param.args[0] != null) {
                                    if (ClientManager.is(ClientManager.Client.Telegram)) openingInRegularViewer.set(true);
                                    ChatMessageCell messageCell = new ChatMessageCell(param.args[0]);
                                    if (messageCell.getChatMessageCell() != null) {
                                        MessageObject messageObject = messageCell.getMessageObject();
                                        if (messageObject.getMessageObject() != null) {
                                            TLRPC.Message message = messageObject.getMessageOwner();
                                            if (message.getTtl() > 0) message.setTtl(0);
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
                            openingInRegularViewer.remove();
                        }
                    }));
                }

                if (ClassLoad.getClass(ClassNames.FILE_LOADER) != null) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.FILE_LOADER), Obfuscate.getMethodName("FileLoader", "getPathToMessage"), ArgsResolver.merge("getPathToMessage", new Class[]{ClassLoad.getClass(ClassNames.MESSAGE)}, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            try {
                                if (ConfigManager.secretMediaSave.isEnable() && param.args[0] != null && pathImage != null) {
                                    TLRPC.Message message = new TLRPC.Message(param.args[0]);
                                    if (message.getId() == id) {
                                        param.setResult(pathImage);
                                    }
                                }
                            } catch (Throwable e) {
                                Logger.e(e);
                            }
                        }
                    }));
                }


                if (ConfigManager.secretMediaSave.isEnable() && !ClientManager.is(ClientManager.Client.Telegram))
                    SecretMediaViewer.openMedia();
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
