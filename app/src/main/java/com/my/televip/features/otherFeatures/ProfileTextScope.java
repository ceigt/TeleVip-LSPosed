package com.my.televip.features.otherFeatures;

import java.util.ArrayDeque;

/** Per-call/per-thread rendering scope; never retain a fragment or view after the callback. */
public final class ProfileTextScope {
    public static final class Frame {
        public final Object token, status, copy, idView;
        public final String id, offline;
        public Frame(Object token, Object status, Object copy, Object idView, String id, String offline) {
            this.token = token; this.status = status; this.copy = copy;
            this.idView = idView; this.id = id; this.offline = offline;
        }
    }
    private final ThreadLocal<ArrayDeque<Frame>> frames = new ThreadLocal<>();
    public void enter(Frame frame) {
        ArrayDeque<Frame> stack = frames.get();
        if (stack == null) { stack = new ArrayDeque<>(); frames.set(stack); }
        stack.push(frame);
    }
    public Frame current() {
        ArrayDeque<Frame> stack = frames.get();
        return stack == null || stack.isEmpty() ? null : stack.peek();
    }
    public void exit(Object token) {
        ArrayDeque<Frame> stack = frames.get();
        if (stack == null) return;
        if (stack.isEmpty() || stack.peek().token != token) { frames.remove(); return; }
        stack.pop();
        if (stack.isEmpty()) frames.remove();
    }
    public CharSequence status(Object view, CharSequence text) {
        Frame frame = current();
        if (frame != null && frame.offline != null && (view == frame.status
                || view == frame.copy && text != null && text.length() > 0)) return frame.offline;
        return text;
    }
}
