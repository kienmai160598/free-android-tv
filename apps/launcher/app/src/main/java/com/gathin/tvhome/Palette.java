package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Looper;

import com.google.android.material.color.utilities.DynamicScheme;
import com.google.android.material.color.utilities.Hct;
import com.google.android.material.color.utilities.MaterialDynamicColors;
import com.google.android.material.color.utilities.QuantizerCelebi;
import com.google.android.material.color.utilities.SchemeTonalSpot;
import com.google.android.material.color.utilities.Score;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Material You dynamic colour for the launcher: a seed picked from the wallpaper the way Android picks
 * it (Celebi quantizer, then Score), expanded into a dark Tonal Spot scheme, Pixel's default variant.
 * Only the roles the launcher paints are resolved, once per wallpaper.
 */
@SuppressLint("RestrictedApi")
final class Palette {

    /** M3 baseline seed, for a plain background with no colour to take a seed from. */
    static final int BASELINE_SEED = 0xFF6750A4;
    private static final int SAMPLE_EDGE = 128;

    final int seed;
    /** Dark or light Tonal Spot scheme for the seed. */
    final boolean dark;
    /** The appearance new schemes are built in: the system's (dark unless the system says light). */
    private static boolean darkMode = true;
    final int primary, onPrimary, primaryContainer, onPrimaryContainer;
    final int secondary, secondaryContainer, onSecondaryContainer;
    final int tertiary, tertiaryContainer;
    final int surface, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest;
    final int onSurface, onSurfaceVariant, outlineVariant;
    /** TV focus roles (androidx.tv.material3): focused buttons and list items invert; cards get an outline border. */
    final int surfaceVariant, inverseSurface, inverseOnSurface, inversePrimary, outline;
    /** Something needs attention (the Wi-Fi pill when the signal is weak or the Internet is gone). */
    final int error;

    private static Palette current;
    private static final List<Runnable> listeners = new ArrayList<>();

    private Palette(int seed, boolean dark) {
        this.seed = seed;
        this.dark = dark;
        DynamicScheme s = new SchemeTonalSpot(Hct.fromInt(seed), dark, 0.0);
        MaterialDynamicColors c = new MaterialDynamicColors();
        primary = c.primary().getArgb(s);
        onPrimary = c.onPrimary().getArgb(s);
        primaryContainer = c.primaryContainer().getArgb(s);
        onPrimaryContainer = c.onPrimaryContainer().getArgb(s);
        secondary = c.secondary().getArgb(s);
        secondaryContainer = c.secondaryContainer().getArgb(s);
        onSecondaryContainer = c.onSecondaryContainer().getArgb(s);
        tertiary = c.tertiary().getArgb(s);
        tertiaryContainer = c.tertiaryContainer().getArgb(s);
        surface = c.surface().getArgb(s);
        surfaceContainer = c.surfaceContainer().getArgb(s);
        surfaceContainerHigh = c.surfaceContainerHigh().getArgb(s);
        surfaceContainerHighest = c.surfaceContainerHighest().getArgb(s);
        onSurface = c.onSurface().getArgb(s);
        onSurfaceVariant = c.onSurfaceVariant().getArgb(s);
        surfaceVariant = c.surfaceVariant().getArgb(s);
        inverseSurface = c.inverseSurface().getArgb(s);
        inverseOnSurface = c.inverseOnSurface().getArgb(s);
        inversePrimary = c.inversePrimary().getArgb(s);
        outline = c.outline().getArgb(s);
        outlineVariant = c.outlineVariant().getArgb(s);
        error = c.error().getArgb(s);
    }

    static Palette fromSeed(int seed) {
        return new Palette(seed, darkMode);
    }

    /** A seed from a plain background colour: itself when it has some colour, the baseline when it is grey. */
    static Palette fromBackground(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        return new Palette(hsv[1] > 0.15f && hsv[2] > 0.1f ? color | 0xFF000000 : BASELINE_SEED, darkMode);
    }

    /** Off the main thread: the scheme for a wallpaper, from a small copy of it. */
    static Palette fromWallpaper(Bitmap wallpaper) {
        int w = wallpaper.getWidth(), h = wallpaper.getHeight();
        float scale = Math.min(1f, SAMPLE_EDGE / (float) Math.max(w, h));
        int sw = Math.max(1, Math.round(w * scale)), sh = Math.max(1, Math.round(h * scale));
        Bitmap small = Bitmap.createScaledBitmap(wallpaper, sw, sh, true);
        int[] pixels = new int[sw * sh];
        small.getPixels(pixels, 0, sw, 0, 0, sw, sh);
        if (small != wallpaper) small.recycle();
        Map<Integer, Integer> quantized = QuantizerCelebi.quantize(pixels, 128);
        List<Integer> ranked = Score.score(quantized, 1, BASELINE_SEED);
        return new Palette(ranked.isEmpty() ? BASELINE_SEED : ranked.get(0), darkMode);
    }

    static Palette current() {
        return current;
    }

    /** The scheme on screen, or the baseline one before any was published (another screen opened first). */
    static Palette currentOrBaseline() {
        return current != null ? current : fromSeed(BASELINE_SEED);
    }

    static boolean darkMode() {
        return darkMode;
    }

    /** Switches the appearance and republishes the current seed in it, so every painted view follows. */
    static void setDarkMode(boolean dark) {
        darkMode = dark;
        if (current != null && current.dark != dark) publish(new Palette(current.seed, dark));
    }

    /**
     * Follows the system's appearance (call before the first scheme is built, and again when the
     * configuration changes). An undefined night mode counts as dark, as Google TV is.
     */
    static void loadDarkMode(android.content.Context context) {
        int night = context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        setDarkMode(night != android.content.res.Configuration.UI_MODE_NIGHT_NO);
    }

    /** The same seed in the dark scheme, for surfaces that must stay dark (the screensaver). */
    static Palette dark(Palette p) {
        return p.dark ? p : new Palette(p.seed, true);
    }

    /** On the main thread: make this the scheme on screen and tell the painted views. */
    static void publish(Palette palette) {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("main thread only");
        if (palette == null || (current != null && current.seed == palette.seed && current.dark == palette.dark)) return;
        current = palette;
        for (Runnable listener : new ArrayList<>(listeners)) listener.run();
    }

    static void addListener(Runnable listener) {
        listeners.add(listener);
    }

    static void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    /** The colour with a new alpha, 0 to 255. */
    static int alpha(int color, int alpha) {
        return (alpha << 24) | (color & 0xFFFFFF);
    }
}
