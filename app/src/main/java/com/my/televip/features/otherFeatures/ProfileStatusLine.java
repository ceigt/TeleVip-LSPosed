package com.my.televip.features.otherFeatures;

/** Telegram 13.1 moved visible profile status out of its formerly populated overlay. */
public final class ProfileStatusLine {
    private ProfileStatusLine() {}

    public static int index(int length, boolean primaryPresent, CharSequence overlayText) {
        if (length < 2) return -1;
        boolean populated = overlayText != null && !overlayText.toString().trim().isEmpty()
                && !overlayText.toString().trim().startsWith("ID: ");
        return length > 3 && (populated || !primaryPresent) ? 3 : 1;
    }

    public static boolean hasId(CharSequence text, String id) {
        return text != null && text.toString().endsWith("\nID: " + id);
    }
}
