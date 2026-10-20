package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Exists only so the launcher may read the TV's media sessions for the Now playing widget: Android
 * hands active sessions to an app whose notification listener the user has allowed. It reads no
 * notifications. Allowed from a computer with
 * {@code adb shell cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.NowPlayingListener}
 * (build-and-install.sh does it with NOW_PLAYING=1).
 */
public final class NowPlayingListener extends NotificationListenerService {
}
