package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;

/**
 * One M3 Expressive control: a button, a connected-group segment or a choice. It owns its background
 * and moves between three looks:
 * rest, selected (a choice that is the current value) and focused or activated (reorder mode), in the
 * TV Material3 colour tokens ({@link #palette}).
 * Focus also changes the shape: every corner goes to its focused radius on a fast spatial spring (so it
 * overshoots a touch, the M3E shape morph), while the colours cross-fade on a default effects spring.
 * Corners are given in px, {@link #FULL} for half the height.
 */
final class Expressive {

    static final float FULL = -1f;

    private final TextView view;
    private final GradientDrawable background = new GradientDrawable();
    private final ArgbEvaluator argb = new ArgbEvaluator();
    private final boolean animations;
    private final SpringAnimation shape, scaleX, scaleY;
    private final float focusScale;
    private ValueAnimator fade;
    /** 0 = rest or selected, 1 = focused: the shape spring's position (may overshoot) and the colour fade's. */
    private float shapeProgress, colorProgress;
    private float[] restCorners = {FULL, FULL, FULL, FULL};
    private float[] focusCorners = {FULL, FULL, FULL, FULL};
    private final float[] radii = new float[8];
    private int restBg, restFg, selectedBg, selectedFg, activeBg, activeFg;
    private boolean tintIcons = true;
    /** An icon colour of its own at rest (0: the content colour); focus blends it to the content colour. */
    private int iconTint;
    /** The label's opacity on top of its colour, for a label that fades in as its button grows. */
    private float labelAlpha = 1f;
    private boolean choice;
    private boolean listItem;
    /** Extra work on focus changes (a header button revealing its label). */
    private Runnable focusHook;

    private static final FloatPropertyCompat<Expressive> SHAPE = new FloatPropertyCompat<Expressive>("shape") {
        @Override
        public float getValue(Expressive e) {
            return e.shapeProgress;
        }

        @Override
        public void setValue(Expressive e, float value) {
            e.shapeProgress = value;
            e.applyShape();
        }
    };

    /** Makes view an expressive control; focus changes drive it from now on. */
    static Expressive attach(TextView view, boolean animations, boolean reducedMotion, float focusScale) {
        Expressive e = new Expressive(view, animations, reducedMotion, focusScale);
        view.setTag(R.id.expressive, e);
        return e;
    }

    /** The control already attached to view, or null. */
    static Expressive of(View view) {
        Object tag = view.getTag(R.id.expressive);
        return tag instanceof Expressive ? (Expressive) tag : null;
    }

