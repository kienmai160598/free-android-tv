package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.text.format.Formatter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.Inet4Address;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * The system card's pages, which OK steps through: what is in use, the network, the device, free space and the
 * chip. Each page is up to three lines in the card's meter rows: an icon, a name, a value, and a bar where a
 * share means something. Read on the main thread when the page shows and on the card's ticks; every read is a
 * system service call or one small sysfs file. The chip's temperature is not among them: SELinux hides the
 * thermal zones from apps on both TVs tried, so the thermal line is Android's own status (Android 10 and up).
 */
final class SystemPages {

    static final int USAGE = 0, NETWORK = 1, DEVICE = 2, SPACE = 3, CHIP = 4, COUNT = 5;
    /** Page names, for the card's settings, in page order. */
    static final int[] NAMES = {R.string.sys_page_usage, R.string.sys_page_network, R.string.sys_page_device,
            R.string.sys_page_space, R.string.sys_page_chip};

    /** One line: no bar when fill is NO_BAR. */
    static final class Line {
        static final float NO_BAR = -1f;
        final int icon;
        final String name, value;
        final float fill;

        Line(int icon, String name, String value, float fill) {
            this.icon = icon;
            this.name = name;
            this.value = value;
            this.fill = fill;
        }
    }

    /** Formatter's gigabyte since Android 8. */
    private static final long GIGABYTE = 1_000_000_000L;
    private static final String CLOCK_FILE = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq";

    private final Context context;

    SystemPages(Context context) {
        this.context = context;
    }

    /**
     * The page's lines; cpu is CpuMeter's last figure, CpuMeter.NOT_YET or CpuMeter.UNAVAILABLE. Usage leaves CPU
     * out when asked, and both pages leave it out when the TV hides it.
     */
    List<Line> lines(int page, int cpu, boolean showCpu) {
        switch (page) {
            case NETWORK: return network();
            case DEVICE: return device();
            case SPACE: return space();
            case CHIP: return chip(cpu);
            default: return usage(cpu, showCpu);
        }
    }

    private List<Line> usage(int cpu, boolean showCpu) {
        List<Line> lines = new ArrayList<>();
        if (showCpu && cpu != CpuMeter.UNAVAILABLE) lines.add(cpuLine(cpu));
        ActivityManager.MemoryInfo memory = memory();
        long usedMemory = memory.totalMem - memory.availMem;
        lines.add(new Line(R.drawable.ic_ram, text(R.string.widget_ram), usedOf(usedMemory, memory.totalMem),
                share(usedMemory, memory.totalMem)));
        StatFs data = stat(Environment.getDataDirectory());
        long total = data == null ? 0 : data.getTotalBytes();
        long used = data == null ? 0 : total - data.getAvailableBytes();
        lines.add(new Line(R.drawable.ic_storage, text(R.string.widget_storage),
                data == null ? "" : usedOf(used, total), share(used, total)));
        return lines;
    }

    private List<Line> network() {
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        Network active = cm == null ? null : cm.getActiveNetwork();
        NetworkCapabilities caps = active == null ? null : cm.getNetworkCapabilities(active);
        LinkProperties link = active == null ? null : cm.getLinkProperties(active);
        boolean wifi = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        boolean wired = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
        WifiInfo info = wifi ? wifiInfo() : null;

        List<Line> lines = new ArrayList<>();
        lines.add(new Line(wired ? R.drawable.ic_ethernet : R.drawable.ic_wifi,
                text(wired ? R.string.sys_ethernet : R.string.sys_wifi),
                caps == null ? text(R.string.sys_offline) : networkName(info), Line.NO_BAR));
        lines.add(new Line(R.drawable.ic_info, text(R.string.sys_ip), ipv4(link), Line.NO_BAR));
        if (info != null) {
            int level = WifiManager.calculateSignalLevel(info.getRssi(), 5);
            lines.add(new Line(R.drawable.ic_speed, text(R.string.sys_signal), info.getLinkSpeed() + " Mbps",
                    level / 4f));
        } else if (wired) {
            lines.add(new Line(R.drawable.ic_speed, text(R.string.sys_link), text(R.string.sys_wired), Line.NO_BAR));
        }
        return lines;
    }

