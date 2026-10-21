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
import android.view.animation.Interpolator;
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
 * its own view on a hardware layer, so the motion is a GPU transform of a cached texture; it runs only
 * while Home is visible ({@link #start}, {@link #stop}).
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

    private static final Form[] COOKIE_FORMS = {
            new Form(9, 0.06f, 12f), new Form(12, 0.05f, 0f),
            new Form(8, 0.09f, 22.5f), new Form(7, 0.07f, 0f)};
    private static final Form[] CLOVER_FORMS = {
            new Form(4, 0.20f, 45f), new Form(8, 0.13f, 22.5f),
            new Form(6, 0.10f, 0f), new Form(5, 0.16f, 18f)};
    private static final long MORPH_HOLD_MS = 4_000;
    private static final Motion.Spec MORPH_SPRING = Motion.SLOW_SPATIAL;
    private final boolean morph;
    private final Interpolator morphCurve = MORPH_SPRING.interpolator();
    private final long morphMs = MORPH_SPRING.durationMs();

    ExpressiveBackdrop(Context context, boolean animations, boolean reducedMotion,
            boolean advancedEffects) {
        super(context);
        animate = animations && !reducedMotion;
        morph = animate && advancedEffects;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        cookie = new ShapeView(context, COOKIE_FORMS);
        clover = new ShapeView(context, CLOVER_FORMS);
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
            if (morph) morphBoth(wanderMs);
        });
        wander.start();
        motion.add(wander);
    }

    private void morphBoth(long ms) {
        long cycleMs = MORPH_HOLD_MS + morphMs;
        morphAt(cookie, ms, cycleMs);
        morphAt(clover, ms + cycleMs / 2, cycleMs);
    }

    private void morphAt(ShapeView shape, long ms, long cycleMs) {
        long step = ms / cycleMs;
        long intoMorph = ms % cycleMs - MORPH_HOLD_MS;
        float progress = intoMorph <= 0
                ? 0f
                : morphCurve.getInterpolation(Math.min(1f, intoMorph / (float) morphMs));
        shape.setMorph(step, progress);
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
        private static final int MORPH_FRAMES = 48;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Form[] forms;
        private Form from, to;
        private float morphProgress;
        private int drawnFrame;
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

        ShapeView(Context context, Form[] forms) {
            super(context);
            this.forms = forms;
            from = forms[0];
            to = forms[0];
            setLayerType(LAYER_TYPE_HARDWARE, null);
        }

        void setMorph(long step, float progress) {
            Form stepFrom = forms[(int) (step % forms.length)];
            Form stepTo = forms[(int) ((step + 1) % forms.length)];
            int frame = Math.round(progress * MORPH_FRAMES);
            if (stepFrom == from && stepTo == to && frame == drawnFrame) return;
            from = stepFrom;
            to = stepTo;
            morphProgress = progress;
            drawnFrame = frame;
            invalidate();
        }

        void setColor(int color) {
            fill.setColor(color);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float r = getWidth() / 2f;
            morphPath(path, r, r, r, from, to, morphProgress);
            c.drawPath(path, fill);
        }
    }

    static final class Form {
        final int lobes;
        final float depth, rotationDeg;

        Form(int lobes, float depth, float rotationDeg) {
            this.lobes = lobes;
            this.depth = depth;
            this.rotationDeg = rotationDeg;
        }

        double scaleAt(double angle) {
            return waveScale(lobes, depth, Math.toRadians(rotationDeg), angle);
        }
    }

    static void morphPath(Path path, float cx, float cy, float radius, Form from, Form to,
            float progress) {
        path.rewind();
        for (int i = 0; i <= OUTLINE_STEPS; i++) {
            double t = Math.PI * 2 * i / OUTLINE_STEPS;
            double fromScale = from.scaleAt(t);
            double scale = fromScale + (to.scaleAt(t) - fromScale) * progress;
            double r = radius * Math.max(0, scale);
            float x = (float) (cx + r * Math.cos(t)), y = (float) (cy + r * Math.sin(t));
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
    }

    /**
     * Writes a rounded polygon in the style of the M3E shape library into path (rewound first): a circle
     * whose radius waves `lobes` times around, r(t) = R (1 + depth cos(lobes (t - rotation))). Shallow
     * waves give a cookie or a sunny burst, deep ones a clover.
     */
    static void shapePath(Path path, float cx, float cy, float radius, int lobes, float depth, float rotationDeg) {
        path.rewind();
        double rot = Math.toRadians(rotationDeg);
        for (int i = 0; i <= OUTLINE_STEPS; i++) {
            double t = Math.PI * 2 * i / OUTLINE_STEPS;
            double r = radius * waveScale(lobes, depth, rot, t);
            float x = (float) (cx + r * Math.cos(t)), y = (float) (cy + r * Math.sin(t));
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
    }

    private static final int OUTLINE_STEPS = 180;

    private static double waveScale(int lobes, float depth, double rotation, double angle) {
        return 1 - depth + depth * Math.cos(lobes * (angle - rotation));
    }
}
