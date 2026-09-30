package com.my.televip.virtuals.SQLite;
import com.my.televip.virtuals.tgnet.NativeByteBuffer;
public final class SQLiteCursor {
    public final NativeByteBuffer data = new NativeByteBuffer();
    public boolean disposed;
    private final boolean empty;
    private boolean visited;
    SQLiteCursor(boolean empty) { this.empty = empty; }
    public boolean next() { if (empty || visited) return false; visited = true; return true; }
    public NativeByteBuffer byteBufferValue(int column) { return data; }
    public int intValue(int column) { return 7; }
    public long longValue(int column) { return -100; }
    public void dispose() { disposed = true; }
}
