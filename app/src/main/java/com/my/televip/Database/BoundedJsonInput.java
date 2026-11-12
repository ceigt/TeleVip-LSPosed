package com.my.televip.Database;
import java.io.*;
/** Bound raw JSON tokens before JsonReader can allocate their decoded strings. */
public final class BoundedJsonInput extends FilterReader {
    private final int limit;private boolean quoted,escaped;private int length;
    public BoundedJsonInput(Reader reader,int limit){super(reader);this.limit=limit;}
    private void accept(int c)throws IOException{
        if(quoted){
            if(!escaped&&c=='"'){quoted=false;length=0;return;}
            if(++length>limit)throw new IOException("JSON string too large");
            if(escaped)escaped=false;else if(c=='\\')escaped=true;
        }else if(c=='"'){quoted=true;length=0;}
        else if(Character.isWhitespace(c)||"{}[],:".indexOf(c)>=0)length=0;
        else if(++length>128)throw new IOException("JSON literal too large");
    }
    @Override public int read()throws IOException{int c=in.read();if(c>=0)accept(c);return c;}
    @Override public int read(char[] chars,int offset,int count)throws IOException{
        int n=in.read(chars,offset,count);for(int i=0;i<n;i++)accept(chars[offset+i]);return n;
    }
}
