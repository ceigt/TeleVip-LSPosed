package com.my.televip.virtuals.SQLite;
import com.my.televip.virtuals.tgnet.NativeByteBuffer;
public final class SQLitePreparedStatement {
    public int steps;
    public boolean disposed;
    private final boolean fail;
    SQLitePreparedStatement(boolean fail) { this.fail = fail; }
    public void requery() {}
    public void bindByteBuffer(int index, NativeByteBuffer value) {}
    public void bindLong(int index, long value) {}
    public void bindInteger(int index, int value) {}
    public void step() { steps++; if (fail) throw new IllegalStateException("Injected native step failure"); }
    public void dispose() { disposed = true; }
}
