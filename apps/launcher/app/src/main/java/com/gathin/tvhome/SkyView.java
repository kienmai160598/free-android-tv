package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Calendar;

/**
 * A full-screen gradient that follows the clock: night blues, a warm horizon at sunset, a soft
 * glow that travels with the sun. Off by default (config.xml, sky_intensity = 0); a theme turns
 * it on with {@code "sky_intensity": 60}, Launcher settings with the Sky background.
 *
 * It follows the weather widget's last report too: cloud, rain or snow tint the gradient (Sky.weathered),
 * and the weather is drawn over it in M3 Expressive shapes (WeatherShapes). A report older than a few hours
 * counts as no report, a clear sky.
 *
 * Like the ambient glow, the backdrop is one small bitmap (480x270) stretched over the screen:
 * a gradient repainted at full resolution every minute is exactly the CPU cost the ambient glow
 * was rewritten to avoid on weak GPUs. It is redrawn once a minute and never animated, so the
 * cost is a single full-screen draw per minute.
 */
public class SkyView extends View {

    /** Full-screen and faded with setAlpha: without this every redraw of Home goes through an offscreen layer. */
    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private static final int TEX_W = 480;
    private static final int TEX_H = 270;

    /** Opacity of the whole backdrop, 0 to 255, from sky_intensity. */
    private int intensity;
    private int lastMinute = -1;
    private Bitmap texture;
    private BitmapDrawable drawable;
    private static final long WEATHER_KEPT_MS = 3 * 60 * 60_000L;
    /** Day for the sun and moon when the report is stale: 06:00 to 18:30. */
    private static final int DAY_FROM = 360, DAY_UNTIL = 1110;
    private Weather.Sky weather = Weather.Sky.CLEAR;
    private boolean day = true;
    private final WeatherShapes shapes = new WeatherShapes();
    private final Paint sunPaint = fill(0xD9FFD27A), moonPaint = fill(0xE6EDE9FF), starPaint = fill(0x99FFFFFF);
    private final Paint cloudPaint = fill(0x52FFFFFF), boltPaint = fill(0xD9FFE27A), snowPaint = fill(0xB3FFFFFF);
    private final Paint fogPaint = fill(0x29FFFFFF), rainPaint = stroke(0x80C8DCFF);

    public SkyView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /** {@code percent} of sky_intensity: 0 hides the backdrop, 100 covers the wallpaper. */
    public void setIntensity(int percent) {
        intensity = Math.max(0, Math.min(100, percent));
        setAlpha(intensity / 100f);
        setVisibility(intensity == 0 ? GONE : VISIBLE);
        if (intensity > 0) render(true);
    }

    /** Picks up a new time of day; cheap and safe to call every minute. */
    public void update() {
        if (intensity == 0 || getVisibility() != VISIBLE) return;
        render(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (intensity > 0) render(true);
    }

    private static Paint fill(int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        return paint;
    }

    private static Paint stroke(int color) {
        Paint paint = fill(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        return paint;
    }

    private void render(boolean force) {
        Calendar now = Calendar.getInstance();
        int minute = Sky.minuteOfDay(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
        if (!force && minute == lastMinute) return;
        lastMinute = minute;
        readWeather(minute);

        Sky.Palette sky = Sky.weathered(Sky.at(minute), weather);
        if (texture == null) texture = Bitmap.createBitmap(TEX_W, TEX_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(texture);
        canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0f, 0f, 0f, TEX_H,
                new int[]{sky.top, sky.mid, sky.bottom},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, TEX_W, TEX_H, paint);

        if (sky.sun > 0) {
            // A warm core low on the horizon, where the sun (or its afterglow) sits.
            int warm = Sky.lerpColor(sky.bottom, 0xFFFFD9A0, 0.65f);
            paint.setShader(new RadialGradient(
                    sky.sunX * TEX_W, TEX_H * 0.94f, TEX_W * 0.42f,
                    new int[]{(sky.sun << 24) | (warm & 0x00FFFFFF), warm & 0x00FFFFFF},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0f, 0f, TEX_W, TEX_H, paint);
        }

        if (drawable == null) {
            drawable = new BitmapDrawable(getResources(), texture);
            drawable.setGravity(Gravity.FILL);
            setBackground(drawable);
        }
        invalidate();
    }

    /** The widget's last report, if recent; its day or night while fresh, else the clock's. */
    private void readWeather(int minute) {
        Weather.Report report = Weather.cached(getContext());
        long now = System.currentTimeMillis();
        boolean known = report != null && now - report.fetchedAt < WEATHER_KEPT_MS;
        Weather.Sky nowWeather = known ? Weather.sky(report.code) : Weather.Sky.CLEAR;
        boolean nowDay = known && !report.stale(now) ? report.day : minute >= DAY_FROM && minute < DAY_UNTIL;
        if (nowWeather == weather && nowDay == day) return;
        weather = nowWeather;
        day = nowDay;
        shapes.build(getWidth(), getHeight(), weather, day);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        shapes.build(w, h, weather, day);
        rainPaint.setStrokeWidth(h * 0.006f);
        boltPaint.setPathEffect(new CornerPathEffect(h * 0.006f));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPath(shapes.fog, fogPaint);
        canvas.drawPath(shapes.stars, starPaint);
        canvas.drawPath(shapes.sun, sunPaint);
        canvas.drawPath(shapes.moon, moonPaint);
        canvas.drawPath(shapes.rain, rainPaint);
        canvas.drawPath(shapes.snow, snowPaint);
        canvas.drawPath(shapes.clouds, cloudPaint);
        canvas.drawPath(shapes.bolt, boltPaint);
    }
}
