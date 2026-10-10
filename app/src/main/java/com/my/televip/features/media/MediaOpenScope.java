package com.my.televip.features.media;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Temporary host-field changes belong to one invocation, including nested openings. */
public final class MediaOpenScope {
    private final ThreadLocal<ArrayDeque<Frame>> frames = new ThreadLocal<>();
    private static final class Frame {
        final Object token, message;
        final int original;
        final IntConsumer setter;
        Frame(Object token, Object message, int original, IntConsumer setter) {
            this.token = token; this.message = message; this.original = original; this.setter = setter;
        }
    }
    public void enter(Object token, Object message, IntSupplier getter, IntConsumer setter) {
        int original = getter.getAsInt();
        ArrayDeque<Frame> stack = frames.get();
        if (stack == null) { stack = new ArrayDeque<>(); frames.set(stack); }
        // Record before changing the field so an exception still has a matching restoration.
        stack.push(new Frame(token, message, original, setter));
        if (original > 0) setter.accept(0);
    }
    public boolean contains(Object message) {
        ArrayDeque<Frame> stack = frames.get();
        if (stack != null) for (Frame frame : stack) if (frame.message == message) return true;
        return false;
    }
    public boolean isActive() { ArrayDeque<Frame> stack = frames.get(); return stack != null && !stack.isEmpty(); }
    public void exit(Object token) {
        ArrayDeque<Frame> stack = frames.get();
        if (stack == null) return;
        Frame found = null;
        for (Iterator<Frame> it = stack.iterator(); it.hasNext();) {
            Frame frame = it.next();
            if (frame.token == token) { found = frame; it.remove(); break; }
        }
        if (stack.isEmpty()) frames.remove();
        if (found != null) found.setter.accept(found.original);
    }
}
