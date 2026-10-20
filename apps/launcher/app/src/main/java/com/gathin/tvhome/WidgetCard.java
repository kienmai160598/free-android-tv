package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;

/**
 * One widget on Home's shelf: a surfaceContainer card shaped like an app tile (same corner radius),
 * focused the way a tile is: the primary indicator grows out from behind it on a fast spatial spring,
 * reaching {@link FocusIndicator#PADDING_DP} past every side, concentric with the card. The view is that
 * much larger than the card on each side, so the indicator has room inside it.
 */
public final class WidgetCard extends FrameLayout {

    static final float REACH_DP = FocusIndicator.PADDING_DP;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** An empty slot (Add widget): an outlineVariant outline instead of the filled card. */
    private boolean ghost;
    private final float density;
    private final SpringAnimation pop;
    private boolean animations = true;
    /** How far the indicator reaches past the card right now, in dp; 0 hides it. */
    private float reachDp;

    private static final FloatPropertyCompat<WidgetCard> REACH = new FloatPropertyCompat<WidgetCard>("reach") {
        @Override
        public float getValue(WidgetCard c) {
            return c.reachDp;
        }

        @Override
        public void setValue(WidgetCard c, float value) {
            c.reachDp = value;
            c.invalidate();
        }
    };

    public WidgetCard(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setFocusable(true);
        setClickable(true);
        int inset = Math.round(REACH_DP * density);
        setPadding(inset, inset, inset, inset);
        Palette p = Palette.currentOrBaseline();
        fill.setColor(p.surfaceContainer);
        ring.setColor(p.primary);
        pop = Motion.FAST_SPATIAL.apply(new SpringAnimation(this, REACH));
        pop.setMinimumVisibleChange(0.05f);
    }

    void setGhost(boolean on) {
        ghost = on;
        fill.setStyle(on ? Paint.Style.STROKE : Paint.Style.FILL);
        fill.setStrokeWidth(on ? 2 * density : 0f);
        invalidate();
    }

    void setMotion(boolean animations, boolean reducedMotion) {
        this.animations = animations;
        Motion.FAST_SPATIAL.reduced(reducedMotion).apply(pop);
    }

    void setPalette(Palette p) {
        fill.setColor(ghost ? p.outlineVariant : p.surfaceContainer);
        ring.setColor(p.primary);
        invalidate();
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, @Nullable Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        pop.cancel();
        if (!gainFocus) {
            reachDp = 0f;
        } else if (animations) {
            reachDp = FocusIndicator.POP_FROM_DP;
            pop.animateToFinalPosition(REACH_DP);
        } else {
            reachDp = REACH_DP;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float inset = REACH_DP * density;
        float radius = Shapes.TILE_FOCUSED * density;
        float w = getWidth(), h = getHeight();
        if (reachDp > 0f) {
            float out = inset - reachDp * density;
            float r = radius + reachDp * density;   // concentric: card radius + the indicator's reach
            c.drawRoundRect(out, out, w - out, h - out, r, r, ring);
        }
        if (ghost) {
            // Over the indicator a ghost card is filled with it; at rest only the outline shows.
            if (reachDp > 0f) return;
            float half = fill.getStrokeWidth() / 2f;
            c.drawRoundRect(inset + half, inset + half, w - inset - half, h - inset - half, radius - half, radius - half, fill);
            return;
        }
        c.drawRoundRect(inset, inset, w - inset, h - inset, radius, radius, fill);
    }
}
