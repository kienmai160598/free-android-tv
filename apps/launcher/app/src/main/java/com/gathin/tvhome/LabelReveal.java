package com.gathin.tvhome;

import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;

/**
 * M3E button group behaviour for a header button: an icon-only segment at rest that grows into a
 * labelled pill while focused, the way an extended FAB expands. The width runs on a fast spatial spring
 * softened to a light landing; the label fades in with the growth (fully shown only near full width) and
 * out ahead of the collapse, so it never reads as text being cut off.
 */
final class LabelReveal {

    /** A segment at rest: wider than tall, as a connected group's icon buttons are. */
    static final float WIDTH_DP = 48f, ICON_DP = 22f, LABEL_GAP_DP = 8f, END_DP = 16f;
    /** Fast spatial stiffness with default spatial damping: quick, with a small overshoot. */
    private static final Motion.Spec WIDTH_SPRING = new Motion.Spec(Motion.DEFAULT_SPATIAL.damping, Motion.FAST_SPATIAL.stiffness);

    private final TextView view;
    private final float density;
    private final boolean animations;
    private final SpringAnimation width;
    private float widthPx;

    private static final FloatPropertyCompat<LabelReveal> WIDTH = new FloatPropertyCompat<LabelReveal>("width") {
        @Override
        public float getValue(LabelReveal r) {
            return r.widthPx;
        }

        @Override
        public void setValue(LabelReveal r, float value) {
            r.setWidth(value);
        }
    };

    LabelReveal(TextView view, boolean animations, boolean reducedMotion) {
        this.view = view;
        this.animations = animations;
        density = view.getResources().getDisplayMetrics().density;
        // Icon centred in the segment; the label starts where the segment ends.
        int inset = Math.round((WIDTH_DP - ICON_DP) / 2f * density);
        view.setPaddingRelative(inset, 0, Math.round(END_DP * density), 0);
        view.setCompoundDrawablePadding(Math.round(LABEL_GAP_DP * density));
        view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        view.setEllipsize(null);
        width = WIDTH_SPRING.reduced(reducedMotion).apply(new SpringAnimation(this, WIDTH));
        width.setMinimumVisibleChange(1f);
        setWidth(collapsed());
        // The Wi-Fi label follows the network name: re-measure when the text changes.
        view.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(android.text.Editable s) { update(); }
        });
    }

    /** Grows to show the label while focused or activated, shrinks back to the circle otherwise. */
    void update() {
        float target = view.isFocused() || view.isActivated() ? expanded() : collapsed();
        if (!animations) {
            width.cancel();
            setWidth(target);
        } else {
            width.animateToFinalPosition(target);
        }
    }

    private float collapsed() {
        return WIDTH_DP * density;
    }

    private float expanded() {
        CharSequence text = view.getText();
        if (text == null || text.length() == 0) return collapsed();
        return view.getPaddingStart() + ICON_DP * density + view.getCompoundDrawablePadding()
                + view.getPaint().measureText(text.toString()) + view.getPaddingEnd();
    }

    private void setWidth(float px) {
        widthPx = px;
        float open = expanded() - collapsed();
        float shown = open <= 0f ? 0f : Math.max(0f, Math.min(1f, (px - collapsed()) / open));
        Expressive e = Expressive.of(view);
        if (e != null) e.labelAlpha(shown * shown);   // stays faint until the pill is mostly open
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) return;
        // The spring may overshoot below the segment on the way back; never narrower than the segment.
        int w = Math.round(Math.max(collapsed(), px));
        if (lp.width != w) {
            lp.width = w;
            view.setLayoutParams(lp);
        }
    }
}
