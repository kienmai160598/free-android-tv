package com.gathin.tvhome;

import android.animation.ObjectAnimator;
import android.animation.TimeAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;

import java.util.ArrayList;
import java.util.List;

/**
 * The home screen's background without a wallpaper: the scheme's surface with Material 3 Expressive
 * shapes (a 9-sided cookie, a 4-leaf clover) in tonal container colours. The shapes stay below the
 * header line, behind the opaque dock and grid, and show in the gaps like a wallpaper.
 *
 * With animations on, they move: each shape turns slowly, breathes a little in scale, wanders around the
 * screen on a slow Lissajous path (two sine waves whose periods never line up, so the route does not
 * visibly repeat), and follows the grid's scrolling at its own depth (parallax, {@link #onGridScrolled}). Every shape is
 * its own view on a hardware layer drawn once, so the motion is a GPU transform of a cached texture,
 * never a redraw of the path; it runs only while Home is visible ({@link #start}, {@link #stop}).
 */
final class ExpressiveBackdrop extends FrameLayout {

    /** One full turn of the cookie; the clover turns the other way, slower. Slow enough to feel ambient. */
    private static final long COOKIE_TURN_MS = 90_000, CLOVER_TURN_MS = 140_000;
    /** Breathing: a 3% swell over this long, eased in and out. */
    private static final long BREATH_MS = 7_000;
    private static final float BREATH = 1.03f;

    private final ShapeView cookie, clover;
    private final List<ValueAnimator> motion = new ArrayList<>();
    private final boolean animate;
    /** How far the grid has scrolled, and how much of it each shape follows. */
    private float scrolled;
    private static final float COOKIE_DEPTH = 0.25f, CLOVER_DEPTH = 0.40f;
    private final SpringAnimation cookieParallax, cloverParallax;
    /**
     * Wandering, as shares of the screen: how far each shape strays from its place (x, y) and the
     * periods of its two waves. The y reach keeps the cookie under the header line.
     */
    private static final float COOKIE_REACH_X = 0.26f, COOKIE_REACH_Y = 0.10f, CLOVER_REACH_X = 0.26f, CLOVER_REACH_Y = 0.12f;
    private static final float COOKIE_PERIOD_X_S = 47f, COOKIE_PERIOD_Y_S = 31f, CLOVER_PERIOD_X_S = 59f, CLOVER_PERIOD_Y_S = 37f;
    /** Wander time so far; kept across stop and start so the shapes carry on from where they were. */
    private long wanderMs;
    private TimeAnimator wander;

    ExpressiveBackdrop(Context context, boolean animations, boolean reducedMotion) {
        super(context);
        animate = animations && !reducedMotion;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        cookie = new ShapeView(context, 9, 0.06f, 12f);
        clover = new ShapeView(context, 4, 0.20f, 45f);
        addView(cookie);
        addView(clover);
        cookieParallax = Motion.DEFAULT_SPATIAL.reduced(reducedMotion)
                .apply(new SpringAnimation(cookie, ShapeView.PARALLAX));
        cloverParallax = Motion.DEFAULT_SPATIAL.reduced(reducedMotion)
                .apply(new SpringAnimation(clover, ShapeView.PARALLAX));
    }

    void setPalette(Palette p) {
        setBackgroundColor(p.surface);
        cookie.setColor(p.primaryContainer);
        clover.setColor(p.tertiaryContainer);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Below the header line (30% down): a large cookie right of centre, a clover low on the left. Both
        // places are far enough in that the wander carries them across the screen, not off its edges.
        // Posted: resizing children from inside a layout pass would be dropped until the next one.
        post(() -> {
            float headerLine = h * 0.30f;
            float cookieR = h * 0.40f;
            place(cookie, w * 0.66f, Math.max(headerLine + cookieR, h * 0.80f), cookieR);
            float cloverR = h * 0.24f;
            place(clover, w * 0.30f, h - cloverR * 0.35f, cloverR);
        });
    }

    /**
     * Sizes a shape's view to its circle so it turns about its own centre. Placed by margins, so the
     * translations stay free for the parallax.
     */
    private static void place(View v, float cx, float cy, float r) {
        int size = Math.round(r * 2);
        LayoutParams lp = new LayoutParams(size, size);
        lp.leftMargin = Math.round(cx - r);
        lp.topMargin = Math.round(cy - r);
        v.setLayoutParams(lp);
    }

    /**
     * The grid scrolled by dy px: the shapes follow at a fraction of it, the cookie (further back) less
     * than the clover, on a default spatial spring, so moving between rows of apps carries the whole
     * screen with depth instead of only the tiles.
     */
    void onGridScrolled(int dy) {
        scrolled += dy;
        if (!animate) {
            cookie.setParallax(-scrolled * COOKIE_DEPTH);
            clover.setParallax(-scrolled * CLOVER_DEPTH);
            return;
        }
        cookieParallax.animateToFinalPosition(-scrolled * COOKIE_DEPTH);
        cloverParallax.animateToFinalPosition(-scrolled * CLOVER_DEPTH);
    }

