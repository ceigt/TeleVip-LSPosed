package com.my.televip.features.connections;

public final class DownloadTuning {
    private static final int MIB = 1024 * 1024;
    static final int NATIVE_SLOTS = 2;
    static final int ROTATION_SLOTS = 4;

    private DownloadTuning() {}

    public static boolean widenConnections(int connectionType, boolean nativeBudget, boolean forceSmall,
            boolean story, long bytes, int slots, int chunk, int requests) {
        return connectionType == -1 && nativeBudget && !forceSmall && !story && bytes >= 32L * MIB
                && slots == NATIVE_SLOTS && (chunk == MIB / 8 || chunk == MIB / 2)
                && (long) chunk * requests == 2L * MIB;
    }
}
