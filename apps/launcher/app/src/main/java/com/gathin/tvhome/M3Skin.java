package com.gathin.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Re-skins an inflated view tree in Material 3 Expressive with the current dynamic colours. Layouts
 * mark their roles with android:tag (m3:panel, and m3:button / m3:choice through the TopButton and
 * SettingsRow styles); a focusable group inside a panel is a row. Each gets its M3 role:
 * <ul>
 *   <li>menu panel: surfaceContainerHigh, 32dp corners, 8dp padding; dialog panel: 28dp, 24dp padding;</li>
 *   <li>menu row: 48dp, fully round, primary when focused. Concentric with the menu panel:
 *       24dp row radius + 8dp padding = 32dp panel radius;</li>
 *   <li>button: an {@link Expressive} tonal button; a row of choices: a connected button group;</li>
 *   <li>text and icons: onSurface / onSurfaceVariant, onPrimary inside a focused row.</li>
 * </ul>
 * Run it once per inflated tree, after the views are filled in.
 */
final class M3Skin {

    static final float MENU_PADDING_DP = 8f;
    static final float MENU_ROW_DP = 48f;
    /** Concentric: the row is fully round (half of 48dp) and the panel adds its padding. */
    static final float MENU_RADIUS_DP = MENU_ROW_DP / 2f + MENU_PADDING_DP;
    static final float DIALOG_RADIUS_DP = Shapes.EXTRA_LARGE;
    static final float DIALOG_PADDING_DP = 24f;
    /**
     * TV Material3 focused scales: buttons 1.1. List items stay at 1: the token's 1.05 assumes room around
     * the item, and these rows fill their column, so scaling would push them past it and get them clipped.
     */
    static final float BUTTON_FOCUS_SCALE = 1.1f, LIST_ITEM_FOCUS_SCALE = 1f;

    private final Context context;
    private final Palette p;
    private final boolean animations, reducedMotion;
    private final float density;
    /** The menu panel being walked, whose rows its highlight follows; null outside a menu. */
    private ViewGroup menuPanel;

    private M3Skin(Context context, Palette p, boolean animations, boolean reducedMotion) {
        this.context = context;
        this.p = p;
        this.animations = animations;
        this.reducedMotion = reducedMotion;
        density = context.getResources().getDisplayMetrics().density;
    }

    static void apply(View root, Palette p, boolean animations, boolean reducedMotion) {
        M3Skin skin = new M3Skin(root.getContext(), p, animations, reducedMotion);
        if (root.getBackground() instanceof ColorDrawable) root.setBackgroundColor(p.surface);
        // A tagged button or panel can be the root itself (a library row, a popup panel).
        skin.walk(root, false, false);
    }

