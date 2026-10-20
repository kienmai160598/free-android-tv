package com.gathin.tvhome;

import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * How busy the CPU is, as a percentage over the time since the last sample. From /proc/stat where an app
 * may read it; Android 8 and later hide that from apps on most TVs (this Mi Box among them), and then
 * from the cores' cpuidle residency times in sysfs, which apps can still read: busy = wall time minus
 * the time every core spent idle. The first sample only sets the baseline.
 */
final class CpuMeter {

    /** -1: no figure yet (first sample). */
    static final int NOT_YET = -1;
    private static final int UNREADABLE = -2;

    private long totalPrev = -1, idleStatPrev = -1;
    private boolean procStatDead;
    private long idlePrev = -1, wallPrev = -1;

    /** Busy percent since the previous call, or {@link #NOT_YET}. Reads a few small files: fine on the main thread. */
    int sample() {
        if (!procStatDead) {
            int fromStat = fromProcStat();
            if (fromStat != UNREADABLE) return fromStat;
            procStatDead = true;
        }
        return fromIdle();
    }

    private int fromProcStat() {
        String s = readFirstLine("/proc/stat");
        if (s == null || !s.startsWith("cpu")) return UNREADABLE;
        String[] t = s.trim().split("\\s+");
        if (t.length < 5) return UNREADABLE;
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
            return UNREADABLE;
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
        if (cores == 0) return NOT_YET;
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
