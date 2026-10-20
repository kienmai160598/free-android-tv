package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

/**
 * A page's hero badge, as Android 16 settings pages have: an icon centred in a 9-sided cookie in the
 * scheme's primaryContainer, the icon in onPrimaryContainer.
 */
public final class ShapeBadge extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private Drawable icon;
    private int iconColor = Palette.currentOrBaseline().onPrimaryContainer;

    public ShapeBadge(Context context, AttributeSet attrs) {
        super(context, attrs);
        fill.setColor(Palette.currentOrBaseline().primaryContainer);
        icon = context.getDrawable(R.drawable.ic_home_settings);
    }

    /** Colours of its own (the air quality band) instead of the scheme's primaryContainer. */
    void setColors(int fill, int icon) {
        this.fill.setColor(fill);
        iconColor = icon;
        invalidate();
    }

    void setIcon(Drawable d) {
        icon = d;
        invalidate();
    }

    void setPalette(Palette p) {
        fill.setColor(p.primaryContainer);
        iconColor = p.onPrimaryContainer;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy);
        ExpressiveBackdrop.shapePath(path, cx, cy, r, 9, 0.07f, 0f);
        c.drawPath(path, fill);
        if (icon != null) {
            int s = Math.round(r * 0.9f);
            icon.setBounds(Math.round(cx - s / 2f), Math.round(cy - s / 2f), Math.round(cx + s / 2f), Math.round(cy + s / 2f));
            icon.setTint(iconColor);
            icon.draw(c);
        }
    }
}
