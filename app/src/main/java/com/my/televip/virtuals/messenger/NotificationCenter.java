package com.my.televip.virtuals.messenger;

import com.my.televip.Class.ClassNames;
import com.my.televip.Class.ClassLoad;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;

import com.my.televip.compat.XposedHelpers;

public class NotificationCenter {


    private static int messagesDeleted = -1;
    private static int tlSchemeParseException = -1;


    public static int getMessagesDeleted() {
        if (messagesDeleted == -1) {
            messagesDeleted = XposedHelpers.getStaticIntField(ClassLoad.getClass(ClassNames.NOTIFICATION_CENTER), Obfuscate.getFieldName("NotificationCenter", "messagesDeleted"));
        }
        return messagesDeleted;
    }

    public static int getTlSchemeParseException() {
        if (tlSchemeParseException == -1) {
            tlSchemeParseException = XposedHelpers.getStaticIntField(ClassLoad.getClass(ClassNames.NOTIFICATION_CENTER), Obfuscate.getFieldName("NotificationCenter", "tlSchemeParseException"));
        }
        return tlSchemeParseException;
    }
}
