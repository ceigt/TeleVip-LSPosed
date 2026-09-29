package com.my.televip.virtuals.ActionBar;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Clients.ClientManager;
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
        if (ClientManager.is(ClientManager.Client.Telegram))
            return (DialogInterface.OnClickListener) (dialog, which) -> lambda.onClick();
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
    private final android.app.AlertDialog.Builder nativeBuilder;
    private Dialog nativeDialog;

    public AlertDialog(Context context) {
        if (ClientManager.is(ClientManager.Client.Telegram)) {
            nativeBuilder = new android.app.AlertDialog.Builder(context);
        } else {
            nativeBuilder = null;
            alertDialog = XposedHelpers.newInstance(ClassLoad.getClass(ClassNames.ALERT_DIALOG_BUILDER), context);
        }
    }

    public Dialog getAlertDialog() {
        if (nativeBuilder != null) return nativeDialog;
        return (Dialog) XposedHelpers.getObjectField(alertDialog, Obfuscate.getFieldName("AlertDialog$Builder", "alertDialog"));
    }

    public void setTitle(CharSequence title) {
        if (nativeBuilder != null) { nativeBuilder.setTitle(title); return; }
        XposedHelpers.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "setTitle"), title);
    }

    public void setView(View view) {
        if (nativeBuilder != null) { nativeBuilder.setView(view); return; }
        XposedHelpers.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "setView"), view);
    }

    public void setMessage(CharSequence message) {
        if (nativeBuilder != null) { nativeBuilder.setMessage(message); return; }
        XposedHelpers.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "setMessage"), message);
    }

    public void setPositiveButton(CharSequence text, Object obj) {
        if (nativeBuilder != null) { nativeBuilder.setPositiveButton(text, (DialogInterface.OnClickListener) obj); return; }
        XposedHelpers.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "setPositiveButton"),
                text, obj
        );
    }

    public void setNegativeButton(CharSequence text, Object obj) {
        if (nativeBuilder != null) { nativeBuilder.setNegativeButton(text, (DialogInterface.OnClickListener) obj); return; }
        XposedHelpers.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "setNegativeButton"),
                text, obj
        );
    }

    public void setNeutralButton(CharSequence text, Object obj) {
        if (nativeBuilder != null) { nativeBuilder.setNeutralButton(text, (DialogInterface.OnClickListener) obj); return; }
        XposedHelpers.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "setNeutralButton"),
                text, obj
        );
    }

    public void show() {
        if (nativeBuilder != null) { nativeDialog = nativeBuilder.show(); return; }
        XposedHelpers.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "show"));
    }

    public Dialog create() {
        if (nativeBuilder != null) return nativeDialog = nativeBuilder.create();
        return (Dialog) XposedHelpers.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "create"));
    }

    public Runnable getDismissRunnable() {
        if (nativeBuilder != null) return () -> { if (nativeDialog != null) nativeDialog.dismiss(); };
        return (Runnable) XposedHelpers.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "getDismissRunnable"));
    }

}
