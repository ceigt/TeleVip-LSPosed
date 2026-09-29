package com.my.televip.virtuals.messenger;

import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;

import com.my.televip.compat.XposedHelpers;

public class FileLoadOperation {

    private final Object fileOperation;

    public FileLoadOperation(Object fileOperation){ this.fileOperation = fileOperation; }

    public void setDownloadChunkSizeBig(int v){
        XposedHelpers.setIntField(fileOperation, Obfuscate.getFieldName("FileLoadOperation", "downloadChunkSizeBig"), v);
    }

    public void setMaxDownloadRequests(int v){
        XposedHelpers.setIntField(fileOperation, Obfuscate.getFieldName("FileLoadOperation", "maxDownloadRequests"), v);
    }

    public void setMaxDownloadRequestsBig(int v){
        XposedHelpers.setIntField(fileOperation, Obfuscate.getFieldName("FileLoadOperation", "maxDownloadRequestsBig"), v);
    }

    public void setMaxCdnParts(int v){
        XposedHelpers.setIntField(fileOperation, Obfuscate.getFieldName("FileLoadOperation", "maxCdnParts"), v);
    }

}
