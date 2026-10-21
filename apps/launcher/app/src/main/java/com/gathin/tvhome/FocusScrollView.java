package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ScrollView;

import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;

public final class FocusScrollView extends ScrollView {

    private static final FloatPropertyCompat<FocusScrollView> SCROLL_Y =
            new FloatPropertyCompat<FocusScrollView>("scrollY") {
                @Override
                public float getValue(FocusScrollView view) {
                    return view.getScrollY();
                }

                @Override
                public void setValue(FocusScrollView view, float value) {
                    view.scrollTo(view.getScrollX(), Math.round(value));
                }
            };

    private final Rect focusedBounds = new Rect();
    private SpringAnimation scrollSpring;
    private boolean animations = true;

    public FocusScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSmoothScrollingEnabled(false);
    }

    void setMotion(boolean animations, boolean reducedMotion) {
        this.animations = animations;
        scrollSpring = Motion.DEFAULT_SPATIAL.reduced(reducedMotion)
                .apply(new SpringAnimation(this, SCROLL_Y));
    }

    @Override
    public boolean executeKeyEvent(KeyEvent event) {
        int direction = verticalDirection(event);
        if (direction == 0) return super.executeKeyEvent(event);

        View focused = findFocus();
        View next = FocusFinder.getInstance().findNextFocus(this, focused, direction);
        if (next == null) return super.executeKeyEvent(event);

        if (event.getAction() == KeyEvent.ACTION_DOWN) next.requestFocus(direction);
        return true;
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        centre(focused);
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        return 0;
    }

    private void centre(View focused) {
        if (getChildCount() == 0 || !isLaidOut()) return;

        focused.getDrawingRect(focusedBounds);
        offsetDescendantRectToMyCoords(focused, focusedBounds);
        int visibleHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        int maxScroll = Math.max(0, getChildAt(0).getHeight() - visibleHeight);
        int centred = focusedBounds.centerY() - getPaddingTop() - visibleHeight / 2;
        int target = Math.max(0, Math.min(maxScroll, centred));
        if (target == getScrollY() && (scrollSpring == null || !scrollSpring.isRunning())) return;

        if (!animations || scrollSpring == null) {
            scrollTo(getScrollX(), target);
            return;
        }

        scrollSpring.animateToFinalPosition(target);
    }

    private static int verticalDirection(KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return View.FOCUS_DOWN;
            case KeyEvent.KEYCODE_DPAD_UP:
                return View.FOCUS_UP;
            default:
                return 0;
        }
    }
}
