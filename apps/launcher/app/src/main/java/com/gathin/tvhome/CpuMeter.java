package com.gathin.tvhome;

import android.content.Context;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * How busy the CPU is, as a percentage over the time since the last sample. From /proc/stat where an app
 * may read it; Android 8 and later hide that from apps on most TVs (this Mi Box among them), and then
 * from the cores' cpuidle residency times in sysfs, which apps can still read: busy = wall time minus
 * the time every core spent idle. Kernels without cpuidle (TCL's Realtek G07) leave /proc/uptime, whose
 * second figure is the idle time of all cores. Where apps may read none of them, /proc/stat through adb
 * ({@link AdbCpuFeed}), when the meter has a context. The first sample only sets the baseline.
 */
final class CpuMeter {

    /** -1: no figure yet (first sample). */
    static final int NOT_YET = -1;
    /** -2: the TV hides every source, so there will never be a figure. */
    static final int UNAVAILABLE = -2;

    private long totalPrev = -1, idleStatPrev = -1;
    private boolean procStatDead, cpuidleDead, uptimeDead;
    private long idlePrev = -1, wallPrev = -1;
    private double uptimeIdlePrev = -1, uptimePrev = -1;
    private final Context context;
    private AdbCpuFeed feed;
    private boolean feedStarted, mayPrompt;

    /** Files only. */
    CpuMeter() {
        this(null);
    }

    /** Files, then adb where the TV hides them. */
    CpuMeter(Context context) {
        this.context = context;
    }

    /**
     * Busy percent since the previous call, {@link #NOT_YET} or {@link #UNAVAILABLE}. Reads a few small files:
     * fine on the main thread.
     */
    int sample() {
        if (!procStatDead) {
            int fromStat = fromProcStat();
            if (fromStat != UNAVAILABLE) return fromStat;
            procStatDead = true;
        }
        if (!cpuidleDead) {
            int fromIdle = fromIdle();
            if (fromIdle != UNAVAILABLE) return fromIdle;
            cpuidleDead = true;
        }
        if (!uptimeDead) {
            int fromUptime = fromUptime();
            if (fromUptime != UNAVAILABLE) return fromUptime;
            uptimeDead = true;
        }
        return fromFeed();
    }

    /** Lets the next sample's adb shell put the TV's allow prompt on screen, once. */
    void askToAllow() {
        stop();
        mayPrompt = true;
    }

    /** Ends the adb shell, if one runs; the next sample starts it again. */
    void stop() {
        if (feed != null) feed.stop();
        feedStarted = false;
    }

    private int fromFeed() {
        if (context == null) return UNAVAILABLE;
        if (feed == null) feed = new AdbCpuFeed(context);
        if (!feedStarted) {
            feed.start(mayPrompt);
            feedStarted = true;
            mayPrompt = false;
        }
        if (feed.failed()) return UNAVAILABLE;
        String line = feed.latest();
        return line == null ? NOT_YET : fromStatLine(line);
    }

    private int fromProcStat() {
        return fromStatLine(readFirstLine("/proc/stat"));
    }

    /** Busy percent from /proc/stat's first line, the time every core spent in each state. */
    private int fromStatLine(String s) {
        if (s == null || !s.startsWith("cpu")) return UNAVAILABLE;
        String[] t = s.trim().split("\\s+");
        if (t.length < 5) return UNAVAILABLE;
        try {
            long user = Long.parseLong(t[1]), nice = Long.parseLong(t[2]), sys = Long.parseLong(t[3]);
            long idle = Long.parseLong(t[4]);
            long iowait = t.length > 5 ? Long.parseLong(t[5]) : 0;
            long irq = t.length > 6 ? Long.parseLong(t[6]) : 0;
            long soft = t.length > 7 ? Long.parseLong(t[7]) : 0;
            long total = user + nice + sys + idle + iowait + irq + soft;
            long idleAll = idle + iowait;
            if (totalPrev < 0) {
                totalPrev = total;
                idleStatPrev = idleAll;
                return NOT_YET;
            }
            long dt = total - totalPrev, di = idleAll - idleStatPrev;
            totalPrev = total;
            idleStatPrev = idleAll;
            if (dt <= 0) return NOT_YET;
            return clamp((int) Math.round((dt - di) * 100.0 / dt));
        } catch (NumberFormatException e) {
            return UNAVAILABLE;
        }
    }

    /** Busy percent from cpuidle time (microseconds) across the cores. */
    private int fromIdle() {
        long idle = 0;
        int cores = 0;
        for (int i = 0; i < 8; i++) {
            boolean any = false;
            for (int state = 0; state < 8; state++) {
                String t = readFirstLine("/sys/devices/system/cpu/cpu" + i + "/cpuidle/state" + state + "/time");
                if (t == null) break;
                try {
                    idle += Long.parseLong(t.trim());
                    any = true;
                } catch (NumberFormatException e) {
                    break;
                }
            }
            if (!any) break;
            cores++;
        }
        if (cores == 0) return UNAVAILABLE;
        long wall = SystemClock.elapsedRealtime() * 1000L;
        if (idlePrev < 0) {
            idlePrev = idle;
            wallPrev = wall;
            return NOT_YET;
        }
        long di = idle - idlePrev;
        long dw = (wall - wallPrev) * cores;
        idlePrev = idle;
        wallPrev = wall;
        if (dw <= 0) return NOT_YET;
        return clamp((int) Math.round((dw - di) * 100.0 / dw));
    }

    /** Busy percent from /proc/uptime: seconds since boot, then the idle seconds of all cores together. */
    private int fromUptime() {
        String s = readFirstLine("/proc/uptime");
        String[] t = s == null ? new String[0] : s.trim().split("\\s+");
        if (t.length < 2) return UNAVAILABLE;
        double uptime, idle;
        try {
            uptime = Double.parseDouble(t[0]);
            idle = Double.parseDouble(t[1]);
        } catch (NumberFormatException e) {
            return UNAVAILABLE;
        }
        if (uptimePrev < 0) {
            uptimePrev = uptime;
            uptimeIdlePrev = idle;
            return NOT_YET;
        }
        double wall = (uptime - uptimePrev) * Runtime.getRuntime().availableProcessors();
        double di = idle - uptimeIdlePrev;
        uptimePrev = uptime;
        uptimeIdlePrev = idle;
        if (wall <= 0) return NOT_YET;
        return clamp((int) Math.round((wall - di) * 100.0 / wall));
    }

    private static int clamp(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    private static String readFirstLine(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            return r.readLine();
        } catch (IOException | SecurityException e) {
            return null;
        }
    }
}
