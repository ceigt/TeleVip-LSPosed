package com.my.televip.features.ghostMode;

import java.util.ArrayDeque;

/** A display-only scope; network and persistence code never changes its phone value. */
public final class PhoneDisplayMask {
    private final ThreadLocal<ArrayDeque<Frame>> frames = new ThreadLocal<>();
    private static final class Frame {
        final Object token;
        final String phone;
        Frame(Object token, String phone) { this.token = token; this.phone = digits(phone); }
    }

    public void enter(Object token, String phone) {
        ArrayDeque<Frame> stack = frames.get();
        if (stack == null) { stack = new ArrayDeque<>(); frames.set(stack); }
        stack.push(new Frame(token, phone));
    }

    public void exit(Object token) {
        ArrayDeque<Frame> stack = frames.get();
        if (stack == null) return;
        if (stack.isEmpty() || stack.peek().token != token) { frames.remove(); return; }
        stack.pop();
        if (stack.isEmpty()) frames.remove();
    }

    /** Null means preserve Telegram's formatter result. */
    public String replacement(String input) {
        ArrayDeque<Frame> stack = frames.get();
        if (stack == null || stack.isEmpty() || stack.peek().phone == null) return null;
        return stack.peek().phone.equals(digits(input)) ? "••••••" : null;
    }

    private static String digits(String value) {
        if (value == null) return null;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') result.append(c);
            else if (c != '+' && c != '-' && c != '(' && c != ')' && !Character.isWhitespace(c)) return null;
        }
        return result.length() == 0 ? null : result.toString();
    }
}
