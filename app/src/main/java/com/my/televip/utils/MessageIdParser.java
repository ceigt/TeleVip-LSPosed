package com.my.televip.utils;

public final class MessageIdParser {
    private MessageIdParser() {}

    /** Telegram message IDs must be positive signed 32-bit integers. */
    public static Integer parse(String text) {
        if (text == null) return null;
        String value = text.trim();
        if (value.isEmpty() || value.length() > 10) return null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') return null;
        }
        try {
            int id = Integer.parseInt(value);
            return id > 0 ? id : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
