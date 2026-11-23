package com.my.televip.features.telegram;

public final class ApiIdentity {
    private ApiIdentity() {}
    public static boolean valid(String id, String hash) {
        try {
            return id != null && id.matches("[0-9]{1,10}") && Integer.parseInt(id) > 0
                    && hash != null && hash.matches("[0-9a-fA-F]{32}");
        } catch (NumberFormatException invalid) { return false; }
    }
}
