package com.my.televip.features.connections;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.messenger.FileLoadOperation;

public class DownloadSpeed {

    public static volatile boolean isEnable = false;

    public static synchronized void init() {
        try (HookInstallation attempt = HookInstallation.begin()) {
            if (!isEnable) {

                if (attempt.require(ClassLoad.getClass(ClassNames.FILE_LOAD_OPERATION))) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.FILE_LOAD_OPERATION), Obfuscate.getMethodName("FileLoadOperation", "updateParams"), new BaseMethodHook() {
                        @Override
                        protected void afterMethod(MethodHookParam param) {
                            if (ConfigManager.downloadSpeed.isEnable()) {
                                FileLoadOperation fileLoadOperation = new FileLoadOperation(param.thisObject);

                                int downloadChunkSizeBig = 1024 * 512;
                                int maxDownloadRequests = 8;

                                long defaultMaxFileSize = 1024L * 1024L * 2000L;

                                int maxCdnParts = (int) (defaultMaxFileSize / downloadChunkSizeBig);

                                fileLoadOperation.setDownloadChunkSizeBig(downloadChunkSizeBig);
                                fileLoadOperation.setMaxDownloadRequests(maxDownloadRequests);
                                fileLoadOperation.setMaxDownloadRequestsBig(maxDownloadRequests);
                                fileLoadOperation.setMaxCdnParts(maxCdnParts);
                                param.setResult(null);
                            }

                        }
                    });
                }
                isEnable = attempt.isComplete();
            }
        } catch (Throwable t){
            Logger.e(t);
        }
    }

}
