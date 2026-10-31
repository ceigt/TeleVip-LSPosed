package com.my.televip.features.otherFeatures;

/** Telegram 13.1 moved visible profile status out of its formerly populated overlay. */
public final class ProfileStatusLine {
    private ProfileStatusLine() {}

    public static int index(int length, boolean primaryPresent, CharSequence overlayText) {
        return index(length, primaryPresent, overlayText, false, true);
    }

    public static int index(int length, boolean primaryPresent, CharSequence overlayText,
                            boolean primaryShown, boolean overlayShown) {
        if (length < 2) return -1;
        boolean populated = overlayText != null && !overlayText.toString().trim().isEmpty()
                && !overlayText.toString().trim().startsWith("ID: ");
        return length > 3 && (populated && !(primaryShown && !overlayShown) || !primaryPresent) ? 3 : 1;
    }

    public static boolean hasId(CharSequence text, String id) {
        return text != null && (text.toString().equals("ID: " + id) || text.toString().endsWith("\nID: " + id));
    }

    public static CharSequence withoutId(CharSequence text) {
        if (text == null) return "";
        String value = text.toString();
        int start = value.lastIndexOf("\nID: ");
        if (start >= 0 && value.substring(start + 5).matches("-?\\d+")) return text.subSequence(0, start);
        return value.matches("ID: -?\\d+") ? "" : text;
    }
}
