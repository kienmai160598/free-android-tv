package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Intent;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Xiaomi's China TVs switch inputs through their own player, by a source number, and nothing there answers the
 * standard ACTION_VIEW on a passthrough channel. The numbers come from the port's label, the way Xiaomi's
 * SourceManager reads them (MiTV-ASTP0: HDMI1 to HDMI3 are 23 to 25, AV is 2); a device behind a port (an HDMI-CEC
 * "Playback_1") goes by its port's label.
 */
final class XiaomiInputs {
    private XiaomiInputs() { }

    private static final String PLAYER = "com.xiaomi.mitv.tvplayer";
    static final ComponentName EXTERNAL_SOURCE = new ComponentName(PLAYER, PLAYER + ".ExternalSourceActivity");
    private static final String ACTION = PLAYER + ".EXTSRC_PLAY";
    private static final String EXTRA_SOURCE = "input";
    private static final int FIRST_HDMI = 23, AV = 2;
    private static final Pattern HDMI = Pattern.compile("HDMI\\s*([1-9])");

    /** The source number for a port's label, or -1 when it names no HDMI or AV port. */
    static int source(String label) {
        if (label == null) return -1;
        String upper = label.trim().toUpperCase(Locale.ROOT);
        Matcher hdmi = HDMI.matcher(upper);
        if (hdmi.find()) return FIRST_HDMI + Integer.parseInt(hdmi.group(1)) - 1;
        return upper.startsWith("AV") ? AV : -1;
    }

    /** Xiaomi's player is in front: an HDMI or AV input is on screen. */
    static boolean isPlayer(CharSequence packageName) {
        return PLAYER.contentEquals(packageName);
    }

    static Intent intent(int source) {
        return new Intent(ACTION).setComponent(EXTERNAL_SOURCE).putExtra(EXTRA_SOURCE, source);
    }
}
