package com.my.televip.virtuals.ui.Cells;

import android.content.Context;
import android.view.View;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.obfuscate.Obfuscate;

import com.my.televip.compat.XposedHelpers;

public class HeaderCell {

    public Object headerCell;

    public HeaderCell(Context context){
        headerCell = XposedHelpers.newInstance(ClassLoad.getClass(ClassNames.HEADER_CELL), context);
    }

    public HeaderCell(Object obj){
       headerCell = obj;
    }

    public View getView(){
        return (View) headerCell;
    }

    public void setText(CharSequence text){
        XposedHelpers.callMethod(headerCell, Obfuscate.getMethodName("HeaderCell","setText"), text);
    }

}
