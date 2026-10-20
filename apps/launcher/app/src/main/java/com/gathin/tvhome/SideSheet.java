package com.gathin.tvhome;

import android.app.Activity;
import android.view.View;

/**
 * The motion of a side sheet activity (launcher settings, running apps): the sheet slides in from the
 * right edge on the default spatial spring while the scrim behind fades in, and leaves on the fast one.
 * Without animations it simply appears and goes.
 */
final class SideSheet {

    private final Activity activity;
    private final View scrim, sheet;
    private final Config config;
    private boolean closing;

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
        if (!config.animations) return;
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
