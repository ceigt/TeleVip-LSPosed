package com.my.televip.settings;

import android.app.Activity;
import com.my.televip.Class.ClassLoad;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.compat.XposedBridge;
import com.my.televip.compat.XposedHelpers;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.utils.Utils;
import java.lang.reflect.*;
import java.util.ArrayList;

/** Native settings row resolved from this build, with a callback fallback for inlined methods. */
public final class NativeSettingsEntry {
    private static boolean installed;
    private NativeSettingsEntry() {}
    public static synchronized boolean init() {
        if (installed) return true;
        try (HookInstallation attempt = HookInstallation.begin()) {
            Class<?> settings = ClassLoad.getClass("org.telegram.ui.SettingsActivity");
            Class<?> factory = ClassLoad.getClass("org.telegram.ui.SettingsActivity$SettingCell$Factory");
            Class<?> row = ClassLoad.getClass("org.telegram.ui.Components.UItem");
            if (!attempt.require(settings) || !attempt.require(factory) || !attempt.require(row)) return false;
            final Method create = rowFactory(factory, row);
            Method fill = uniqueMethod(settings, Obfuscate.getMethodName("SettingsActivity", "fillItems"), ArrayList.class);
            Method click = uniqueMethod(settings, Obfuscate.getMethodName("SettingsActivity", "onClick"), row);
            if (fill != null && click != null) {
                HMethod.hookMethod(fill, new BaseMethodHook() {
                    @Override protected void afterMethod(MethodHookParam param) {
                        ArrayList<?> items = argument(param.args, ArrayList.class);
                        if (items != null) addRow(items, create);
                    }
                });
                HMethod.hookMethod(click, new BaseMethodHook() {
                    @Override protected void beforeMethod(MethodHookParam param) {
                        Object item = argument(param.args, row);
                        if (item != null && id(item) == SettingsFallback.ROW_ID) {open();param.setResult(null);}
                    }
                });
            } else wrapCallbacks(settings, row, create);
            installed = attempt.isComplete();
            if (installed) Logger.l("Native TeleVip settings entry installed");
        } catch (Throwable error) { Logger.e(error); }
        return installed;
    }
    private static Method rowFactory(Class<?> factory, Class<?> row) throws NoSuchMethodException {
        for (int count : new int[]{7,6}) {
            Method found = null;
            String name = Obfuscate.getMethodName("SettingsActivity$SettingCell$Factory", count == 7 ? "ofIIIICCC" : "of");
            for (Method m : factory.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (!m.getName().equals(name) || !Modifier.isStatic(m.getModifiers())
                        || !row.isAssignableFrom(m.getReturnType()) || p.length != count) continue;
                boolean fits = true;
                for (int i=0;i<count;i++) fits &= i<4 ? p[i]==int.class : p[i]==CharSequence.class || p[i]==String.class;
                if (!fits) continue;
                if (found != null) throw new NoSuchMethodException("Ambiguous settings row factory");
                found = m;
            }
            if (found != null) {found.setAccessible(true);return found;}
        }
        throw new NoSuchMethodException("Native settings row factory unavailable");
    }
    public static Method uniqueMethod(Class<?> cls,String name,Class<?> argument) {
        if(cls==null || argument==null) return null;
        Method found = null;
        for (Method method : cls.getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getReturnType()!=void.class) continue;
            boolean contains=false;
            for (Class<?> type : method.getParameterTypes()) contains |= type==argument;
            if (!contains) continue;
            if (found!=null) return null;
            found=method;
        }
        return found;
    }
    @SuppressWarnings("unchecked")
    private static void addRow(ArrayList<?> source,Method create) {
        try {
            ArrayList<Object> items=(ArrayList<Object>)source;
            int position=items.size();
            for(int i=0;i<items.size();i++) {
                int value=id(items.get(i));
                if(value==SettingsFallback.ROW_ID) return;
                if(value==10) position=i+1;
            }
            Activity activity=Utils.getCurrentActivity();
            if(activity==null) return;
            int icon=activity.getResources().getIdentifier("settings_features","drawable",Utils.pkgName);
            Object[] args=create.getParameterCount()==7
                    ? new Object[]{SettingsFallback.ROW_ID,-1007845,-1996271,icon,"TeleVip","TeleVip settings",null}
                    : new Object[]{SettingsFallback.ROW_ID,-1007845,-1996271,icon,"TeleVip","TeleVip settings"};
            items.add(position,create.invoke(null,args));
        } catch(Throwable error) {Logger.e(error);}
    }
    private static int id(Object item) {return XposedHelpers.getIntField(item,Obfuscate.getFieldName("UItem","id"));}
    private static void open() {
        Activity activity=Utils.getCurrentActivity();
        if(activity!=null && !activity.isFinishing()) SettingsFallback.showSettings(activity);
    }
    private static <T> T argument(Object[] args,Class<T> type) {
        if(args!=null) for(Object arg:args) if(type.isInstance(arg)) return type.cast(arg);
        return null;
    }
    private static void wrapCallbacks(Class<?> settings,Class<?> row,Method create) {
        Class<?> recycler=ClassLoad.getClass("org.telegram.ui.Components.UniversalRecyclerView");
        if(recycler==null) {HookInstallation.failure();return;}
        boolean found=false;
        for(Constructor<?> ctor:recycler.getDeclaredConstructors()) {
            Class<?>[] types=ctor.getParameterTypes();
            if(types.length!=4 || !types[0].isAssignableFrom(settings) || !types[1].isInterface() || !types[2].isInterface()) continue;
            XposedBridge.hookMethod(ctor,new BaseMethodHook() {
                @Override protected void beforeMethod(MethodHookParam param) {
                    if(!settings.isInstance(param.args[0])) return;
                    Object fill=param.args[1],click=param.args[2];
                    if(fill!=null) param.args[1]=wrap(types[1],fill,(method,args)-> {
                        Object result=method.invoke(fill,args);
                        ArrayList<?> items=argument(args,ArrayList.class);
                        if(items!=null) addRow(items,create);
                        return result;
                    });
                    if(click!=null) param.args[2]=wrap(types[2],click,(method,args)-> {
                        Object item=argument(args,row);
                        if(item!=null && id(item)==SettingsFallback.ROW_ID) {open();return null;}
                        return method.invoke(click,args);
                    });
                }
            });
            found=true;
        }
        if(!found) HookInstallation.failure();
    }
    private interface Call {Object invoke(Method method,Object[] args) throws Throwable;}
    private static Object wrap(Class<?> type,Object original,Call call) {
        return Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)-> {
            if(method.getDeclaringClass()==Object.class) {
                if(method.getName().equals("equals")) return args!=null && args.length==1 && proxy==args[0];
                if(method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                return "TeleVip settings callback";
            }
            try {return call.invoke(method,args);} catch(InvocationTargetException error) {throw error.getCause();}
        });
    }
}
