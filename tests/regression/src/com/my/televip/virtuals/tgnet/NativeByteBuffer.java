package com.my.televip.virtuals.tgnet;
public final class NativeByteBuffer {
    public final Object nativeByteBuffer = this;
    public boolean reused;
    public int flags;
    public void position(int position) {}
    public int readInt32(boolean exception) { return flags; }
    public void writeInt32(int value) { flags = value; }
    public void reuse() { reused = true; }
}
