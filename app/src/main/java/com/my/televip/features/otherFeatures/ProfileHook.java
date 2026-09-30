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

import com.my.televip.compat.XposedHelpers;

public class ProfileHook {

    private static boolean initialized = false;
    private static int attempts;
    private static String lastClass;

    public static synchronized boolean isInitialized() { return initialized; }

    public static synchronized void init(String className) {
        if (initialized || className == null) return;
        if (!className.equals(lastClass)) { lastClass = className; attempts = 0; }
        if (attempts >= 3) return;
        attempts++;

        Class<?> clazz = ClassLoad.getClass(className);
        if (clazz == null) { FeatureStateManager.resetProfile(); return; }

        try (HookInstallation attempt = HookInstallation.begin()) {

        HMethod.hookMethod(ClassLoad.getClass(ClassNames.PROFILE_ACTIVITY), ClientManager.is(ClientManager.Client.Telegram) ? "createView" : Obfuscate.getMethodName("ProfileActivity", "createActionBarMenu"), ArgsResolver.merge("createActionBarMenu", ClientManager.is(ClientManager.Client.Telegram) ? new Class[]{Context.class} : new Class[]{boolean.class}, new BaseMethodHook() {
            @Override
            protected void afterMethod(MethodHookParam param) {
                ProfileActivity profileActivity = new ProfileActivity(param.thisObject);
                if (getUserID(profileActivity) > 1) {

                    ActionBarMenuItem otherItem = profileActivity.getOtherItem();

                    if (otherItem.getActionBarMenuItem() != null) {

                        int drawableResource = 0x7f0806d3;

                        if (!ClientManager.is(ClientManager.Client.Nagram) && !ClientManager.is(ClientManager.Client.Momogram)) {
                            drawableResource = XposedHelpers.getStaticIntField(ClassLoad.getClass(ClassNames.DRAWABLE), "msg_filled_menu_users");
                        }

                        otherItem.addSubItem(8353847, drawableResource, Translator.get(Keys.ApproximateCreationDate));
                    }
                }
            }
        }));

        HMethod.hookMethod(clazz, ClientManager.is(ClientManager.Client.Telegram) ? "b" : "onItemClick", int.class, new BaseMethodHook() {
            @Override
            protected void afterMethod(MethodHookParam param) {

                int id = (int) param.args[0];

                if (id == 8353847) {
                    final Object thisClass = XposedHelpers.getObjectField(param.thisObject, Obfuscate.getFieldName("ProfileActivity", "this$0"));
                    ProfileActivity profile = new ProfileActivity(thisClass);

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
            }
        });
        initialized = attempt.isComplete();
        if (initialized) FeatureStateManager.saveProfile(className);
        else FeatureStateManager.resetProfile();
        } catch (Throwable error) {
            FeatureStateManager.resetProfile();
            Logger.e(error);
        }
    }

    private static long getUserID(ProfileActivity profile) {
        if (profile.getUserId() > 1) {
            return profile.getUserId();
        }
        return 0;
    }

}
