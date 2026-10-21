package com.gathin.tvhome;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

/**
 * One widget on Home's shelf: a surfaceContainer card shaped like an app tile (same corner radius).
 * The view is {@link #REACH_DP} larger than the card on every side: that is where the shelf's focus
 * indicator ({@link ShelfHighlight}) shows around it, the way the tiles' indicator reaches past a tile.
 *
 * Motion, as M3 Expressive has it: OK presses the card in a little on the fast spatial spring and lets it
 * spring back, and a card that steps to another page turns along the x axis, the old page leaving to the
 * left as the new one comes in from the right.
 */
public final class WidgetCard extends FrameLayout {

    static final float REACH_DP = FocusIndicator.PADDING_DP;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** An empty slot's fill: the screen's own surface, so the backdrop shapes do not show through. */
    private final Paint hole = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    /** An empty slot (Add widget): an outlineVariant outline at rest, a normal card while focused. */
    private boolean ghost;
    private boolean motion;
    private static final float PRESSED_SCALE = 0.96f;
    /** The page turn: how far the pages travel and how fast the old one leaves. */
    private static final float TURN_DP = 24f;
    private static final long LEAVE_MS = 90;
    private boolean turning;
    private Runnable turnPaint;
    private ObjectAnimator turnFade;

    public WidgetCard(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setFocusable(true);
        setClickable(true);
        int inset = Math.round(REACH_DP * density);
        setPadding(inset, inset, inset, inset);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(2 * density);
        setPalette(Palette.currentOrBaseline());
    }

    void setGhost(boolean on) {
        ghost = on;
        invalidate();
    }

    /** Animations on and motion not reduced (the shelf's own rule). */
    void setMotion(boolean on) {
        motion = on;
    }

    @Override
    public void setPressed(boolean pressed) {
        boolean was = isPressed();
        super.setPressed(pressed);
        // Only a card that acts on OK gives under it (system stats only show).
        if (pressed == was || !motion || !hasOnClickListeners()) return;
        float scale = pressed ? PRESSED_SCALE : 1f;
        Motion.FAST_SPATIAL.drive(animate().scaleX(scale).scaleY(scale));
    }

    /**
     * Shows the card's next page: {@code paint} fills the content, between the old page leaving and the new
     * one arriving. A press while a turn is under way finishes that one at once and turns again.
     */
    void turn(Runnable paint) {
        View content = getChildAt(0);
        if (!motion || content == null) {
            paint.run();
            return;
        }
        if (turning) settle(content);
        turning = true;
        turnPaint = paint;
        float shift = TURN_DP * density;
        content.animate().alpha(0f).translationX(-shift).setDuration(LEAVE_MS)
                .setInterpolator(new AccelerateInterpolator()).withEndAction(() -> arrive(content, shift));
    }

    private void arrive(View content, float shift) {
        turnPaint.run();
        content.setTranslationX(shift);
        Motion.DEFAULT_SPATIAL.drive(content.animate().translationX(0f)).withEndAction(() -> turning = false);
        turnFade = Motion.DEFAULT_EFFECTS.fade(content, 1f, 0);
    }

    /** Ends a turn under way where it was going: the new page painted, in place and opaque. */
    private void settle(View content) {
        content.animate().cancel();
        if (turnFade != null) turnFade.cancel();
        turnPaint.run();
        content.setTranslationX(0f);
        content.setAlpha(1f);
        turning = false;
    }

    void setPalette(Palette p) {
        fill.setColor(p.surfaceContainer);
        outline.setColor(p.outlineVariant);
        hole.setColor(p.surface);
        invalidate();
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, @Nullable Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (ghost) invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float inset = REACH_DP * density;
        float radius = Shapes.TILE_FOCUSED * density;
        float w = getWidth(), h = getHeight();
        if (ghost && !isFocused()) {
            // At rest an empty slot is only an outline; focused it is a card like the others.
            float half = outline.getStrokeWidth() / 2f;
            c.drawRoundRect(inset, inset, w - inset, h - inset, radius, radius, hole);
            c.drawRoundRect(inset + half, inset + half, w - inset - half, h - inset - half, radius - half, radius - half, outline);
            return;
        }
        c.drawRoundRect(inset, inset, w - inset, h - inset, radius, radius, fill);
    }
}