    private List<Line> device() {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(R.drawable.ic_tv, text(R.string.sys_model), Build.MODEL, Line.NO_BAR));
        lines.add(new Line(R.drawable.ic_info, text(R.string.sys_android), Build.VERSION.RELEASE, Line.NO_BAR));
        lines.add(new Line(R.drawable.ic_timer, text(R.string.sys_uptime), uptime(), Line.NO_BAR));
        return lines;
    }

    private List<Line> space() {
        List<Line> lines = new ArrayList<>();
        ActivityManager.MemoryInfo memory = memory();
        lines.add(new Line(R.drawable.ic_ram, text(R.string.widget_ram), free(memory.availMem),
                share(memory.availMem, memory.totalMem)));
        StatFs data = stat(Environment.getDataDirectory());
        lines.add(new Line(R.drawable.ic_storage, text(R.string.widget_storage),
                data == null ? "" : free(data.getAvailableBytes()),
                data == null ? Line.NO_BAR : share(data.getAvailableBytes(), data.getTotalBytes())));
        StatFs usb = removable();
        lines.add(new Line(R.drawable.ic_storage, text(R.string.sys_usb),
                usb == null ? text(R.string.sys_none) : free(usb.getAvailableBytes()),
                usb == null ? Line.NO_BAR : share(usb.getAvailableBytes(), usb.getTotalBytes())));
        return lines;
    }

    private List<Line> chip(int cpu) {
        List<Line> lines = new ArrayList<>();
        if (cpu != CpuMeter.UNAVAILABLE) lines.add(cpuLine(cpu));
        lines.add(new Line(R.drawable.ic_speed, text(R.string.sys_clock), clock(), Line.NO_BAR));
        lines.add(new Line(R.drawable.ic_thermostat, text(R.string.sys_thermal), thermal(), Line.NO_BAR));
        return lines;
    }

    private Line cpuLine(int cpu) {
        boolean known = cpu >= 0;
        return new Line(R.drawable.ic_memory, text(R.string.widget_cpu), known ? cpu + "%" : "",
                known ? cpu / 100f : 0f);
    }

    // ---- readings --------------------------------------------------------------------------------

    private ActivityManager.MemoryInfo memory() {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        context.getSystemService(ActivityManager.class).getMemoryInfo(info);
        return info;
    }

    private static StatFs stat(File dir) {
        try {
            return new StatFs(dir.getPath());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A USB drive or SD card: the second of the app's external dirs is on it while one is mounted. */
    private StatFs removable() {
        File[] dirs = context.getExternalFilesDirs(null);
        for (int i = 1; i < dirs.length; i++) {
            if (dirs[i] != null) return stat(dirs[i]);
        }
        return null;
    }

    @SuppressWarnings("deprecation")   // getConnectionInfo: the only way to the link speed and signal below API 31
    private WifiInfo wifiInfo() {
        WifiManager wifi = context.getApplicationContext().getSystemService(WifiManager.class);
        return wifi == null ? null : wifi.getConnectionInfo();
    }

    /** The SSID, which needs the location permission tvctl grants; without it Android says <unknown ssid>. */
    private String networkName(WifiInfo info) {
        String ssid = info == null ? null : info.getSSID();
        if (ssid == null || ssid.equals(WifiManager.UNKNOWN_SSID)) return text(R.string.sys_connected);
        return ssid.length() > 1 && ssid.startsWith("\"") && ssid.endsWith("\"") ? ssid.substring(1, ssid.length() - 1)
                : ssid;
    }

    private static String ipv4(LinkProperties link) {
        if (link == null) return "";
        for (LinkAddress address : link.getLinkAddresses()) {
            if (address.getAddress() instanceof Inet4Address) return address.getAddress().getHostAddress();
        }
        return "";
    }

    private String uptime() {
        long minutes = SystemClock.elapsedRealtime() / 60_000L;
        long hours = minutes / 60, days = hours / 24;
        if (days > 0) return context.getString(R.string.uptime_days, days, hours % 24);
        if (hours > 0) return context.getString(R.string.uptime_hours, hours, minutes % 60);
        return context.getString(R.string.uptime_minutes, minutes);
    }

    /** cpu0's clock now, in GHz; empty where the kernel hides it. */
    private static String clock() {
        try (BufferedReader in = new BufferedReader(new FileReader(CLOCK_FILE))) {
            long khz = Long.parseLong(in.readLine().trim());
            return oneDecimal(khz / 1_000_000.0) + " GHz";
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    private String thermal() {
        if (Build.VERSION.SDK_INT < 29) return text(R.string.thermal_unknown);
        int status = context.getSystemService(PowerManager.class).getCurrentThermalStatus();
        if (status <= PowerManager.THERMAL_STATUS_NONE) return text(R.string.thermal_normal);
        return text(status <= PowerManager.THERMAL_STATUS_MODERATE ? R.string.thermal_warm : R.string.thermal_hot);
    }

    // ---- formatting ------------------------------------------------------------------------------

    private String text(int res) {
        return context.getString(res);
    }

    private String free(long bytes) {
        return context.getString(R.string.sys_free, Formatter.formatShortFileSize(context, bytes));
    }

    private static float share(long part, long whole) {
        return whole > 0 ? part / (float) whole : 0f;
    }

    /**
     * "1.4 / 2.0 GB": the unit once, when both sizes share it, so the meter's name keeps its room. Under
     * a gigabyte of a total in gigabytes reads "0.8 / 1.6 GB", not "843 MB / 1.6 GB" or the Formatter's
     * "0.91 GB" past 900 MB (both cut "Memory" short).
     */
    private String usedOf(long used, long total) {
        String u = Formatter.formatShortFileSize(context, used), t = Formatter.formatShortFileSize(context, total);
        int cut = lastSpace(t);
        boolean sameUnit = cut > 0 && lastSpace(u) > 0 && u.substring(lastSpace(u)).equals(t.substring(cut));
        if (total >= GIGABYTE && used < GIGABYTE) u = oneDecimal(used / (double) GIGABYTE);
        else if (sameUnit) u = u.substring(0, lastSpace(u));
        return context.getString(R.string.widget_used_of, u, t);
    }

    private static String oneDecimal(double value) {
        NumberFormat format = NumberFormat.getNumberInstance();
        format.setMinimumFractionDigits(1);
        format.setMaximumFractionDigits(1);
        return format.format(value);
    }

    private static int lastSpace(String s) {
        for (int i = s.length() - 1; i >= 0; i--) if (Character.isSpaceChar(s.charAt(i))) return i;
        return -1;
    }
}
