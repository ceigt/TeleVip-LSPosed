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
        return  (Object[]) XposedHelpers.getObjectField(profileActivity, Obfuscate.getFieldName("ProfileActivity", "nameTextView"));
    }

    public Object[] getOnlineTextView(){
        return  (Object[]) XposedHelpers.getObjectField(profileActivity, Obfuscate.getFieldName("ProfileActivity", "onlineTextView"));
    }

    public ActionBarMenuItem getOtherItem(){
        return new ActionBarMenuItem(XposedHelpers.getObjectField(profileActivity, Obfuscate.getFieldName("ProfileActivity", "otherItem")));
    }


    public BaseFragment getBaseFragment(){
        return new BaseFragment(profileActivity);
    }
}
