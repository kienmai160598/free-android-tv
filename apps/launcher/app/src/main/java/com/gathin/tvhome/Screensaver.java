package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

/**
 * The screensaver's choices in Launcher settings: when it starts, which is the system's screen_off_timeout
 * (what Android TV's own "When to start" writes, and where Xiaomi's firmware has no setting), and how
 * ClockDream looks.
 */
final class Screensaver {
    private Screensaver() { }

    static final String STYLE_KEY = "dream_style";
    /** Home's M3E shapes drifting behind the clock, a colour glow, the clock alone and dim, a bigger clock. */
    static final String SHAPES = "shapes", GLOW = "glow", MINIMAL = "minimal", LARGE = "large";
    static final String[] STYLES = {SHAPES, GLOW, MINIMAL, LARGE};
    /** Choices for screen_off_timeout, in minutes; 0 is never. */
    static final int[] MINUTES = {0, 5, 10, 15, 30};
    private static final int NEVER_MS = Integer.MAX_VALUE;
    private static final int MINUTE_MS = 60_000;

    static String style(SharedPreferences choices) {
        String chosen = choices.getString(STYLE_KEY, SHAPES);
        for (String known : STYLES) if (known.equals(chosen)) return known;
        return SHAPES;
    }

    /** Minutes before the screensaver starts, 0 for never; at least 1 for any shorter timeout. */
    static int minutes(Context ctx) {
        int ms = Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, NEVER_MS);
        if (ms >= NEVER_MS / 2) return 0;
        return Math.max(1, Math.round(ms / (float) MINUTE_MS));
    }

    /** Writes screen_off_timeout; false without WRITE_SECURE_SETTINGS, which tvctl install grants. */
    static boolean setMinutes(Context ctx, int minutes) {
        try {
            return Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT,
                    minutes == 0 ? NEVER_MS : minutes * MINUTE_MS);
        } catch (SecurityException e) {
            return false;
        }
    }
}
