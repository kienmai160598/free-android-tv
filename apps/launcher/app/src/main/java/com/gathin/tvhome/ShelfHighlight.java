package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;

/**
 * The widget shelf's focus indicator, moving exactly as the app tiles' one does ({@link FocusIndicator}):
 * a primary rounded shape behind the focused card, reaching {@link FocusIndicator#PADDING_DP} past it,
 * concentric with it. It pops from {@link FocusIndicator#POP_FROM_DP} when focus arrives on the shelf,
 * travels between cards on the default spatial spring, and with Expressive motion its leading edge runs
 * ahead on a stiffer spring so it stretches along the move and settles. Drawn as the shelf's background,
 * so the cards' own fill covers all of it but the reach.
 */
final class ShelfHighlight extends Drawable {

    private static final Motion.Spec LEAD = new Motion.Spec(Motion.DEFAULT_SPATIAL.damping, Motion.FAST_SPATIAL.stiffness);
    private static final float MAX_LEAD = 0.3f;

    private final ViewGroup shelf;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density, inset;
    private final boolean animations, stretch;
    private final SpringAnimation trailSpring, leadSpring, pop;
    /** The focused card's left edge (trailing and leading), its visible width, top and height, in px. */
    private float trailX, leadX, cardW, cardTop, cardH;
    private float padDp = FocusIndicator.PADDING_DP;
    private boolean shown;

    private static FloatPropertyCompat<ShelfHighlight> property(String name, int which) {
        return new FloatPropertyCompat<ShelfHighlight>(name) {
            @Override
            public float getValue(ShelfHighlight h) {
                return which == 0 ? h.trailX : which == 1 ? h.leadX : h.padDp;
            }

            @Override
            public void setValue(ShelfHighlight h, float value) {
                if (which == 0) h.trailX = value;
                else if (which == 1) h.leadX = value;
                else h.padDp = value;
                h.invalidateSelf();
            }
        };
    }

    private final ViewTreeObserver.OnGlobalFocusChangeListener focusListener = (oldFocus, newFocus) -> moveTo(newFocus);

    static ShelfHighlight attach(ViewGroup shelf, boolean animations, boolean reducedMotion, boolean stretch) {
        ShelfHighlight h = new ShelfHighlight(shelf, animations, reducedMotion, stretch);
        shelf.setBackground(h);
        shelf.getViewTreeObserver().addOnGlobalFocusChangeListener(h.focusListener);
        return h;
    }

    private ShelfHighlight(ViewGroup shelf, boolean animations, boolean reducedMotion, boolean stretch) {
        this.shelf = shelf;
        this.animations = animations;
        this.stretch = stretch && animations && !reducedMotion;
        density = shelf.getResources().getDisplayMetrics().density;
        inset = WidgetCard.REACH_DP * density;
        paint.setColor(Palette.currentOrBaseline().primary);
        trailSpring = Motion.DEFAULT_SPATIAL.reduced(reducedMotion).apply(new SpringAnimation(this, property("trail", 0)));
        leadSpring = LEAD.reduced(reducedMotion).apply(new SpringAnimation(this, property("lead", 1)));
        pop = Motion.FAST_SPATIAL.reduced(reducedMotion).apply(new SpringAnimation(this, property("pop", 2)));
        trailSpring.setMinimumVisibleChange(0.5f);
        leadSpring.setMinimumVisibleChange(0.5f);
        pop.setMinimumVisibleChange(0.05f);
    }

    private boolean lifted;

    /** A widget picked up in Edit Home: the indicator reaches further, as a carried tile's does. */
    void setLifted(boolean on) {
        if (on == lifted) return;
        lifted = on;
        float target = on ? FocusIndicator.LIFT_DP : FocusIndicator.PADDING_DP;
        if (!animations) {
            padDp = target;
            invalidateSelf();
        } else {
            pop.animateToFinalPosition(target);
        }
    }

    void setColor(int argb) {
        paint.setColor(argb);
        invalidateSelf();
    }

    /** Focus moved: to a card on this shelf (slide or pop there) or away from it (hide). */
    private void moveTo(View focused) {
        if (!(focused instanceof WidgetCard) || focused.getParent() != shelf) {
            if (shown) {
                shown = false;
                trailSpring.cancel();
                leadSpring.cancel();
                pop.cancel();
                invalidateSelf();
            }
            return;
        }
        float x = focused.getLeft() + inset;
        cardW = focused.getWidth() - 2 * inset;
        cardTop = focused.getTop() + inset;
        cardH = focused.getHeight() - 2 * inset;
        if (!shown || !animations) {
            trailSpring.cancel();
            leadSpring.cancel();
            trailX = leadX = x;
            shown = true;
            float rest = lifted ? FocusIndicator.LIFT_DP : FocusIndicator.PADDING_DP;
            if (animations) {
                pop.cancel();
                padDp = FocusIndicator.POP_FROM_DP;
                pop.animateToFinalPosition(rest);
            } else {
                padDp = rest;
            }
            invalidateSelf();
            return;
        }
        trailSpring.animateToFinalPosition(x);
        if (stretch) leadSpring.animateToFinalPosition(x);
        else {
            leadSpring.cancel();
            leadX = x;
        }
    }

    /** The card moved under focus (added, removed or reordered widgets): slide to where it is now. */
    void follow(View card) {
        if (shown && card != null && card.isFocused()) moveTo(card);
    }

    @Override
    public void draw(@NonNull Canvas c) {
        if (!shown || cardW <= 0f) return;
        float pad = padDp * density;
        float left = trailX, right = trailX + cardW;
        if (stretch) {
            float ahead = Math.max(-cardW * MAX_LEAD, Math.min(cardW * MAX_LEAD, leadX - trailX));
            left = Math.min(left, trailX + ahead);
            right = Math.max(right, trailX + ahead + cardW);
        }
        float r = Shapes.TILE_FOCUSED * density + pad;   // concentric with the card
        c.drawRoundRect(left - pad, cardTop - pad, right + pad, cardTop + cardH + pad, r, r, paint);
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