    /** Re-reads the selection of every expressive control under root (after setSelected calls). */
    static void refresh(View root) {
        Expressive e = Expressive.of(root);
        if (e != null) e.update();
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) refresh(g.getChildAt(i));
        }
    }

    private void walk(View v, boolean inPanel, boolean inRow) {
        Object tag = v.getTag();
        Expressive attached = Expressive.of(v);
        if (attached != null) attached.palette(p);   // a re-skin after the scheme changed (dark/light)
        boolean isRow = false;
        if ("m3:panel".equals(tag)) {
            menuPanel = skinPanel(v) ? (ViewGroup) v : null;
            inPanel = true;
        } else if (inPanel && !inRow && v instanceof ViewGroup && v.isFocusable() && v.isClickable()) {
            // Any clickable, focusable group inside a panel is a list row (menu rows, input rows, card
            // rows). Clickable keeps out a ScrollView, which makes itself focusable.
            skinRow(v, menuPanel != null);
            isRow = true;
        } else if ("m3:button".equals(tag) && v instanceof TextView && Expressive.of(v) == null) {
            Expressive.attach((TextView) v, animations, reducedMotion, BUTTON_FOCUS_SCALE).palette(p);
        } else if ("m3:choice".equals(tag) && v instanceof TextView && Expressive.of(v) == null) {
            skinChoice((TextView) v);
        } else if ("m3:listitem".equals(tag) && v instanceof TextView && Expressive.of(v) == null) {
            skinListItem((TextView) v);
        } else if (v instanceof ShapeBadge) {
            ((ShapeBadge) v).setPalette(p);
        }
        if (v instanceof TextView && Expressive.of(v) == null) skinText((TextView) v, inRow || isRow);
        if (v instanceof ImageView) skinIcon((ImageView) v, inRow || isRow);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i), inPanel, inRow || isRow);
        }
    }

    /** Skins a panel; true when it is a menu (its rows then share one sliding highlight). */
    private boolean skinPanel(View v) {
        boolean menu = v.getPaddingTop() <= (int) (MENU_PADDING_DP * density);
        GradientDrawable d = new GradientDrawable();
        d.setColor(p.surfaceContainerHigh);
        d.setCornerRadius((menu ? MENU_RADIUS_DP : DIALOG_RADIUS_DP) * density);
        if (menu && v instanceof ViewGroup) {
            MenuHighlight.attach((ViewGroup) v, d, p.inverseSurface, MENU_ROW_DP / 2f * density, animations, reducedMotion);
        } else {
            v.setBackground(d);
        }
        int pad = (int) ((menu ? MENU_PADDING_DP : DIALOG_PADDING_DP) * density);
        v.setPadding(pad, pad, pad, menu ? pad : (int) (20 * density));
        v.setElevation(menu ? 3 * density : 6 * density);
        return menu && v instanceof ViewGroup;
    }

    private void skinRow(View v, boolean inMenu) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp != null && lp.height > 0 && lp.height < MENU_ROW_DP * density) lp.height = (int) (MENU_ROW_DP * density);
        int padH = (int) (16 * density);
        v.setPadding(padH, v.getPaddingTop(), padH, v.getPaddingBottom());
        if (inMenu) {
            // The menu's highlight slides behind the rows; the row itself stays clear.
            MenuHighlight.markRow(v);
            v.setBackground(null);
            return;
        }
        StateListDrawable states = new StateListDrawable();
        // TV Material3 list item: inverseSurface when focused.
        states.addState(new int[]{android.R.attr.state_focused}, rounded(p.inverseSurface, MENU_ROW_DP / 2f));
        states.addState(new int[0], rounded(0, MENU_ROW_DP / 2f));
        if (animations) {
            states.setEnterFadeDuration((int) Motion.FAST_EFFECTS.durationMs());
            states.setExitFadeDuration((int) Motion.FAST_EFFECTS.durationMs());
        }
        v.setBackground(states);
    }

    /** Settings choices: a lone one is a 56dp tonal button, a row of them a connected button group. */
    private void skinChoice(TextView v) {
        Expressive e = Expressive.attach(v, animations, reducedMotion, 1f).asChoice().palette(p);
        ViewGroup parent = (ViewGroup) v.getParent();
        int count = 0, index = -1;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View c = parent.getChildAt(i);
            if (c.getBackground() == null && Expressive.of(c) == null) continue;
            if (c == v) index = count;
            count++;
        }
        float inner = Shapes.SMALL * density, F = Expressive.FULL;
        boolean group = parent instanceof LinearLayout
                && ((LinearLayout) parent).getOrientation() == LinearLayout.HORIZONTAL && count > 1;
        if (group) {
            // Connected button group: outer corners round, inner ones small, 2dp apart; focus rounds it fully.
            boolean first = index == 0, last = index == count - 1;
            e.corners(new float[]{first ? F : inner, last ? F : inner, last ? F : inner, first ? F : inner},
                    new float[]{F, F, F, F});
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.setMarginEnd(last ? 0 : (int) (2 * density));
            lp.setMarginStart(0);
            v.setLayoutParams(lp);
        } else {
            float rest = Shapes.LARGE * density;
            e.corners(new float[]{rest, rest, rest, rest}, new float[]{F, F, F, F});
        }
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        lp.height = (int) (40 * density);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        v.setTypeface(font(R.font.sans_medium));
        int pad = (int) (16 * density);
        v.setPadding(pad, 0, pad, 0);
        e.update();
    }

    /**
     * A grouped list item, Android 16 settings style: items of one group 2dp apart, large corners at the
     * group's outer ends and small ones between items; focus rounds every corner to extra-large.
     */
    private void skinListItem(TextView v) {
        Expressive e = Expressive.attach(v, animations, reducedMotion, LIST_ITEM_FOCUS_SCALE).asListItem().tintIcons(false).palette(p);
        ViewGroup parent = (ViewGroup) v.getParent();
        int count = 0, index = -1;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View c = parent.getChildAt(i);
            if (!"m3:listitem".equals(c.getTag()) || c.getVisibility() == View.GONE) continue;
            if (c == v) index = count;
            count++;
        }
        float outer = Shapes.LARGE_INCREASED * density, inner = Shapes.EXTRA_SMALL * density;
        float focus = Shapes.EXTRA_LARGE * density;
        boolean first = index == 0, last = index == count - 1;
        e.corners(new float[]{first ? outer : inner, first ? outer : inner, last ? outer : inner, last ? outer : inner},
                new float[]{focus, focus, focus, focus});
        if (!first) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.topMargin = (int) (2 * density);
            v.setLayoutParams(lp);
        }
        e.update();
    }

    private void skinText(TextView t, boolean inRow) {
        if ("m3:grouplabel".equals(t.getTag())) {
            t.setTextColor(p.primary);
            return;
        }
        // The role (primary or secondary text, state-aware or not) comes from the layout's own colour the
        // first time; a later re-skin (dark/light) reuses it, since by then every colour is opaque.
        Object saved = t.getTag(R.id.m3_text_role);
        int role;
        if (saved instanceof Integer) {
            role = (Integer) saved;
        } else {
            ColorStateList c = t.getTextColors();
            int rest = c.getDefaultColor();
            int focused = c.getColorForState(new int[]{android.R.attr.state_focused}, rest);
            role = ((rest >>> 24) >= 0xE6 ? 1 : 0) | (focused != rest ? 2 : 0);
            t.setTag(R.id.m3_text_role, role);
        }
        boolean strong = (role & 1) != 0;
        if (inRow || (role & 2) != 0) {
            int on = strong ? p.inverseOnSurface : Palette.alpha(p.inverseOnSurface, 0xCC);
            t.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                    new int[]{on, strong ? p.onSurface : p.onSurfaceVariant}));
        } else {
            t.setTextColor(strong ? p.onSurface : p.onSurfaceVariant);
        }
        Typeface tf = t.getTypeface();
        if (tf == null || tf == Typeface.DEFAULT || tf == Typeface.DEFAULT_BOLD) {
            float sp = t.getTextSize() / context.getResources().getDisplayMetrics().scaledDensity;
            t.setTypeface(font(sp >= 28 ? R.font.sans_headline : sp >= 18 ? R.font.sans_medium : R.font.sans_regular));
        }
        if (android.os.Build.VERSION.SDK_INT >= 28 && t.isAllCaps()) {
            // M3 sets labels in sentence case; the wide tracking was there for the capitals.
            t.setAllCaps(false);
            t.setLetterSpacing(0.01f);
        }
    }

    private void skinIcon(ImageView i, boolean inRow) {
        ColorStateList tint = i.getImageTintList();
        if (tint == null && !inRow) return;
        i.setImageTintList(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{p.inverseOnSurface, p.onSurfaceVariant}));
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusDp * density);
        return d;
    }

    private Typeface font(int res) {
        try {
            return context.getResources().getFont(res);
        } catch (RuntimeException e) {
            return Typeface.DEFAULT;
        }
    }
}
