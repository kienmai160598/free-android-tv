package com.gathin.tvhome;

import android.content.SharedPreferences;

/**
 * Home's look as chosen in Launcher settings: the scheme's seed colour (kept under background_color, over the
 * theme's, see Config) and what is drawn behind the apps. Pushing or resetting a theme clears both.
 */
final class HomeLook {
    private HomeLook() { }

    static final String BACKDROP_KEY = "backdrop";
    /** The M3 Expressive shapes, the time-of-day sky, or the scheme's surface alone. */
    static final String SHAPES = "shapes", SKY = "sky", PLAIN = "plain";
    static final String[] BACKDROPS = {SHAPES, SKY, PLAIN};
    /**
     * The sky's opacity when it is chosen here and the theme gives it none. Its gradient is a dark one, so a
     * light scheme gets a tint of it that keeps the dark header text readable.
     */
    private static final int SKY_INTENSITY_DARK = 70, SKY_INTENSITY_LIGHT = 30;

    /** Seeds offered in Launcher settings, in the order of R.array.look_seed_names: M3's baseline violet first. */
    static final int[] SEEDS = {0xFF6750A4, 0xFF0061A4, 0xFF006A6A, 0xFF386A20, 0xFF8B5000, 0xFF984061, 0xFFB3261E};

    static String backdrop(SharedPreferences choices) {
        String chosen = choices.getString(BACKDROP_KEY, SHAPES);
        for (String known : BACKDROPS) if (known.equals(chosen)) return known;
        return SHAPES;
    }

    /** sky_intensity for a backdrop: the theme's for the shapes, a visible sky for the sky, none for plain. */
    static int skyIntensity(String backdrop, int themeIntensity, boolean dark) {
        if (SKY.equals(backdrop)) {
            if (themeIntensity > 0) return themeIntensity;
            return dark ? SKY_INTENSITY_DARK : SKY_INTENSITY_LIGHT;
        }
        return PLAIN.equals(backdrop) ? 0 : themeIntensity;
    }
}
