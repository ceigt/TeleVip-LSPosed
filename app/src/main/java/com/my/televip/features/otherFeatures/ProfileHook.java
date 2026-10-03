package com.my.televip.features.otherFeatures;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import android.content.Context;
import com.my.televip.Clients.ClientManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.logging.Logger;
import com.my.televip.language.Keys;
import com.my.televip.language.Translator;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.utils.IdDateEstimator;
import com.my.televip.utils.Utils;
import com.my.televip.virtuals.ActionBar.ActionBarMenuItem;
import com.my.televip.virtuals.ActionBar.AlertDialog;
import com.my.televip.virtuals.ui.ProfileActivity;


public class ProfileHook {

    private static boolean initialized = false;
    private static int attempts;
    private static String lastClass;

    public static synchronized boolean isInitialized() { return initialized; }

    public static synchronized void init(String className) {
        if (initialized) return;
        if (!String.valueOf(className).equals(lastClass)) { lastClass = className; attempts = 0; }
        if (attempts >= 3) return;
        attempts++;

        Class<?> clazz = ClassLoad.getClass(ClassNames.PROFILE_ACTIVITY);
        if (clazz == null) { FeatureStateManager.resetProfile(); return; }

        try (HookInstallation attempt = HookInstallation.begin()) {

            HMethod.hookMethod(clazz, Obfuscate.getMethodName("ProfileActivity", "createView"),
                    ArgsResolver.merge("createView", new Class[]{Context.class}, new BaseMethodHook() {
                        @Override protected void afterMethod(MethodHookParam param) {
                            MenuClicks.attach(param.thisObject, ProfileHook::onClick);
                        }
                    }));
            HMethod.hookMethod(clazz, Obfuscate.getMethodName("ProfileActivity", "createActionBarMenu"),
                    ArgsResolver.merge("createActionBarMenu", new Class[]{boolean.class}, new BaseMethodHook() {
                        @Override protected void afterMethod(MethodHookParam param) {
                            try {
                                ProfileActivity profile = new ProfileActivity(param.thisObject);
                                MenuClicks.attach(param.thisObject, ProfileHook::onClick);
                                if (getUserID(profile) <= 1) return;
                                ActionBarMenuItem menu = profile.getOtherItem();
                                if (menu.getActionBarMenuItem() == null || Utils.getCurrentActivity() == null) return;
                                int icon = Utils.getCurrentActivity().getResources().getIdentifier(
                                        "msg_filled_menu_users", "drawable", Utils.pkgName);
                                menu.addSubItem(8353847, icon, Translator.get(Keys.ApproximateCreationDate));
                            } catch (Throwable error) {Logger.e(error);}
                        }
                    }));

        initialized = attempt.isComplete();
        FeatureStateManager.resetProfile();
        } catch (Throwable error) {
            FeatureStateManager.resetProfile();
            Logger.e(error);
        }
    }

    private static void onClick(Object fragment,int id) {
        try {
            if (id == 8353847) {
                ProfileActivity profile = new ProfileActivity(fragment);

                AlertDialog alertDialog = new AlertDialog(Utils.getCurrentActivity());
                alertDialog.setTitle(Translator.get(Keys.TeleVip));
                alertDialog.setMessage("\n" +
                        Translator.get(Keys.ApproximateCreationDate) + " : " +
                                IdDateEstimator.getYearAndMethod(getUserID(profile)) + "\n\n" +
                                Translator.get(Keys.Age) + " : " +
                                IdDateEstimator.getAge(getUserID(profile)) + "\n\n" +
                                Translator.get(Keys.ApproximateCreationDateNotice)
                );
                alertDialog.setPositiveButton(Translator.get(Keys.Done), null);
                alertDialog.show();
            }
        } catch(Throwable error) { Logger.e(error); }
    }

    private static long getUserID(ProfileActivity profile) {
        if (profile.getUserId() > 1) {
            return profile.getUserId();
        }
        return 0;
    }

}
