package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * The M3 (2024) linear progress indicator, determinate: a rounded active bar, a gap, the rounded track,
 * and a stop dot at the track's end. Active in primary, track in secondaryContainer.
 */
public final class M3Progress extends View {

    private static final float GAP_DP = 4f, STOP_DP = 4f;

    private final Paint active = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private float progress;

    public M3Progress(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setPalette(Palette.currentOrBaseline());
    }

    void setPalette(Palette p) {
        active.setColor(p.primary);
        track.setColor(p.secondaryContainer);
        invalidate();
    }

    /** 0 to 1. */
    void setProgress(float value) {
        float v = Math.max(0f, Math.min(1f, value));
        if (v == progress) return;
        progress = v;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), r = h / 2f, gap = GAP_DP * density;
        float end = w * progress;
        if (end > 0f) c.drawRoundRect(0, 0, Math.max(h, end), h, r, r, active);
        float trackStart = end > 0f ? Math.max(h, end) + gap : 0f;
        if (trackStart < w) {
            c.drawRoundRect(trackStart, 0, w, h, r, r, track);
            c.drawCircle(w - r, r, Math.min(r, STOP_DP * density / 2f), active);
        }
    }
}
