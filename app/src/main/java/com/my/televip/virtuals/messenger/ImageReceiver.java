package com.my.televip.virtuals.messenger;

import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;

import com.my.televip.compat.XposedHelpers;

public class ImageReceiver {

    public Object imageReceiver;

    public ImageReceiver(Object imageReceiver){
        this.imageReceiver = imageReceiver;
    }

    public ImageLocation getImageLocation() {
        return new ImageLocation(XposedHelpers.callMethod(imageReceiver, Obfuscate.getMethodName("ImageReceiver", "getImageLocation")));
    }

}
