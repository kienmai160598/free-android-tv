package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Locale;

/**
 * The air quality widget's data: the US AQI and PM2.5 now, and tomorrow's worst hour, from Open-Meteo's
 * air quality API (free, no key) at the weather widget's place ({@link Weather#place}). Kept for half
 * an hour in the launcher's preferences, like the weather.
 */
final class AirQuality {

    /** The AQI bands (US EPA, the same breakpoints as Vietnam's VN_AQI), each up to and including its limit. */
    enum Level {
        GOOD(50), MODERATE(100), SENSITIVE(150), UNHEALTHY(200), VERY_UNHEALTHY(300), HAZARDOUS(Integer.MAX_VALUE);

        final int upTo;

        Level(int upTo) {
            this.upTo = upTo;
        }
    }

    static final long STALE_MS = 30 * 60_000L;
    private static final String PREFS = "air";

    static final class Report {
        final int aqi, pm25, tomorrowMax;
        final long fetchedAt;

        Report(int aqi, int pm25, int tomorrowMax, long fetchedAt) {
            this.aqi = aqi;
            this.pm25 = pm25;
            this.tomorrowMax = tomorrowMax;
            this.fetchedAt = fetchedAt;
        }

        boolean stale(long now) {
            return now - fetchedAt > STALE_MS;
        }
    }

    private AirQuality() {
    }

    static Level level(int aqi) {
        for (Level l : Level.values()) if (aqi <= l.upTo) return l;
        return Level.HAZARDOUS;
    }

    static int label(Level level) {
        switch (level) {
            case GOOD: return R.string.aqi_good;
            case MODERATE: return R.string.aqi_moderate;
            case SENSITIVE: return R.string.aqi_sensitive;
            case UNHEALTHY: return R.string.aqi_unhealthy;
            case VERY_UNHEALTHY: return R.string.aqi_very_unhealthy;
            default: return R.string.aqi_hazardous;
        }
    }

    /** The band's standard colour (green to maroon); the widget harmonises it with the scheme before use. */
    static int color(Level level) {
        switch (level) {
            case GOOD: return 0xFF00E400;
            case MODERATE: return 0xFFFFFF00;
            case SENSITIVE: return 0xFFFF7E00;
            case UNHEALTHY: return 0xFFFF0000;
            case VERY_UNHEALTHY: return 0xFF8F3F97;
            default: return 0xFF7E0023;
        }
    }

    static Report cached(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.contains("fetched")) return null;
        return new Report(p.getInt("aqi", 0), p.getInt("pm25", 0), p.getInt("tomorrow", -1), p.getLong("fetched", 0));
    }

    /** Off the main thread: a fresh report, saved for next time. */
    static Report fetch(Context context, String city) throws IOException, JSONException {
        Weather.Place at = Weather.place(context, city);
        JSONObject f = Weather.get(String.format(Locale.US,
                "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f"
                        + "&current=us_aqi,pm2_5&hourly=us_aqi&timezone=auto&forecast_days=2", at.lat, at.lon));
        JSONObject current = f.getJSONObject("current");
        int aqi = (int) Math.round(current.getDouble("us_aqi"));
        int pm25 = (int) Math.round(current.getDouble("pm2_5"));
        // Tomorrow is the day after the report's own local date (the API answers in the place's time zone).
        String today = current.getString("time").substring(0, 10);
        String tomorrow = LocalDate.parse(today).plusDays(1).toString();
        JSONObject hourly = f.getJSONObject("hourly");
        int worst = worstOn(tomorrow, hourly.getJSONArray("time"), hourly.getJSONArray("us_aqi"));
        long now = System.currentTimeMillis();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("aqi", aqi).putInt("pm25", pm25)
                .putInt("tomorrow", worst).putLong("fetched", now).apply();
        return new Report(aqi, pm25, worst, now);
    }

    /** The highest hourly AQI on that date (yyyy-mm-dd), or -1 when it has none. */
    static int worstOn(String date, JSONArray times, JSONArray values) throws JSONException {
        int worst = -1;
        for (int i = 0; i < times.length() && i < values.length(); i++) {
            if (!times.getString(i).startsWith(date) || values.isNull(i)) continue;
            worst = Math.max(worst, (int) Math.round(values.getDouble(i)));
        }
        return worst;
    }
}
