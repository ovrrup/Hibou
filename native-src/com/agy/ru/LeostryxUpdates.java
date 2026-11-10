package com.agy.ru;

import android.content.Context;

/** Compatibility delegation wrapper forwarding to HibouUpdates. */
public final class LeostryxUpdates {
    public static String getAppInfo(Context ctx) {
        return HibouUpdates.getAppInfo(ctx);
    }
    public static String installUpdate(Context ctx) {
        return HibouUpdates.installUpdate(ctx);
    }
}
