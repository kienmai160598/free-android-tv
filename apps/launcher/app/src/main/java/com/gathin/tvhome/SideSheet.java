package com.gathin.tvhome;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

/**
 * The motion of a side sheet activity (launcher settings, running apps): the sheet slides in from the
 * right edge on the default spatial spring while the scrim behind fades in, and leaves on the fast one.
 * Its sections follow it in one after another (M3 Expressive's split and stagger), each a little later and
 * from a little further right, so the sheet arrives as a cascade instead of one slab.
 * Without animations it simply appears and goes.
 */
final class SideSheet {

    private final Activity activity;
    private final View scrim, sheet;
    private final Config config;
    private boolean closing;
    /** Sections that stagger in: the first few, the rest are below the fold. */
    private static final int STAGGERED = 8;
    private static final long STAGGER_MS = 35;
    private static final float STAGGER_DP = 56f;

    SideSheet(Activity activity, View scrim, View sheet, Config config) {
        this.activity = activity;
        this.scrim = scrim;
        this.sheet = sheet;
        this.config = config;
    }

    boolean closing() {
        return closing;
    }

    void slideIn() {
        slideIn(null);
    }

    /** Slides the sheet in with {@code sections} (a vertical list of its groups, or null) cascading after it. */
    void slideIn(ViewGroup sections) {
        if (!config.animations) return;
        if (sections != null && !config.reducedMotion) staggerIn(sections);
        Motion.Spec spring = Motion.DEFAULT_SPATIAL.reduced(config.reducedMotion);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(Motion.DEFAULT_EFFECTS.durationMs());
        sheet.setTranslationX(offscreen());
        // Once laid out, from just past the edge, so the spring covers the sheet's own width.
        sheet.post(() -> {
            sheet.setTranslationX(offscreen());
            sheet.animate().translationX(0f).setInterpolator(spring.interpolator()).setDuration(spring.durationMs());
        });
    }

    private void staggerIn(ViewGroup sections) {
        float offset = STAGGER_DP * activity.getResources().getDisplayMetrics().density;
        Motion.Spec spatial = Motion.DEFAULT_SPATIAL, effects = Motion.DEFAULT_EFFECTS;
        for (int i = 0; i < Math.min(STAGGERED, sections.getChildCount()); i++) {
            View section = sections.getChildAt(i);
            long delay = (i + 1) * STAGGER_MS;
            section.setTranslationX(offset * (1 + i * 0.15f));
            section.setAlpha(0f);
            spatial.drive(section.animate().translationX(0f)).setStartDelay(delay);
            effects.fade(section, 1f, delay);
        }
    }

    /** Slides the sheet out, then finishes the activity. */
    void close() {
        if (closing) return;
        closing = true;
        if (!config.animations) {
            activity.finish();
            return;
        }
        Motion.Spec fast = Motion.FAST_SPATIAL.reduced(config.reducedMotion);
        scrim.animate().alpha(0f).setDuration(fast.durationMs());
        sheet.animate().translationX(offscreen()).setInterpolator(fast.interpolator()).setDuration(fast.durationMs())
                .withEndAction(activity::finish);
    }

    /** Past the right edge: the sheet's width and its margin. */
    private float offscreen() {
        return sheet.getWidth() > 0 ? sheet.getWidth() + 24 * activity.getResources().getDisplayMetrics().density
                : activity.getResources().getDisplayMetrics().widthPixels;
    }
}
