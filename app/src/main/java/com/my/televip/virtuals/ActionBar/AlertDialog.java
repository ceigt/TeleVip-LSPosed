package com.my.televip.virtuals.ActionBar;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import com.my.televip.logging.Logger;

/** System dialogs avoid renamed host builders and listener interfaces. */
public final class AlertDialog {
    @FunctionalInterface public interface OnClick {void onClick();}
    public static Object click(OnClick action) {
        return action == null ? null : safe((dialog, which) -> action.onClick());
    }
    private static DialogInterface.OnClickListener safe(DialogInterface.OnClickListener action) {
        if (action == null) return null;
        return (dialog, which) -> {
            try { action.onClick(dialog, which); }
            catch (Throwable error) { Logger.e(error); }
        };
    }
    private final android.app.AlertDialog.Builder builder;
    private Dialog dialog;
    public AlertDialog(Context context) {builder=new android.app.AlertDialog.Builder(context);}
    public Dialog getAlertDialog() {return dialog;}
    public void setTitle(CharSequence text) {builder.setTitle(text);}
    public void setView(View view) {builder.setView(view);}
    public void setMessage(CharSequence text) {builder.setMessage(text);}
    public void setPositiveButton(CharSequence text,Object action) {builder.setPositiveButton(text,safe((DialogInterface.OnClickListener)action));}
    public void setNegativeButton(CharSequence text,Object action) {builder.setNegativeButton(text,safe((DialogInterface.OnClickListener)action));}
    public void setNeutralButton(CharSequence text,Object action) {builder.setNeutralButton(text,safe((DialogInterface.OnClickListener)action));}
    public void show() {dialog=builder.show();}
    public Dialog create() {return dialog=builder.create();}
    public Runnable getDismissRunnable() {return ()->{if(dialog!=null)dialog.dismiss();};}
}
