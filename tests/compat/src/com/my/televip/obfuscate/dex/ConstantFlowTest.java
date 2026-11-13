package com.my.televip.obfuscate.dex;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/** Minimal DEX code: an object result in v6 must preserve a constant in v7; a wide result must not. */
public final class ConstantFlowTest {
    private static Map<Integer,Long> writes(int... instructions){
        byte[] bytes=new byte[160+instructions.length*2];bytes[0]='d';bytes[1]='e';bytes[2]='x';bytes[3]='\n';
        ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);b.putInt(0x28,0x12345678);b.putInt(128+12,instructions.length);
        for(int i=0;i<instructions.length;i++)b.putShort(144+i*2,(short)instructions[i]);
        Map<Integer,Long> writes=new HashMap<>();
        CodeScanner.constantFlow(new DexFile("synthetic.dex",bytes),128,new CodeScanner.ConstantSink(){
            @Override public void instanceWrite(int field,long value){writes.put(field,value);}
        });return writes;
    }
    public static void main(String[] args){
        if(!Long.valueOf(Integer.MAX_VALUE).equals(writes(0x0714,0xffff,0x7fff,0x060c,0x0759,0,0x000e).get(0)))throw new AssertionError("object result erased adjacent int");
        if(writes(0x0714,0xffff,0x7fff,0x060b,0x0759,0,0x000e).containsKey(0))throw new AssertionError("wide result retained overwritten high register");
        if(!Long.valueOf(Integer.MAX_VALUE).equals(writes(0x0214,0xffff,0x7fff,0x0003,7,2,0x0759,0,0x000e).get(0)))throw new AssertionError("move/16 lost constant");
        System.out.println("CONSTANT_FLOW_PASS: object/wide result isolation and move/16");
    }
}
