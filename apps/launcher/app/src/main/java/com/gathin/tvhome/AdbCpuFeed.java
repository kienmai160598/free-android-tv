package com.gathin.tvhome;

import android.content.Context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The first line of /proc/stat every few seconds through {@link LocalAdb}, for TVs that hide every CPU
 * figure from apps (TCL's Realtek G07: no /proc/stat, no cpuidle, no /proc/uptime). One shell runs for as
 * long as Home shows, so the launcher signs in once, not once per sample. The TV asks to allow the
 * launcher's key only when the owner turns the CPU line on in the card's settings; until it is allowed,
 * or with adb over the network off, there is no figure.
 */
final class AdbCpuFeed {

    private static final String COMMAND = "while :; do head -n 1 /proc/stat; sleep 3; done";

    private final LocalAdb adb;
    /** The running shell; a thread whose session is no longer this one leaves the figures alone. */
    private volatile LocalAdb.Session session;
    private volatile String latest;
    private volatile boolean failed;

    AdbCpuFeed(Context context) {
        adb = new LocalAdb(context);
    }

    /**
     * Starts the shell unless it runs; a feed that failed tries again on the next start. mayPrompt lets the TV
     * ask to allow the key.
     */
    void start(boolean mayPrompt) {
        if (session != null && !failed) return;
        stop();
        failed = false;
        latest = null;
        LocalAdb.Session running = adb.new Session();
        session = running;
        new Thread(() -> {
            StringBuilder pending = new StringBuilder();
            try {
                running.run(COMMAND, mayPrompt, chunk -> {
                    if (session == running) keepLast(pending, chunk);
                });
            } catch (IOException e) {
                // Off, not allowed, or closed by stop(): no figure until the next start.
            }
            if (session == running) failed = true;
        }, "cpu-feed").start();
    }

    void stop() {
        if (session == null) return;
        session.close();
        session = null;
    }

    /** The newest whole "cpu " line, or null before the first. */
    String latest() {
        return latest;
    }

    boolean failed() {
        return failed;
    }

    private void keepLast(StringBuilder pending, byte[] chunk) {
        pending.append(new String(chunk, StandardCharsets.US_ASCII));
        int end;
        while ((end = pending.indexOf("\n")) >= 0) {
            String line = pending.substring(0, end).trim();
            pending.delete(0, end + 1);
            if (line.startsWith("cpu ")) latest = line;
        }
    }
}
