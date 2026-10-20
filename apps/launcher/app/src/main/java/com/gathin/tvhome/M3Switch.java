package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.StateSet;

import androidx.annotation.NonNull;

/**
 * The M3 switch as a drawable, for the end of a settings row: a 52x32dp track with a thumb that is
 * 16dp when off and 24dp when on. Toggling slides and grows the thumb on a fast spatial spring and
 * cross-fades the colours. Inside a focused row (inverseSurface) it takes the inverse roles.
 */
final class M3Switch extends Drawable {

    private final float density;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final ArgbEvaluator argb = new ArgbEvaluator();
    private final boolean animations;
    private Palette p;
    private boolean checked;
    private boolean focused;
    /** 0 off, 1 on: may overshoot on the spring. */
    private float progress;
    private ValueAnimator anim;

    M3Switch(float density, Palette palette, boolean animations) {
        this.density = density;
        this.p = palette;
        this.animations = animations;
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(2 * density);
    }

    void setPalette(Palette palette) {
        p = palette;
        invalidateSelf();
    }

    void setChecked(boolean on) {
        if (on == checked && anim == null) {
            progress = on ? 1f : 0f;
            invalidateSelf();
            return;
        }
        checked = on;
        if (anim != null) anim.cancel();
        if (!animations) {
            progress = on ? 1f : 0f;
            invalidateSelf();
            return;
        }
        anim = ValueAnimator.ofFloat(progress, on ? 1f : 0f);
        anim.setInterpolator(Motion.FAST_SPATIAL.interpolator());
        anim.setDuration(Motion.FAST_SPATIAL.durationMs());
        anim.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidateSelf();
        });
        anim.start();
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(@NonNull int[] state) {
        boolean f = StateSet.stateSetMatches(new int[]{android.R.attr.state_focused}, state);
        if (f == focused) return false;
        focused = f;
        invalidateSelf();
        return true;
    }

    @Override
    public int getIntrinsicWidth() {
        return Math.round(52 * density);
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.round(32 * density);
    }

    @Override
    public void draw(@NonNull Canvas c) {
        Rect b = getBounds();
        float t = Math.max(0f, Math.min(1f, progress));
        // Off and on colours, swapped onto the row's primary background while it is focused.
        // In a focused row (inverseSurface) the switch uses the inverse roles to stay legible.
        int trackOff = focused ? p.inverseSurface : p.surfaceContainerHighest;
        int trackOn = focused ? p.inversePrimary : p.primary;
        int ring = focused ? p.inverseOnSurface : p.outline;
        int thumbOff = focused ? p.inverseOnSurface : p.outline;
        int thumbOn = focused ? p.inverseSurface : p.onPrimary;

        float h = b.height(), r = h / 2f;
        rect.set(b.left, b.top, b.right, b.bottom);
        track.setColor((int) argb.evaluate(t, trackOff, trackOn));
        c.drawRoundRect(rect, r, r, track);
        if (t < 1f) {
            float inset = outline.getStrokeWidth() / 2f;
            outline.setColor(Palette.alpha(ring, Math.round(255 * (1f - t))));
            c.drawRoundRect(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset, r - inset, r - inset, outline);
        }
        // The thumb travels between the track's round ends and grows from 16dp to 24dp; the overshoot
        // of the spring carries the position (it may pass the end slightly) but not the size.
        float size = (16 + 8 * t) * density;
        float start = b.left + r, end = b.right - r;
        float cx = start + (end - start) * progress;
        thumb.setColor((int) argb.evaluate(t, thumbOff, thumbOn));
        c.drawCircle(cx, b.exactCenterY(), size / 2f, thumb);
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
