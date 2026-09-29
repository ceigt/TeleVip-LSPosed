package com.my.televip.virtuals.ActionBar;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.utils.Utils;

import java.lang.reflect.Proxy;

import com.my.televip.compat.XposedHelpers;

public class AlertDialog {


    @FunctionalInterface
    public interface OnClick {
        void onClick();
    }

    public static Object click(OnClick lambda) {
        Class<?> listenerClass = ClassLoad.getClass(ClassNames.ALERT_DIALOG_BUTTON_CLICK);
        if (listenerClass != null) {
            return Proxy.newProxyInstance(
                    Utils.classLoader,
                    new Class[]{listenerClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals(Obfuscate.getMethodName("AlertDialog$OnButtonClickListener", "onClick"))) {
                            lambda.onClick();
                        }
                        return null;
                    }
            );
        } else {
            return (DialogInterface.OnClickListener) (dialog, which) -> lambda.onClick();
        }
    }

    Object alertDialog;

    public AlertDialog(Context context) {
        alertDialog = XposedHelpers.newInstance(ClassLoad.getClass(ClassNames.ALERT_DIALOG_BUILDER), context);
    }

    public Dialog getAlertDialog() {
        return (Dialog) XposedHelpers.getObjectField(alertDialog, Obfuscate.getFieldName("AlertDialog$Builder", "alertDialog"));
    }

    public void setTitle(CharSequence title) {
        XposedHelpers.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "setTitle"), title);
    }

    public void setView(View view) {
        XposedHelpers.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "setView"), view);
    }

    public void setMessage(CharSequence message) {
        XposedHelpers.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "setMessage"), message);
    }

    public void setPositiveButton(CharSequence text, Object obj) {
        XposedHelpers.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "setPositiveButton"),
                text, obj
        );
    }

    public void setNegativeButton(CharSequence text, Object obj) {
        XposedHelpers.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "setNegativeButton"),
                text, obj
        );
    }

    public void setNeutralButton(CharSequence text, Object obj) {
        XposedHelpers.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "setNeutralButton"),
                text, obj
        );
    }

    public void show() {
        XposedHelpers.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "show"));
    }

    public Dialog create() {
        return (Dialog) XposedHelpers.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "create"));
    }

    public Runnable getDismissRunnable() {
        return (Runnable) XposedHelpers.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "getDismissRunnable"));
    }

}
