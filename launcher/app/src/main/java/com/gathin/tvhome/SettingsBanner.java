package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * The banner for the TV's own Settings app, whose stock artwork (a grey gear on grey) sits outside the
 * scheme: a gear (onPrimary) in a 9-sided M3E cookie (primary) on a neutral surfaceContainerHighest tile.
 */
final class SettingsBanner extends Drawable {

    static final String PACKAGE = "com.android.tv.settings";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Drawable gear;
    private Palette palette;

    SettingsBanner(Context context) {
        gear = context.getDrawable(R.drawable.ic_settings_filled).mutate();
    }

    /** The scheme to draw in, from the adapter with every other tile, so it never lags the rest. */
    void setPalette(Palette p) {
        palette = p;
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas c) {
        Palette p = palette != null ? palette : Palette.currentOrBaseline();
        Rect b = getBounds();
        // A neutral container like the system Settings icon, with the accent on the cookie: content in
        // front of the backdrop, whose shapes use the container tones, never the same purple as them.
        c.drawColor(p.surfaceContainerHighest);
        float cx = b.exactCenterX(), cy = b.exactCenterY(), r = b.height() * 0.36f;
        ExpressiveBackdrop.shapePath(path, cx, cy, r, 9, 0.07f, 0f);
        paint.setColor(p.primary);
        c.drawPath(path, paint);
        int s = Math.round(r * 1.05f);
        gear.setBounds(Math.round(cx - s / 2f), Math.round(cy - s / 2f), Math.round(cx + s / 2f), Math.round(cy + s / 2f));
        gear.setTint(p.onPrimary);
        gear.draw(c);
    }

    @Override
    public int getIntrinsicWidth() {
        return 320;
    }

    @Override
    public int getIntrinsicHeight() {
        return 180;
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
