package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;

import java.util.ArrayList;
import java.util.List;

/**
 * The M3E menu's focus: one inverseSurface pill behind the rows that springs to the focused row,
 * instead of each row fading its own background in and out. The edge leading the move runs on a
 * stiffer spring than the trailing one, so the pill stretches along the move and settles back to the
 * row's height. Drawn as the menu panel's background, under the rows, with the panel's own fill.
 * A row's text and icons take the focused colour in proportion to how much of the row the pill
 * covers, so they change with the pill instead of switching ahead of it. In a menu taller than the
 * screen the rows scroll: the pill then sticks to the focused row as the list scrolls under it.
 */
final class MenuHighlight extends Drawable {

    /** The leading edge: default spatial damping (a light landing), fast spatial stiffness. */
    private static final Motion.Spec LEAD = new Motion.Spec(Motion.DEFAULT_SPATIAL.damping, Motion.FAST_SPATIAL.stiffness);
    private static final Motion.Spec TRAIL = Motion.DEFAULT_SPATIAL;

    private final ViewGroup panel;
    private final Drawable fill;
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float radius;
    private final boolean animations, reducedMotion;
    private final SpringAnimation topSpring, bottomSpring;
    private final Rect rowRect = new Rect();
    private float top, bottom, left, right;
    private boolean shown;
    private final ArgbEvaluator argb = new ArgbEvaluator();
    /** Text and icons in the rows with their rest and focused colours, read once from the skin. */
    private final List<Tinted> tinted = new ArrayList<>();
    private boolean collected;
    private static final int[] FOCUSED = {android.R.attr.state_focused};

    private static final class Tinted {
        final View view, row;
        final int rest, on;

        Tinted(View view, View row, int rest, int on) {
            this.view = view;
            this.row = row;
            this.rest = rest;
            this.on = on;
        }
    }

    private static final FloatPropertyCompat<MenuHighlight> TOP = new FloatPropertyCompat<MenuHighlight>("top") {
        @Override
        public float getValue(MenuHighlight h) {
            return h.top;
        }

        @Override
        public void setValue(MenuHighlight h, float value) {
            h.top = value;
            h.moved();
        }
    };

    private static final FloatPropertyCompat<MenuHighlight> BOTTOM = new FloatPropertyCompat<MenuHighlight>("bottom") {
        @Override
        public float getValue(MenuHighlight h) {
            return h.bottom;
        }

        @Override
        public void setValue(MenuHighlight h, float value) {
            h.bottom = value;
            h.moved();
        }
    };

    private final ViewTreeObserver.OnGlobalFocusChangeListener focusListener = (oldFocus, newFocus) -> moveTo(newFocus);

