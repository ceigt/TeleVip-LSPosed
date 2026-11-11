package com.my.televip.features.messages;
import java.util.*;
import com.my.televip.Class.*;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.*;
import com.my.televip.obfuscate.Obfuscate;

/** Formatter changes exist only while Telegram builds a message time, before native measurement. */
public final class SecondsDisplay {
    private static boolean installed;
    private static final ThreadLocal<ArrayDeque<Boolean>> scope=new ThreadLocal<>();
    private SecondsDisplay(){}
    public static synchronized void init(){
        if(installed)return;
        try(HookInstallation attempt=HookInstallation.begin()){
            HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_MESSAGE_CELL),Obfuscate.getMethodName("ChatMessageCell","timeString"),ClassLoad.getClass(ClassNames.MESSAGE_OBJECT),ClassLoad.getClass(ClassNames.MESSAGE_OBJECT),boolean.class,int.class,new BaseMethodHook(){
                @Override protected void beforeMethod(MethodHookParam p){ArrayDeque<Boolean> stack=scope.get();if(stack==null){stack=new ArrayDeque<>();scope.set(stack);}stack.push(ConfigManager.showSeconds!=null&&ConfigManager.showSeconds.isEnable());}
                @Override protected void afterMethod(MethodHookParam p){ArrayDeque<Boolean> stack=scope.get();if(stack!=null&&!stack.isEmpty())stack.pop();if(stack==null||stack.isEmpty())scope.remove();}
            });
            BaseMethodHook formatter=new BaseMethodHook(){
                @Override protected void afterMethod(MethodHookParam p){
                    ArrayDeque<Boolean> stack=scope.get();if(stack==null||stack.isEmpty())return;
                    if(!stack.peek()||p.hasThrowable()||!(p.getResult() instanceof String))return;
                    long millis=p.args[0] instanceof Date?((Date)p.args[0]).getTime():((Number)p.args[0]).longValue();
                    p.setResult(TimeSeconds.add((String)p.getResult(),millis));
                }
            };
            HMethod.hookMethod(ClassLoad.getClass(ClassNames.FAST_DATE_FORMAT),Obfuscate.getMethodName("FastDateFormat","formatJ"),long.class,formatter);
            HMethod.hookMethod(ClassLoad.getClass(ClassNames.FAST_DATE_FORMAT),Obfuscate.getMethodName("FastDateFormat","formatD"),java.util.Date.class,formatter);
            installed=attempt.isComplete();
        }
    }
}
