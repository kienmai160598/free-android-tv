package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * The Material 3 focus indicator: a 3dp ring, 2dp outside the focused tile, in the scheme's secondary
 * colour. One instance floats over the grid and springs between tiles. (Named for the glass highlight
 * it replaced; the activity and layouts still refer to it by that name.)
 */
public class GlassView extends View {

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    /** The view is laid out this far outside the tile on each side (MainActivity's pad). */
    private final float inset;
    private float tileRadius;
    private int ringColor = Palette.currentOrBaseline().secondary;
    private int accent;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        inset = 6f * density;
        tileRadius = Shapes.TILE_FOCUSED * density;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Shapes.FOCUS_RING_WIDTH * density);
        ring.setColor(ringColor);
        // Cache the ring as a texture: the spring only changes translation, so the GPU moves it without redrawing.
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /** Colours the ring with an accent (festival, reorder mode); 0 goes back to the scheme's colour. */
    void setAccent(int argb) {
        accent = argb;
        ring.setColor(accent != 0 ? accent | 0xFF000000 : ringColor);
        invalidate();
    }

    /** The scheme's secondary colour, from the dynamic colour scheme. */
    void setRingColor(int argb) {
        ringColor = argb;
        if (accent == 0) ring.setColor(ringColor);
        invalidate();
    }

    /** The focused tile's corner radius in pixels; the ring adds its offset so the two stay concentric. */
    void setRadius(float px) {
        tileRadius = px;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float stroke = ring.getStrokeWidth();
        float out = Shapes.FOCUS_RING_OFFSET * density + stroke / 2f;
        rect.set(inset - out, inset - out, getWidth() - inset + out, getHeight() - inset + out);
        float r = tileRadius + out;
        canvas.drawRoundRect(rect, r, r, ring);
    }
}
