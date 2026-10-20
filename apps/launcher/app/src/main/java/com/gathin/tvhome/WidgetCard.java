package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

/**
 * One widget on Home's shelf: a surfaceContainer card shaped like an app tile (same corner radius).
 * The view is {@link #REACH_DP} larger than the card on every side: that is where the shelf's focus
 * indicator ({@link ShelfHighlight}) shows around it, the way the tiles' indicator reaches past a tile.
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
