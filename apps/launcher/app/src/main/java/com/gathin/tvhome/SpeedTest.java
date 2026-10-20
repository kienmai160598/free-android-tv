package com.gathin.tvhome;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * The Wi-Fi menu's speed test, on Cloudflare's speed endpoint: latency as the best of a few empty
 * requests, then downloads of {@link #BYTES} one after another for {@link #MAX_MS}, reporting the speed as
 * it goes. The longer run lets the speed settle past TCP's slow start and the Wi-Fi's own ramp-up. Runs only when asked, on its own thread; results come back on the main thread.
 */
final class SpeedTest {

    interface Listener {
        /** The download so far, in Mbit/s. */
        void progress(int mbps);

        void done(int mbps, int pingMs);

        void failed();
    }

    private static final String URL_BASE = "https://speed.cloudflare.com/__down?bytes=";
    private static final long BYTES = 25_000_000L;
    private static final long MAX_MS = 15_000;
    private static final int PINGS = 3, TIMEOUT_MS = 6000;

    /** Netflix's recommendations: 4K needs 15 Mbit/s, HD 5, SD 3. */
    enum Verdict { UHD, HD, SD, SLOW }

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled;

    static Verdict verdict(int mbps) {
        if (mbps >= 15) return Verdict.UHD;
        if (mbps >= 5) return Verdict.HD;
        if (mbps >= 3) return Verdict.SD;
        return Verdict.SLOW;
    }

    void cancel() {
        cancelled = true;
    }

    void start(Listener listener) {
        new Thread(() -> {
            try {
                int ping = Integer.MAX_VALUE;
                for (int i = 0; i < PINGS && !cancelled; i++) {
                    long t0 = SystemClock.elapsedRealtime();
                    drain(open(URL_BASE + "0"), null);
                    ping = (int) Math.min(ping, SystemClock.elapsedRealtime() - t0);
                }
                long[] got = {0};
                long t0 = SystemClock.elapsedRealtime();
                while (!cancelled && SystemClock.elapsedRealtime() - t0 < MAX_MS) {
                    long before = got[0];
                    drain(open(URL_BASE + BYTES), bytes -> {
                        got[0] = before + bytes;
                        long ms = SystemClock.elapsedRealtime() - t0;
                        if (ms > 250) {
                            int mbps = mbps(got[0], ms);
                            main.post(() -> {
                                if (!cancelled) listener.progress(mbps);
                            });
                        }
                        return !cancelled && ms < MAX_MS;
                    });
                }
                int mbps = mbps(got[0], SystemClock.elapsedRealtime() - t0);
                int pingMs = ping;
                main.post(() -> {
                    if (!cancelled) listener.done(mbps, pingMs);
                });
            } catch (IOException e) {
                main.post(() -> {
                    if (!cancelled) listener.failed();
                });
            }
        }, "speedtest").start();
    }

    private static int mbps(long bytes, long ms) {
        return ms <= 0 ? 0 : (int) Math.round(bytes * 8.0 / ms / 1000.0);
    }

    private interface Progress {
        /** Total bytes so far; false stops the download. */
        boolean read(long total);
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setUseCaches(false);
        c.setRequestProperty("User-Agent", "tvhome-launcher");
        return c;
    }

    private static void drain(HttpURLConnection c, Progress progress) throws IOException {
        try (InputStream in = c.getInputStream()) {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            byte[] buf = new byte[64 * 1024];
            long total = 0;
            for (int n; (n = in.read(buf)) > 0; ) {
                total += n;
                if (progress != null && !progress.read(total)) break;
            }
        } finally {
            c.disconnect();
        }
    }
}
