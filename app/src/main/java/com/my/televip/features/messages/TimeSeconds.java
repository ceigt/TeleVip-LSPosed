package com.my.televip.features.messages;
import java.util.*;
import java.util.regex.*;

/** Extend the original formatter token; retain its locale, hour cycle, markers and surrounding text. */
public final class TimeSeconds {
    private static final Pattern MINUTES=Pattern.compile("(?<![\\p{N}:.])(\\p{Nd}{1,2}([:.])\\p{Nd}{2})(?![:.\\p{N}])");
    private TimeSeconds(){}
    public static String add(String original,long millis){
        if(original==null||original.isEmpty())return original;
        Matcher m=MINUTES.matcher(original);if(!m.find())return original;
        int second=(int)Math.floorMod(Math.floorDiv(millis,1000),60);
        int digit=m.group(1).codePointAt(0);int zero=digit-Character.digit(digit,10);
        String seconds=new String(Character.toChars(zero+second/10))+new String(Character.toChars(zero+second%10));
        return original.substring(0,m.end())+m.group(2)+seconds+original.substring(m.end());
    }
}
