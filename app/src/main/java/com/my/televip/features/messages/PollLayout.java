package com.my.televip.features.messages;

import android.view.View;
import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.compat.XposedHelpers;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.logging.Logger;

/** Poll metadata has a dedicated last row, including timed and long-author polls. */
public final class PollLayout {
    private static boolean installed;
    private PollLayout() {}
    public static int addedHeight(boolean alreadySeparate,int lineHeight){return alreadySeparate?0:Math.max(0,lineHeight);}
    public static synchronized void init(){
        if(installed)return;
        try {
            installed=HMethod.hookMethod(ClassLoad.getClass("org.telegram.ui.Cells.ChatMessagePoll"),Obfuscate.getMethodName("ChatMessagePoll","setMessageContentIfPoll"),ClassLoad.getClass(ClassNames.MESSAGE_OBJECT),boolean.class,new BaseMethodHook(){
                @Override protected void afterMethod(MethodHookParam p){
                    if(p.hasThrowable())return;
                    Object cell=XposedHelpers.getObjectField(p.thisObject,Obfuscate.getFieldName("ChatMessagePoll","cell"));
                    String flag=Obfuscate.getFieldName("ChatMessageCell","instantTextNewLine");
                    boolean separated=(boolean)XposedHelpers.getObjectField(cell,flag);
                    float density=((View)cell).getResources().getDisplayMetrics().density;
                    android.text.TextPaint paint=com.my.televip.virtuals.ActionBar.Theme.getTextPaint();
                    int gap=addedHeight(separated,(int)Math.ceil(Math.max(18*density,paint==null?0:paint.getFontSpacing()+4*density)));
                    if(gap==0)return;
                    String height=Obfuscate.getFieldName("ChatMessageCell","totalHeight");
                    XposedHelpers.setIntField(cell,height,XposedHelpers.getIntField(cell,height)+gap);
                    XposedHelpers.setBooleanField(cell,flag,true);
                }
            });
        }catch(Throwable error){Logger.e(error);}
    }
}
