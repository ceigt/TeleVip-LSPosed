package com.my.televip.virtuals.ui.Cells;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.obfuscate.Obfuscate;

import com.my.televip.compat.XposedHelpers;

public class TextCheckCell {

    public Object textCell;

    public TextCheckCell(Context context){
        textCell = XposedHelpers.newInstance(ClassLoad.getClass(ClassNames.TEXT_CHECK_CELL), context);
    }

    public TextCheckCell(Object obj){
        textCell = obj;
    }

    public void setTextAndValueAndCheck(CharSequence text, String value, boolean checked, boolean multiline, boolean divider){
        XposedHelpers.callMethod(textCell, Obfuscate.getMethodName("TextCheckCell","setTextAndValueAndCheck"), text, value, checked, multiline,  divider);
    }

    public void setTextAndCheck(CharSequence text, boolean checked, boolean divider){
        XposedHelpers.callMethod(textCell, Obfuscate.getMethodName("TextCheckCell","setTextAndCheck"), text, checked,  divider);
    }

    public void setChecked(boolean checked){
        XposedHelpers.callMethod(textCell, Obfuscate.getMethodName("TextCheckCell","setChecked"), checked);
    }

    public boolean isChecked(){
        return (boolean) XposedHelpers.callMethod(textCell, Obfuscate.getMethodName("TextCheckCell","isChecked"));
    }

    public TextView getTextView(){
        return (TextView) XposedHelpers.getObjectField(textCell, Obfuscate.getFieldName("TextCheckCell","textView"));
    }

    public View getView(){
        return (View) textCell;
    }

}
