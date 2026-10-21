package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Random;

/**
 * Screensaver: the launcher's thin clock and date on black, in the style chosen in Launcher settings: over
 * Home's M3E shapes, over a slow ambient glow, alone and dim, or larger. Every minute the clock drifts to a
 * new spot and the glow shifts colour, so nothing burns in. No network, no images: cheap enough for a 1 GB TV.
 */
public class ClockDream extends DreamService {

    private static final long MOVE_MS = 60_000L;
    /** The shapes at part strength on black, the dim style's clock and date: nothing bright stays put. */
    private static final float SHAPES_ALPHA = 0.55f, MINIMAL_ALPHA = 0.5f;
    private static final float LARGE_SCALE = 1.5f;
    /** Glow colours from the scheme; the glow is drawn at partial alpha on black. */
    private int[] palette;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private View content;
    private GradientDrawable glow;
    private int colorIndex;
    private int glowColor;
    private String style;
    private ExpressiveBackdrop shapes;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            moveClock();
            shiftGlow();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_clock);

        content = findViewById(R.id.dreamContent);
        View ambient = findViewById(R.id.dreamAmbient);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        // The launcher's dynamic colours: a primary clock, onSurfaceVariant date, and a glow that drifts
        // through the scheme's accent roles instead of a fixed rainbow.
        // Always the dark scheme: the screensaver sits on black so nothing burns in. Seeded like Home's when
        // Home has not run in this process yet.
        Config config = Config.load(this);
        Palette home = Palette.current();
        Palette p = Palette.dark(home != null ? home : Palette.fromBackground(config.backgroundColor));
        palette = new int[]{p.primary, p.tertiary, p.secondary};
        M3Skin.apply(content, p, true, false);
        colorClock(content, p);
        colorIndex = random.nextInt(palette.length);
        setGlow(palette[colorIndex]);

        style = Screensaver.style(getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE));
        if (Screensaver.SHAPES.equals(style)) {
            shapes = new ExpressiveBackdrop(this, config.animations, config.reducedMotion,
                    config.advancedEffects);
            shapes.setPalette(p);
            shapes.setBackgroundColor(0xFF000000);
            shapes.setAlpha(SHAPES_ALPHA);
            ((ViewGroup) findViewById(R.id.dreamRoot)).addView(shapes, 0);
            ambient.setVisibility(View.GONE);
        } else if (Screensaver.MINIMAL.equals(style)) {
            ambient.setVisibility(View.GONE);
            content.setAlpha(MINIMAL_ALPHA);
        } else if (Screensaver.LARGE.equals(style)) {
            scaleText(content, LARGE_SCALE);
        }
    }

    /** Every text in the clock {@code factor} times as large, so the layout keeps its proportions. */
    private static void scaleText(View v, float factor) {
        if (v instanceof TextView) {
            TextView text = (TextView) v;
            text.setTextSize(TypedValue.COMPLEX_UNIT_PX, text.getTextSize() * factor);
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) scaleText(g.getChildAt(i), factor);
        }
    }

    /** The clock's resting opacity: dimmer in the minimal style. */
    private float clockAlpha() {
        return Screensaver.MINIMAL.equals(style) ? MINIMAL_ALPHA : 1f;
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        content.setAlpha(0f);
        content.post(() -> {
            placeClock();
            content.animate().alpha(clockAlpha()).setDuration(1200).start();
        });
        if (shapes != null) shapes.start();
        handler.postDelayed(tick, MOVE_MS);
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacks(tick);
        if (shapes != null) shapes.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        if (shapes != null) shapes.stop();
        super.onDetachedFromWindow();
    }

    /** The big time (the largest text) in primary, as on the home screen. */
    private static void colorClock(View v, Palette p) {
        if (v instanceof TextClock && ((TextView) v).getTextSize() > 150) {
            ((TextView) v).setTextColor(p.primary);
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) colorClock(g.getChildAt(i), p);
        }
    }

    private void moveClock() {
        content.animate().alpha(0f).setDuration(900).withEndAction(() -> {
            placeClock();
            content.animate().alpha(clockAlpha()).setDuration(1200).start();
        }).start();
    }

    /** A random spot that keeps the whole clock on screen, with the glow centred behind it. */
    private void placeClock() {
        View parent = (View) content.getParent();
        int maxX = Math.max(0, parent.getWidth() - content.getWidth());
        int maxY = Math.max(0, parent.getHeight() - content.getHeight());
        float x = maxX * (0.1f + 0.8f * random.nextFloat());
        float y = maxY * (0.1f + 0.8f * random.nextFloat());
        content.setX(x);
        content.setY(y);
        if (parent.getWidth() > 0 && parent.getHeight() > 0) {
            glow.setGradientCenter((x + content.getWidth() / 2f) / parent.getWidth(),
                    (y + content.getHeight() / 2f) / parent.getHeight());
        }
    }

    private void shiftGlow() {
        if (!Screensaver.GLOW.equals(style) && !Screensaver.LARGE.equals(style)) return;   // no glow on screen
        int from = glowColor;
        colorIndex = (colorIndex + 1) % palette.length;
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), from, palette[colorIndex]);
        anim.setDuration(8000);
        anim.addUpdateListener(a -> setGlow((int) a.getAnimatedValue()));
        anim.start();
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}