    /**
     * Puts the highlight behind panel's rows (views tagged by {@link #markRow}), or recolours the one
     * already there after a re-skin. fill is the panel's container colour and shape.
     */
    static void attach(ViewGroup panel, Drawable fill, int color, float radiusPx, boolean animations, boolean reducedMotion) {
        Object existing = panel.getTag(R.id.m3_menu_highlight);
        if (existing instanceof MenuHighlight) ((MenuHighlight) existing).detach();
        MenuHighlight h = new MenuHighlight(panel, fill, color, radiusPx, animations, reducedMotion);
        panel.setTag(R.id.m3_menu_highlight, h);
        panel.setBackground(h);
        panel.getViewTreeObserver().addOnGlobalFocusChangeListener(h.focusListener);
        ScrollView scroll = findScroll(panel);
        if (scroll != null) scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> h.followScroll());
        panel.post(() -> h.moveTo(panel.findFocus()));
    }

    /** Marks a row the highlight follows; its own background stays clear. */
    static void markRow(View row) {
        row.setTag(R.id.m3_menu_highlight, Boolean.TRUE);
    }

    private MenuHighlight(ViewGroup panel, Drawable fill, int color, float radiusPx, boolean animations, boolean reducedMotion) {
        this.panel = panel;
        this.fill = fill;
        this.radius = radiusPx;
        this.animations = animations;
        this.reducedMotion = reducedMotion;
        pill.setColor(color);
        topSpring = TRAIL.reduced(reducedMotion).apply(new SpringAnimation(this, TOP));
        bottomSpring = TRAIL.reduced(reducedMotion).apply(new SpringAnimation(this, BOTTOM));
        topSpring.setMinimumVisibleChange(0.5f);
        bottomSpring.setMinimumVisibleChange(0.5f);
    }

    private static ScrollView findScroll(View v) {
        if (v instanceof ScrollView) return (ScrollView) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                ScrollView s = findScroll(g.getChildAt(i));
                if (s != null) return s;
            }
        }
        return null;
    }

    /**
     * The rows scrolled (focus moved past the visible part): the springs aimed where the row was, so
     * put the pill straight onto where the focused row is now.
     */
    private void followScroll() {
        if (!shown) return;
        View row = rowOf(panel.findFocus());
        if (row == null) return;
        topSpring.cancel();
        bottomSpring.cancel();
        rowRect.set(0, 0, row.getWidth(), row.getHeight());
        panel.offsetDescendantRectToMyCoords(row, rowRect);
        top = rowRect.top;
        bottom = rowRect.bottom;
        moved();
    }

    /** A re-skin replaced this highlight: stop following focus and stop moving. */
    private void detach() {
        panel.getViewTreeObserver().removeOnGlobalFocusChangeListener(focusListener);
        topSpring.cancel();
        bottomSpring.cancel();
    }

    private void moveTo(View focused) {
        View row = rowOf(focused);
        if (row == null) {
            if (shown) {
                shown = false;
                moved();
            }
            return;
        }
        rowRect.set(0, 0, row.getWidth(), row.getHeight());
        panel.offsetDescendantRectToMyCoords(row, rowRect);
        left = rowRect.left;
        right = rowRect.right;
        if (!shown || !animations) {
            topSpring.cancel();
            bottomSpring.cancel();
            top = rowRect.top;
            bottom = rowRect.bottom;
            shown = true;
            moved();
            return;
        }
        boolean down = rowRect.top > top;
        (down ? TRAIL : LEAD).reduced(reducedMotion).apply(topSpring);
        (down ? LEAD : TRAIL).reduced(reducedMotion).apply(bottomSpring);
        topSpring.animateToFinalPosition(rowRect.top);
        bottomSpring.animateToFinalPosition(rowRect.bottom);
    }

    private void moved() {
        invalidateSelf();
        tint();
    }

    /** Colours each row's text and icons by the share of the row the pill covers right now. */
    private void tint() {
        if (!collected) {
            collected = true;
            collect(panel, null);
        }
        for (Tinted t : tinted) {
            float cover = 0f;
            if (shown && t.row.getHeight() > 0) {
                rowRect.set(0, 0, t.row.getWidth(), t.row.getHeight());
                panel.offsetDescendantRectToMyCoords(t.row, rowRect);
                float overlap = Math.min(rowRect.bottom, bottom) - Math.max(rowRect.top, top);
                cover = Math.max(0f, Math.min(1f, overlap / rowRect.height()));
            }
            int c = (int) argb.evaluate(cover, t.rest, t.on);
            if (t.view instanceof TextView) ((TextView) t.view).setTextColor(c);
            else ((ImageView) t.view).setImageTintList(ColorStateList.valueOf(c));
        }
    }

    /** Reads the skin's rest and focused colours of every text and tinted icon inside a row. */
    private void collect(View v, View row) {
        if (Boolean.TRUE.equals(v.getTag(R.id.m3_menu_highlight))) row = v;
        if (row != null) {
            ColorStateList c = v instanceof TextView ? ((TextView) v).getTextColors()
                    : v instanceof ImageView ? ((ImageView) v).getImageTintList() : null;
            // Only the skin sets a focus-aware list. A plain colour is one an earlier highlight on this
            // panel painted (menus get skinned twice); the colours it read then are kept on the view.
            if (c != null && c.isStateful()) {
                int rest = c.getDefaultColor();
                v.setTag(R.id.m3_menu_colors, new int[]{rest, c.getColorForState(FOCUSED, rest)});
            }
            Object saved = v.getTag(R.id.m3_menu_colors);
            if (saved instanceof int[]) {
                int[] colors = (int[]) saved;
                tinted.add(new Tinted(v, row, colors[0], colors[1]));
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), row);
        }
    }

    /** The marked row holding v, or null when focus is outside this panel's rows. */
    private View rowOf(View v) {
        while (v != null && v != panel) {
            if (Boolean.TRUE.equals(v.getTag(R.id.m3_menu_highlight))) return v;
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return null;
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        fill.setBounds(bounds);
    }

    @Override
    public void draw(@NonNull Canvas c) {
        fill.draw(c);
        if (!shown) return;
        float r = Math.min(radius, (bottom - top) / 2f);
        c.drawRoundRect(left, top, right, bottom, r, r, pill);
    }

    @Override
    public void getOutline(@NonNull android.graphics.Outline outline) {
        fill.getOutline(outline);   // the panel's elevation shadow keeps the container's shape
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        fill.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
