package com.my.televip.virtuals.tgnet;
public final class NativeByteBuffer {
    public NativeByteBuffer(){}
    public NativeByteBuffer(int size){}
    public NativeByteBuffer(Object object){}
    public final Object nativeByteBuffer = this;
    public final java.nio.ByteBuffer buffer=java.nio.ByteBuffer.allocate(16);
    public boolean reused;
    public int flags;
    public TLRPC.MessageData previous;
    public void position(int position) {}
    public int readInt32(boolean exception) { return flags; }
    public void writeInt32(int value) { flags = value; }
    public void reuse() { reused = true; }
}
