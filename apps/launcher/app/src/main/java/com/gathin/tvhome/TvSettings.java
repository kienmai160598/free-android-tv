package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/**
 * The TV's own Settings. Xiaomi's China firmware ships AOSP's com.android.tv.settings beside its own
 * com.xiaomi.mitv.settings, and Settings.ACTION_SETTINGS resolves to AOSP's, which has no picture, sound or
 * source settings: the stock launcher opened Xiaomi's.
 */
final class TvSettings {
    private TvSettings() { }

    /** Vendor Settings to prefer over Settings.ACTION_SETTINGS, first found wins. */
    private static final String[] VENDOR_ACTIONS = {
            "com.xiaomi.mitv.settings.SETTINGS",
    };

    static Intent main(Context ctx) {
        for (String action : VENDOR_ACTIONS) {
            Intent i = new Intent(action);
            if (i.resolveActivity(ctx.getPackageManager()) != null) return i;
        }
        return new Intent(Settings.ACTION_SETTINGS);
    }

    /** Picture settings, or the TV's Settings where they have no entry of their own (Xiaomi: Image settings). */
    static Intent picture(Context ctx) {
        Intent i = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
        return i.resolveActivity(ctx.getPackageManager()) != null ? i : main(ctx);
    }
}
