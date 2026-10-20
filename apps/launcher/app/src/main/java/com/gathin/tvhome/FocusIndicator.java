package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.recyclerview.widget.RecyclerView;

/**
 * The M3E active indicator for app tiles: a primary-coloured rounded shape behind the focused tile,
 * reaching {@link #PADDING_DP} past it on every side, concentric with it (indicator radius = the
 * focused tile's radius + the padding). Drawn by the grid between the dock container and the tiles,
 * so it sits inside the container's 12dp padding, 8dp clear of its edge, and never crosses it.
 *
 * The motion comes from the activity's springs on the (invisible) GlassView: this reads that view's
 * centre and alpha every frame, so retargeting mid-flight and scrolling keep working as they did.
 *
 * With Expressive motion the indicator moves the way the M3E menu highlight does: a second, stiffer
 * spring carries its leading edge to the new tile ahead of the trailing one (the GlassView's), so it
 * stretches along the move, then settles back onto the tile as the trailing edge catches up.
 */
final class FocusIndicator extends RecyclerView.ItemDecoration {

    static final float PADDING_DP = 4f;
    static final float POP_FROM_DP = 1f;
    /** How far it reaches while its tile is carried in Edit Home: lifted, still inside the dock's 12dp padding. */
    static final float LIFT_DP = 8f;

    private final View model;
    private final ViewGroup root;
    private final RecyclerView grid;
    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Rect offset = new Rect();
    private float tileW, tileH, tileRadius;
    /** How far the indicator reaches past the tile right now, in dp: it pops from {@link #POP_FROM_DP}. */
    private float padDp = PADDING_DP;
    private final SpringAnimation pop;
    private final boolean animations;
    /** Expressive motion: the leading edge runs ahead on its own spring and the indicator stretches. */
    private final boolean stretch;
    /** The leading edge's centre, in the GlassView's translation space (y without the scroll shift). */
    private float leadX, leadY;
    private float scrollShift;
    private final SpringAnimation leadSpringX, leadSpringY;
    /** The most the leading edge may run ahead, as a share of the tile, so a long move stays a pill. */
    private static final float MAX_LEAD = 0.3f;
    /** Fast spatial stiffness with default spatial damping, against the trailing edge's default spatial. */
    private static final Motion.Spec LEAD = new Motion.Spec(Motion.DEFAULT_SPATIAL.damping, Motion.FAST_SPATIAL.stiffness);

    private static final FloatPropertyCompat<FocusIndicator> PAD = new FloatPropertyCompat<FocusIndicator>("pad") {
        @Override
        public float getValue(FocusIndicator f) {
            return f.padDp;
        }

        @Override
        public void setValue(FocusIndicator f, float value) {
            f.padDp = value;
            f.grid.invalidate();
        }
    };

    private static final FloatPropertyCompat<FocusIndicator> LEAD_X = new FloatPropertyCompat<FocusIndicator>("leadX") {
        @Override
        public float getValue(FocusIndicator f) {
            return f.leadX;
        }

        @Override
        public void setValue(FocusIndicator f, float value) {
            f.leadX = value;
            f.grid.invalidate();
        }
    };

    private static final FloatPropertyCompat<FocusIndicator> LEAD_Y = new FloatPropertyCompat<FocusIndicator>("leadY") {
        @Override
        public float getValue(FocusIndicator f) {
            return f.leadY;
        }

        @Override
        public void setValue(FocusIndicator f, float value) {
            f.leadY = value;
            f.grid.invalidate();
        }
    };

    FocusIndicator(View model, ViewGroup root, RecyclerView grid, float density, boolean animations, boolean reducedMotion,
                   boolean stretch) {
        this.model = model;
        this.root = root;
        this.grid = grid;
        this.density = density;
        this.animations = animations;
        this.stretch = stretch && animations && !reducedMotion;
        paint.setColor(Palette.currentOrBaseline().primary);   // until Home applies its scheme
        pop = Motion.FAST_SPATIAL.reduced(reducedMotion).apply(new SpringAnimation(this, PAD));
        pop.setMinimumVisibleChange(0.05f);
        leadSpringX = LEAD.reduced(reducedMotion).apply(new SpringAnimation(this, LEAD_X));
        leadSpringY = LEAD.reduced(reducedMotion).apply(new SpringAnimation(this, LEAD_Y));
    }

    /** The trailing springs got a new target (same space as theirs): the leading edge heads there too. */
    void leadTo(float x, float y) {
        if (!stretch) return;
        leadSpringX.animateToFinalPosition(x);
        leadSpringY.animateToFinalPosition(y);
    }

    /** The indicator jumped (shown, or no animation): the leading edge jumps with it. */
    void snapLead(float x, float y) {
        leadSpringX.cancel();
        leadSpringY.cancel();
        leadX = x;
        leadY = y;
    }

    /** How far the grid has scrolled the GlassView (MainActivity's glassScrollShift). */
    void setScrollShift(float shift) {
        scrollShift = shift;
    }

    private boolean lifted;

    /** Edit Home picked the tile up (wider reach) or put it down, on the same fast spatial spring as the pop. */
    void setLifted(boolean on) {
        if (on == lifted) return;
        lifted = on;
        float target = on ? LIFT_DP : PADDING_DP;
        if (!animations) {
            padDp = target;
            grid.invalidate();
        } else {
            pop.animateToFinalPosition(target);
        }
    }

    /** A tile took focus: the indicator grows out from behind it on a fast spatial spring. */
    void pop() {
        if (!animations || lifted) return;   // a carried tile moving keeps its lift
        pop.cancel();
        padDp = POP_FROM_DP;
        pop.animateToFinalPosition(PADDING_DP);
    }

    void setColor(int argb) {
        paint.setColor(argb);
    }

    /** The focused tile's unscaled size and its focused corner radius, in px. */
    void setTile(float width, float height, float radius) {
        tileW = width;
        tileH = height;
        tileRadius = radius;
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        float alpha = model.getAlpha();
        if (alpha <= 0f || tileW <= 0f || model.getWidth() <= 0) return;
        // The model's centre in root coordinates, moved into the grid's.
        offset.set(0, 0, 0, 0);
        root.offsetDescendantRectToMyCoords(parent, offset);
        float cx = model.getTranslationX() + model.getLeft() + model.getWidth() / 2f - offset.left;
        float cy = model.getTranslationY() + model.getTop() + model.getHeight() / 2f - offset.top;
        float pad = padDp * density;
        float halfW = tileW / 2f + pad, halfH = tileH / 2f + pad;
        rect.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH);
        if (stretch) {
            // The leading edge's tile-sized shape, at most MAX_LEAD of a tile ahead; the indicator spans both.
            float ax = Math.max(-tileW * MAX_LEAD, Math.min(tileW * MAX_LEAD, leadX - model.getTranslationX()));
            float ay = Math.max(-tileH * MAX_LEAD, Math.min(tileH * MAX_LEAD, leadY + scrollShift - model.getTranslationY()));
            rect.union(cx + ax - halfW, cy + ay - halfH, cx + ax + halfW, cy + ay + halfH);
        }
        // Concentric with the tile at every step of the pop: radius = tile radius + current reach.
        float r = tileRadius + pad;
        int base = paint.getAlpha();
        paint.setAlpha(Math.round(base * alpha));
        c.drawRoundRect(rect, r, r, paint);
        paint.setAlpha(base);
    }
}
