package com.gathin.tvhome;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The apps with a live process, for the running apps panel, read and stopped through {@link LocalAdb}:
 * the process list (ps) for what runs and how much memory it holds, the recent tasks for the order. Only
 * apps with a launcher entry count; the caller decides which (the launcher itself and the keyboard stay out).
 */
final class BackgroundApps {

    static final class App {
        final String pkg;
        final long memoryKb;
        /** Place among the recent tasks, 0 the most recent; {@link Integer#MAX_VALUE} for none (a service). */
        final int recency;

        App(String pkg, long memoryKb, int recency) {
            this.pkg = pkg;
            this.memoryKb = memoryKb;
            this.recency = recency;
        }
    }

    private static final String SPLIT = "--recents--";
    static final String LIST_COMMAND = "ps -A -o RSS,NAME; echo " + SPLIT + "; dumpsys activity recents | grep 'Recent #'";
    private static final Pattern PROCESS = Pattern.compile("^\\s*(\\d+)\\s+([A-Za-z][\\w.]*)(?::\\S*)?\\s*$");
    private static final Pattern TASK = Pattern.compile("A=\\d+:([\\w.]+)");
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][\\w.]*");

    private BackgroundApps() {
    }

    static List<App> list(LocalAdb adb, Predicate<String> listed) throws IOException {
        return parse(adb.shell(LIST_COMMAND), listed);
    }

    /** Force-stops the apps, as `am force-stop` would from a computer. */
    static void stop(LocalAdb adb, Collection<String> packages) throws IOException {
        StringBuilder command = new StringBuilder();
        for (String pkg : packages) {
            if (!PACKAGE.matcher(pkg).matches()) continue;   // it goes into a shell line
            if (command.length() > 0) command.append("; ");
            command.append("am force-stop ").append(pkg);
        }
        if (command.length() > 0) adb.shell(command.toString());
    }

    /**
     * Reads {@link #LIST_COMMAND}'s output: memory summed over each app's processes ("pkg" and "pkg:sub"),
     * most recent task first, then the rest by memory.
     */
    static List<App> parse(String output, Predicate<String> listed) {
        int split = output.indexOf(SPLIT);
        String ps = split < 0 ? output : output.substring(0, split);
        String recents = split < 0 ? "" : output.substring(split + SPLIT.length());
        Map<String, Long> memory = new HashMap<>();
        for (String line : ps.split("\n")) {
            Matcher m = PROCESS.matcher(line);
            if (!m.matches() || !listed.test(m.group(2))) continue;
            memory.merge(m.group(2), Long.parseLong(m.group(1)), Long::sum);
        }
        List<String> tasks = new ArrayList<>();
        Matcher t = TASK.matcher(recents);
        while (t.find()) tasks.add(t.group(1));
        List<App> apps = new ArrayList<>();
        for (Map.Entry<String, Long> e : memory.entrySet()) {
            apps.add(new App(e.getKey(), e.getValue(), recency(e.getKey(), tasks)));
        }
        apps.sort((a, b) -> a.recency != b.recency ? Integer.compare(a.recency, b.recency)
                : Long.compare(b.memoryKb, a.memoryKb));
        return apps;
    }

    /** A task's affinity is the package or starts with it ("com.google.android.youtube.tv.main"). */
    private static int recency(String pkg, List<String> tasks) {
        for (int i = 0; i < tasks.size(); i++) {
            String affinity = tasks.get(i);
            if (affinity.equals(pkg) || affinity.startsWith(pkg + ".")) return i;
        }
        return Integer.MAX_VALUE;
    }
}
