package com.my.televip.features.stories;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;

import com.my.televip.compat.XposedBridge;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class DisableStories {

    public static volatile boolean isEnable = false;

    public static synchronized void init() {
        try (HookInstallation attempt = HookInstallation.begin()) {
            if (!isEnable) {

                if (attempt.require(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER))) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER), "MessagesController", new String[]{"storiesEnabled", "storyEntitiesAllowed",}, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (ConfigManager.disableStories.isEnable()) param.setResult(false);
                        }
                    });

                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER), Obfuscate.getMethodName("MessagesController", "storyEntitiesAllowed2"), ArgsResolver.merge("storyEntitiesAllowed", new Class[]{ClassLoad.getClass(ClassNames.TLRPC_USER)}, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (ConfigManager.disableStories.isEnable()) param.setResult(false);
                        }
                    }));
                }

                if (attempt.require(ClassLoad.getClass(ClassNames.STORIES_CONTROLLER))) {
                    Class<?> controller = ClassLoad.getClass(ClassNames.STORIES_CONTROLLER);
                    Class<?> peerStories = ClassLoad.getClass("org.telegram.tgnet.tl.TL_stories$PeerStories");
                    if (attempt.require(peerStories)) {
                        Field visible = controller.getDeclaredField(Obfuscate.getFieldName("StoriesController", "dialogListStories"));
                        Field hidden = controller.getDeclaredField(Obfuscate.getFieldName("StoriesController", "hiddenListStories"));
                        StoryLists.validate(visible, hidden);
                        Constructor<?> constructor = controller.getDeclaredConstructor(int.class);
                        HMethod.hookMethod(constructor, new BaseMethodHook() {
                            @Override protected void afterMethod(MethodHookParam param) throws Throwable {
                                if (!param.hasThrowable()) {
                                    StoryLists.install(param.thisObject, visible, hidden, peerStories,
                                            () -> ConfigManager.disableStories.isEnable());
                                    Logger.l("Story presentation lists filtered");
                                }
                            }
                        });
                    }
                    XposedBridge.hookAllMethods(ClassLoad.getClass(ClassNames.STORIES_CONTROLLER), Obfuscate.getMethodName("StoriesController", "hasStories"), new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (ConfigManager.disableStories.isEnable())
                                param.setResult(false);
                        }
                    });
                }
                isEnable = attempt.isComplete();
            }
        } catch (Throwable t){
            Logger.e(t);
        }
    }

}
