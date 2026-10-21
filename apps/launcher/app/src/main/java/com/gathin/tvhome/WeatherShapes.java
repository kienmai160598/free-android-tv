package com.gathin.tvhome;

import android.graphics.Path;
import android.graphics.RectF;

/**
 * The weather drawn on the sky in the M3 Expressive shape language: a sunny burst for the sun, a crescent and
 * four-point sparkles at night, puffy clouds of overlapping cookies, rounded rain strokes, a bolt, six-lobed
 * flakes and fog bands. Built into paths when the weather, the time of day or the size changes; SkyView fills
 * them once a minute at most, nothing moves.
 *
 * Places are shares of the screen, picked for Home's layout: the sky shows above the widgets (the header
 * leaves room right of the greeting), in the gap above the dock and along the bottom edge.
 */
final class WeatherShapes {
    final Path sun = new Path(), moon = new Path(), stars = new Path(), clouds = new Path();
    final Path rain = new Path(), bolt = new Path(), snow = new Path(), fog = new Path();

    /** Clouds as centre x, centre y and radius, in shares of the height except x (of the width). */
    private static final float[][] ONE_CLOUD = {{0.70f, 0.15f, 0.075f}};
    private static final float[][] CLOUDS = {{0.58f, 0.12f, 0.07f}, {0.80f, 0.19f, 0.06f}, {0.28f, 0.61f, 0.05f},
            {0.90f, 0.62f, 0.045f}};
    private static final float[][] STARS = {{0.52f, 0.06f}, {0.60f, 0.21f}, {0.74f, 0.05f}, {0.86f, 0.24f},
            {0.95f, 0.08f}, {0.18f, 0.60f}, {0.47f, 0.63f}, {0.72f, 0.58f}, {0.05f, 0.95f}, {0.62f, 0.96f}};
    private static final float SUN_X = 0.62f, SUN_Y = 0.12f, SUN_R = 0.10f;

    private final Path piece = new Path();

    void build(int w, int h, Weather.Sky weather, boolean day) {
        for (Path p : new Path[]{sun, moon, stars, clouds, rain, bolt, snow, fog}) p.rewind();
        if (w <= 0 || h <= 0) return;
        boolean clearish = weather == Weather.Sky.CLEAR || weather == Weather.Sky.PARTLY;
        if (clearish && day) addSun(w, h);
        if (clearish && !day) addNight(w, h);
        if (weather == Weather.Sky.FOG) addFog(w, h);
        if (weather == Weather.Sky.PARTLY) addClouds(w, h, ONE_CLOUD, weather);
        if (weather == Weather.Sky.CLOUDY || weather == Weather.Sky.RAIN || weather == Weather.Sky.THUNDER
                || weather == Weather.Sky.SNOW) {
            addClouds(w, h, CLOUDS, weather);
        }
    }

    private void addSun(int w, int h) {
        ExpressiveBackdrop.shapePath(sun, SUN_X * w, SUN_Y * h, SUN_R * h, 12, 0.07f, 0f);
    }

    /** A crescent where the sun would be, and sparkles in the open sky. */
    private void addNight(int w, int h) {
        float cx = SUN_X * w, cy = SUN_Y * h, r = SUN_R * h * 0.75f;
        moon.addCircle(cx, cy, r, Path.Direction.CW);
        piece.rewind();
        piece.addCircle(cx + r * 0.45f, cy - r * 0.30f, r * 0.85f, Path.Direction.CW);
        moon.op(piece, Path.Op.DIFFERENCE);
        for (int i = 0; i < STARS.length; i++) {
            float size = h * (i % 3 == 0 ? 0.016f : 0.010f);
            ExpressiveBackdrop.shapePath(piece, STARS[i][0] * w, STARS[i][1] * h, size, 4, 0.55f, 45f);
            stars.op(piece, Path.Op.UNION);
        }
    }

    private void addFog(int w, int h) {
        float band = h * 0.026f;
        float[][] bands = {{0.50f, 0.08f, 0.30f}, {0.58f, 0.15f, 0.36f}, {0.52f, 0.22f, 0.26f},
                {0.20f, 0.60f, 0.30f}, {0.62f, 0.63f, 0.34f}};
        for (float[] b : bands) {
            RectF pill = new RectF(b[0] * w, b[1] * h - band / 2, (b[0] + b[2]) * w, b[1] * h + band / 2);
            fog.addRoundRect(pill, band / 2, band / 2, Path.Direction.CW);
        }
    }

    private void addClouds(int w, int h, float[][] places, Weather.Sky weather) {
        for (int i = 0; i < places.length; i++) {
            float cx = places[i][0] * w, cy = places[i][1] * h, r = places[i][2] * h;
            addCloud(cx, cy, r);
            if (weather == Weather.Sky.RAIN || weather == Weather.Sky.THUNDER) addRain(cx, cy, r);
            if (weather == Weather.Sky.SNOW) addSnow(cx, cy, r);
            if (weather == Weather.Sky.THUNDER && i == 0) addBolt(cx, cy, r);
        }
    }

    /** Three cookie puffs on a rounded base, merged into one outline. */
    private void addCloud(float cx, float cy, float r) {
        float[][] puffs = {{0f, -0.25f, 1f}, {-0.95f, 0.15f, 0.68f}, {0.90f, 0.10f, 0.78f}};
        for (float[] puff : puffs) {
            ExpressiveBackdrop.shapePath(piece, cx + puff[0] * r, cy + puff[1] * r, puff[2] * r, 9, 0.05f, 20f);
            clouds.op(piece, Path.Op.UNION);
        }
        piece.rewind();
        piece.addRoundRect(new RectF(cx - 1.55f * r, cy, cx + 1.6f * r, cy + 0.72f * r), 0.36f * r, 0.36f * r,
                Path.Direction.CW);
        clouds.op(piece, Path.Op.UNION);
    }

    /** Slanted strokes under the cloud, alternately long and short; SkyView strokes them with round caps. */
    private void addRain(float cx, float cy, float r) {
        for (int i = 0; i < 5; i++) {
            float x = cx - 1.1f * r + i * 0.55f * r, top = cy + 0.95f * r + (i % 2) * 0.25f * r;
            float length = (i % 2 == 0 ? 0.55f : 0.40f) * r;
            rain.moveTo(x, top);
            rain.lineTo(x - 0.18f * length, top + length);
        }
    }

    private void addSnow(float cx, float cy, float r) {
        for (int i = 0; i < 4; i++) {
            float x = cx - 0.9f * r + i * 0.6f * r, y = cy + 1.05f * r + (i % 2) * 0.35f * r;
            ExpressiveBackdrop.shapePath(piece, x, y, 0.13f * r, 6, 0.35f, 0f);
            snow.op(piece, Path.Op.UNION);
        }
    }

    private void addBolt(float cx, float cy, float r) {
        float[][] points = {{0.10f, 0.70f}, {-0.30f, 1.30f}, {0.00f, 1.30f}, {-0.18f, 1.85f}, {0.38f, 1.12f},
                {0.08f, 1.12f}, {0.30f, 0.70f}};
        bolt.moveTo(cx + points[0][0] * r, cy + points[0][1] * r);
        for (int i = 1; i < points.length; i++) bolt.lineTo(cx + points[i][0] * r, cy + points[i][1] * r);
        bolt.close();
    }
}
