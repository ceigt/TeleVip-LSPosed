package com.my.televip.messages;

import com.my.televip.compat.XposedHelpers;
import java.util.List;

/** Clears only a known deleted message, never another pin or a server setting. */
public final class PinnedReferences {
    private PinnedReferences() {}
    public static void clearCached(Object full, String field, List<Integer> ids) {
        if (full != null && ids.contains(XposedHelpers.getIntField(full, field)))
            XposedHelpers.setIntField(full, field, 0);
    }
}