    private Expressive(TextView view, boolean animations, boolean reducedMotion, float focusScale) {
        this.view = view;
        this.animations = animations;
        this.focusScale = animations ? focusScale : 1f;
        background.setShape(GradientDrawable.RECTANGLE);
        view.setBackground(background);
        shape = Motion.FAST_SPATIAL.reduced(reducedMotion).apply(new SpringAnimation(this, SHAPE));
        shape.setMinimumVisibleChange(0.002f);
        scaleX = Motion.FAST_SPATIAL.reduced(reducedMotion).apply(new SpringAnimation(view, DynamicAnimation.SCALE_X));
        scaleY = Motion.FAST_SPATIAL.reduced(reducedMotion).apply(new SpringAnimation(view, DynamicAnimation.SCALE_Y));
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot) applyShape();
        });
        view.setOnFocusChangeListener((v, focused) -> {
            update();
            if (focusHook != null) focusHook.run();
        });
    }

    /** Corners at rest and focused, clockwise from the top left (start-top in LTR). */
    Expressive corners(float[] rest, float[] focused) {
        restCorners = rest.clone();
        focusCorners = focused.clone();
        applyShape();
        return this;
    }

    /** A choice rests on surfaceContainerHighest and shows its current value in secondaryContainer. */
    Expressive asChoice() {
        choice = true;
        return this;
    }

    Expressive onFocusChange(Runnable hook) {
        focusHook = hook;
        return this;
    }

    /** A grouped list item rests on surfaceContainer with onSurface text. */
    Expressive asListItem() {
        listItem = true;
        return this;
    }

    /** Colours the icon apart from the label at rest, e.g. error for a warning; 0 goes back to normal. */
    void iconTint(int argb) {
        if (argb == iconTint) return;
        iconTint = argb;
        applyColors();
    }

    /** Fades the text alone (not the icon), 0 to 1. */
    void labelAlpha(float alpha) {
        if (alpha == labelAlpha) return;
        labelAlpha = alpha;
        applyColors();
    }

    Expressive tintIcons(boolean tint) {
        tintIcons = tint;
        applyColors();
        return this;
    }

    /**
     * Colours from the TV Material3 tokens (androidx.tv.material3 ButtonDefaults / ListItemDefaults):
     * a button rests on surfaceVariant at 80% with onSurface at 80% and inverts when focused (onSurface
     * container, inverseOnSurface content); a list item rests on its group's surfaceContainer and takes
     * inverseSurface when focused; a selected choice is secondaryContainer at 40% with
     * onSecondaryContainer.
     */
    Expressive palette(Palette p) {
        if (listItem) {
            restBg = p.surfaceContainer;
            restFg = p.onSurface;
            activeBg = p.inverseSurface;
            activeFg = p.inverseOnSurface;
        } else if (choice) {
            // A segmented choice: the selected one must read lighter than its siblings, so rest sits on
            // surfaceContainerHigh and the selection takes secondaryContainer in full. (The TV list token's
            // 40% secondaryContainer is for items on a transparent list and came out darker here.)
            restBg = p.surfaceContainerHigh;
            restFg = p.onSurfaceVariant;
            activeBg = p.onSurface;
            activeFg = p.inverseOnSurface;
        } else {
            restBg = Palette.alpha(p.surfaceVariant, 0xCC);
            restFg = Palette.alpha(p.onSurface, 0xCC);
            activeBg = p.onSurface;
            activeFg = p.inverseOnSurface;
        }
        selectedBg = choice ? p.secondaryContainer : Palette.alpha(p.secondaryContainer, 0x66);
        selectedFg = p.onSecondaryContainer;
        applyColors();
        return this;
    }

    /** Re-reads focus, selection and the activated state and moves towards them. */
    void update() {
        boolean on = view.isFocused() || view.isActivated();
        float target = on ? 1f : 0f;
        if (fade != null) fade.cancel();
        if (!animations) {
            shape.cancel();
            shapeProgress = colorProgress = target;
            applyShape();
            applyColors();
            return;
        }
        fade = ValueAnimator.ofFloat(colorProgress, target);
        fade.setInterpolator(Motion.DEFAULT_EFFECTS.interpolator());
        fade.setDuration(Motion.DEFAULT_EFFECTS.durationMs());
        fade.addUpdateListener(a -> {
            colorProgress = (float) a.getAnimatedValue();
            applyColors();
        });
        fade.start();
        shape.animateToFinalPosition(target);
        float s = view.isFocused() ? focusScale : 1f;
        scaleX.animateToFinalPosition(s);
        scaleY.animateToFinalPosition(s);
    }

    private void applyShape() {
        float full = view.getHeight() / 2f;
        for (int i = 0; i < 4; i++) {
            float rest = restCorners[i] == FULL ? full : restCorners[i];
            float focus = focusCorners[i] == FULL ? full : focusCorners[i];
            float r = Math.max(0f, Math.min(full, rest + (focus - rest) * shapeProgress));
            radii[i * 2] = r;
            radii[i * 2 + 1] = r;
        }
        background.setCornerRadii(radii);
    }

    private void applyColors() {
        boolean selected = view.isSelected();
        int baseBg = selected ? selectedBg : restBg, baseFg = selected ? selectedFg : restFg;
        background.setColor((int) argb.evaluate(colorProgress, baseBg, activeBg));
        int fg = (int) argb.evaluate(colorProgress, baseFg, activeFg);
        view.setTextColor(labelAlpha >= 1f ? fg : Palette.alpha(fg, Math.round((fg >>> 24) * labelAlpha)));
        int icon = iconTint == 0 ? fg : (int) argb.evaluate(colorProgress, iconTint, activeFg);
        if (tintIcons) view.setCompoundDrawableTintList(ColorStateList.valueOf(icon));
    }
}
