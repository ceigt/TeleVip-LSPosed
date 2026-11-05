package com.my.televip.virtuals.ui;

import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.ActionBar.ActionBarMenuItem;

import com.my.televip.compat.XposedHelpers;

public class ProfileActivity {

    Object profileActivity;

    public ProfileActivity(Object obj){
        profileActivity = obj;
    }

    public long getChatId(){
        return XposedHelpers.getLongField(profileActivity, Obfuscate.getFieldName("ProfileActivity", "chatId"));
    }

    public long getUserId(){
        return XposedHelpers.getLongField(profileActivity, Obfuscate.getFieldName("ProfileActivity", "userId"));
    }

    public Object[] getNameTextView(){
        String name = com.my.televip.obfuscate.RuntimeMappings.active().resolveField("ProfileActivity", "nameTextViewSingle");
        if (name != null) return new Object[]{null, XposedHelpers.getObjectField(profileActivity, name)};
        return (Object[]) XposedHelpers.getObjectField(profileActivity, Obfuscate.getFieldName("ProfileActivity", "nameTextView"));
    }

    public Object[] getOnlineTextView(){
        String status = com.my.televip.obfuscate.RuntimeMappings.active().resolveField("ProfileActivity", "statusTextView");
        if (status != null) return new Object[]{null, XposedHelpers.getObjectField(profileActivity, status)};
        return  (Object[]) XposedHelpers.getObjectField(profileActivity, Obfuscate.getFieldName("ProfileActivity", "onlineTextView"));
    }

    public ActionBarMenuItem getOtherItem(){
        return new ActionBarMenuItem(XposedHelpers.getObjectField(profileActivity, Obfuscate.getFieldName("ProfileActivity", "otherItem")));
    }


    public BaseFragment getBaseFragment(){
        return new BaseFragment(profileActivity);
    }
}
