package com.my.televip.settings;

import java.util.Locale;

/** Same matching for translated labels, descriptions and section names. */
public final class SettingsSearch {
    private SettingsSearch() {}
    public static boolean matches(String query, String... values) {
        StringBuilder text = new StringBuilder();
        for (String value : values) if (value != null) text.append(value).append(' ');
        String haystack = text.toString().toLowerCase(Locale.ROOT);
        for (String word : query.trim().toLowerCase(Locale.ROOT).split("\\s+"))
            if (!haystack.contains(word)) return false;
        return true;
    }
}