    /** Starts the ambient motion (Home visible). */
    void start() {
        if (!animate || !motion.isEmpty()) return;
        motion.add(turn(cookie, COOKIE_TURN_MS, 1f));
        motion.add(turn(clover, CLOVER_TURN_MS, -1f));
        motion.add(breathe(cookie, 0));
        motion.add(breathe(clover, BREATH_MS / 2));
        long base = wanderMs;
        wander = new TimeAnimator();
        wander.setTimeListener((animation, totalMs, deltaMs) -> {
            wanderMs = base + totalMs;
            float s = wanderMs / 1000f, w = getWidth(), h = getHeight();
            cookie.setWander(wave(s, COOKIE_PERIOD_X_S, 0f) * COOKIE_REACH_X * w, wave(s, COOKIE_PERIOD_Y_S, 0.3f) * COOKIE_REACH_Y * h);
            clover.setWander(wave(s, CLOVER_PERIOD_X_S, 0.6f) * CLOVER_REACH_X * w, wave(s, CLOVER_PERIOD_Y_S, 0.1f) * CLOVER_REACH_Y * h);
        });
        wander.start();
        motion.add(wander);
    }

    /** A sine wave of this period in seconds, phase in turns, from -1 to 1. */
    private static float wave(float seconds, float periodS, float phase) {
        return (float) Math.sin(2 * Math.PI * (seconds / periodS + phase));
    }

    /** Stops it (Home hidden), leaving the shapes where they are. */
    void stop() {
        for (ValueAnimator a : motion) a.cancel();
        motion.clear();
    }

    private static ValueAnimator turn(View v, long ms, float direction) {
        float from = v.getRotation();
        ObjectAnimator a = ObjectAnimator.ofFloat(v, View.ROTATION, from, from + 360f * direction);
        a.setDuration(ms);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setInterpolator(new LinearInterpolator());
        a.start();
        return a;
    }

    private static ValueAnimator breathe(View v, long delay) {
        ValueAnimator a = ValueAnimator.ofFloat(1f, BREATH);
        a.setDuration(BREATH_MS);
        a.setStartDelay(delay);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setInterpolator(new AccelerateDecelerateInterpolator());
        a.addUpdateListener(x -> {
            float s = (float) x.getAnimatedValue();
            v.setScaleX(s);
            v.setScaleY(s);
        });
        a.start();
        return a;
    }

    /** One shape, drawn once into a hardware layer and moved only by transforms. */
    private static final class ShapeView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int lobes;
        private final float depth, rotation;
        /** Two moves add up on the translation: the parallax spring's (y) and the wander (x and y). */
        private float parallaxY, wanderX, wanderY;

        static final FloatPropertyCompat<ShapeView> PARALLAX = new FloatPropertyCompat<ShapeView>("parallax") {
            @Override
            public float getValue(ShapeView v) {
                return v.parallaxY;
            }

            @Override
            public void setValue(ShapeView v, float value) {
                v.setParallax(value);
            }
        };

        void setParallax(float y) {
            parallaxY = y;
            setTranslationY(parallaxY + wanderY);
        }

        void setWander(float x, float y) {
            wanderX = x;
            wanderY = y;
            setTranslationX(wanderX);
            setTranslationY(parallaxY + wanderY);
        }

        ShapeView(Context context, int lobes, float depth, float rotation) {
            super(context);
            this.lobes = lobes;
            this.depth = depth;
            this.rotation = rotation;
            setLayerType(LAYER_TYPE_HARDWARE, null);
        }

        void setColor(int color) {
            fill.setColor(color);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float r = getWidth() / 2f;
            shapePath(path, r, r, r, lobes, depth, rotation);
            c.drawPath(path, fill);
        }
    }

    /**
     * Writes a rounded polygon in the style of the M3E shape library into path (rewound first): a circle
     * whose radius waves `lobes` times around, r(t) = R (1 + depth cos(lobes (t - rotation))). Shallow
     * waves give a cookie or a sunny burst, deep ones a clover.
     */
    static void shapePath(Path path, float cx, float cy, float radius, int lobes, float depth, float rotationDeg) {
        path.rewind();
        int steps = 180;
        double rot = Math.toRadians(rotationDeg);
        for (int i = 0; i <= steps; i++) {
            double t = Math.PI * 2 * i / steps;
            double r = radius * (1 - depth + depth * Math.cos(lobes * (t - rot)));
            float x = (float) (cx + r * Math.cos(t)), y = (float) (cy + r * Math.sin(t));
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
    }
}
