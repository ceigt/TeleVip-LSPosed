package com.my.televip.virtuals.messenger;

import com.my.televip.Class.ClassNames;
import com.my.televip.Class.ClassLoad;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;

import java.io.File;

import com.my.televip.compat.XposedHelpers;

public class FileLoader {

    Object fileLoader;

    public FileLoader(Object fileLoader){
        this.fileLoader = fileLoader;
    }

    public File getLocalFile(ImageLocation location) {
        return (File) XposedHelpers.callMethod(fileLoader, Obfuscate.getMethodName("FileLoader", "getLocalFile"), location.imageLocation);
    }

    public static FileLoader getInstance(int num) {
        return new FileLoader(XposedHelpers.callStaticMethod(ClassLoad.getClass(ClassNames.FILE_LOADER), Obfuscate.getMethodName("FileLoader", "getInstance"), num));
    }

}
