package com.my.televip.virtuals.ui;

import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.features.media.SecretMediaSave;
import com.my.televip.hooks.HMethod;
import com.my.televip.Class.ClassLoad;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.logging.Logger;
import com.my.televip.virtuals.messenger.FileLoader;
import com.my.televip.virtuals.messenger.MessageObject;
import com.my.televip.virtuals.messenger.UserConfig;
import com.my.televip.virtuals.tgnet.TLRPC;

import java.io.File;

public class SecretMediaViewer {

    private static boolean isEnable = false;

    public static synchronized void openMedia() {
        try {
            if (!isEnable) {
                if (ClassLoad.getClass(ClassNames.SECRET_MEDIA_VIEWER) == null) {
                    com.my.televip.hooks.HookInstallation.failure();
                    return;
                }

                isEnable = HMethod.hookMethod(ClassLoad.getClass(ClassNames.SECRET_MEDIA_VIEWER), Obfuscate.getMethodName("SecretMediaViewer", "openMedia"), ArgsResolver.merge("openMedia", new Class[]{ClassLoad.getClass(ClassNames.MESSAGE_OBJECT), ClassLoad.getClass(ClassNames.PHOTO_VIEWER_PROVIDER), java.lang.Runnable.class, java.lang.Runnable.class}, new BaseMethodHook() {
                    @Override
                    protected void beforeMethod(MethodHookParam param) {
                        boolean secretMediaSave = ConfigManager.secretMediaSave != null && ConfigManager.secretMediaSave.isEnable();
                        if (ConfigManager.preventMedia.isEnable() && !secretMediaSave) {
                            param.args[2] = null;
                            param.args[3] = null;

                            MessageObject messageObject = new MessageObject(param.args[0]);
                            if (messageObject.getMessageObject() != null) {
                                TLRPC.Message messageOwner = messageObject.getMessageOwner();
                                if (messageOwner.get_Message() != null) {
                                    messageOwner.setTtl(0);
                                }
                            }
                        }

                        if (secretMediaSave && (param.args.length >= 2 || param.args[0] != null)) {
                            MessageObject messageObject = new MessageObject(param.args[0]);
                            PhotoViewer.PhotoViewerProvider provider = new PhotoViewer.PhotoViewerProvider(param.args[1]);
                            final PhotoViewer.PlaceProviderObject object = provider.getPlaceForPhoto(messageObject, null, 0, true, false);
                            if (object.getImageReceiver().imageReceiver != null) {
                                FileLoader fileLoader = FileLoader.getInstance(UserConfig.getSelectedAccount());
                                File image = fileLoader.getLocalFile(object.getImageReceiver().getImageLocation());
                                if (image != null) {
                                    SecretMediaSave.pathImage = image;
                                    SecretMediaSave.id = messageObject.getMessageOwner().getId();
                                }
                                PhotoViewer.getInstance().openPhoto(messageObject, messageObject.getDialogId(), 0L, 0L, provider, false);
                                param.setResult(null);
                            }
                        }
                    }
                }));
            }
        } catch (Throwable e) {
            Logger.e(e);
        }
    }

}
