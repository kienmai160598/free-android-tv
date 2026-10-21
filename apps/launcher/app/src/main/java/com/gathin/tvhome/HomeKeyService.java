package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Sends the remote's Home button to this launcher on remotes that emit KEYCODE_ALL_APPS for Home
 * (Xiaomi sticks and boxes). Android TV 9 routes that key only to the system-image launcher, so once
 * the stock launcher is disabled the button does nothing. Every other key passes through untouched.
 *
 * On firmware that lets only its own launcher be Home (Xiaomi's China TVs ignore set-home-activity, and
 * with the stock launcher disabled Home falls to Settings' FallbackHome), the stock one stays enabled
 * and this service stands in for it: KEYCODE_HOME opens this launcher, and so does the stock launcher
 * coming to the front (after boot, or Back out of an app). With the stock launcher disabled anyway,
 * Settings' FallbackHome is the one that comes to the front. Home windows are matched by their activity
 * class, and only when no other window follows within SETTLE_MS, so Settings, which shares FallbackHome's
 * task, still opens.
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 */
public class HomeKeyService extends AccessibilityService {

    /** Remote buttons that should open Settings (config.xml, settings_key_scancodes and settings_key_keycodes). */
    private int[] settingsScancodes = new int[0];
    private int[] settingsKeycodes = new int[0];
    private boolean settingsPanel;
    /** app_keys, the same map InputKeyService reads, for setups that run only this service. */
    private Map<Integer, String> appKeys = Collections.emptyMap();
    private static final long JUST_BOOTED_MS = 3 * 60_000;
    private static final String STANDS_IN_FOR_HOME = "stands_in_for_home";
    private static final long SETTLE_MS = 300;
    private final Handler settle = new Handler(Looper.getMainLooper());
    private final Runnable replaceOtherHome = () -> {
        rememberStandingIn();
        openHome();
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        // Keycodes and the panel choice can also come from a theme; theme.sh restarts this service.
        Config config = Config.load(this);
        settingsKeycodes = config.settingsKeycodes;
        settingsPanel = config.settingsKeyPanel;
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
        if (SystemClock.elapsedRealtime() < JUST_BOOTED_MS) openHome();
    }

    private boolean isSettingsKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;
        for (int code : settingsScancodes) if (event.getScanCode() == code) return true;
        for (int code : settingsKeycodes) if (event.getKeyCode() == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // The stock launcher handled the remote's Settings button; with it disabled the button does nothing.
        if (isSettingsKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                Intent open = settingsPanel ? new Intent(this, QuickSettingsActivity.class)
                        : TvSettings.main(this);
                startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return true;
        }
        String app = appKeys.get(event.getKeyCode());
        if (app != null) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) RemoteKeys.launch(this, app);
            return true;
        }
        boolean standsInForHome = standsInForHome() || stockHome() != null;
        boolean home = event.getKeyCode() == KeyEvent.KEYCODE_ALL_APPS
                || (event.getKeyCode() == KeyEvent.KEYCODE_HOME && standsInForHome);
        if (!home) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) openHome();
        return true;
    }

    /** The activity Home resolves to when it is not this launcher, else null. */
    private ActivityInfo stockHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo owner = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        if (owner == null || owner.activityInfo == null) return null;
        return getPackageName().equals(owner.activityInfo.packageName) ? null : owner.activityInfo;
    }

    private boolean isOtherHomeWindow(AccessibilityEvent event) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> homes = getPackageManager()
                .queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo candidate : homes) {
            ActivityInfo activity = candidate.activityInfo;
            if (activity == null || getPackageName().equals(activity.packageName)) continue;
            if (isWindowOf(activity, event)) return true;
        }

        return false;
    }

    private boolean isActivityWindow(AccessibilityEvent event) {
        if (event.getClassName() == null) return false;

        ComponentName window = new ComponentName(event.getPackageName().toString(),
                event.getClassName().toString());
        try {
            getPackageManager().getActivityInfo(window, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static boolean isWindowOf(ActivityInfo activity, AccessibilityEvent event) {
        return activity.packageName.contentEquals(event.getPackageName())
                && event.getClassName() != null
                && activity.name.contentEquals(event.getClassName());
    }

    private boolean standsInForHome() {
        return getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE)
                .getBoolean(STANDS_IN_FOR_HOME, false);
    }

    private void rememberStandingIn() {
        if (standsInForHome()) return;

        getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).edit()
                .putBoolean(STANDS_IN_FOR_HOME, true).apply();
    }

    private void openHome() {
        Intent home = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(home);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        if (isOtherHomeWindow(event)) {
            settle.removeCallbacks(replaceOtherHome);
            settle.postDelayed(replaceOtherHome, SETTLE_MS);
        } else if (isActivityWindow(event)) {
            settle.removeCallbacks(replaceOtherHome);
        }
    }

    @Override
    public void onInterrupt() {
        settle.removeCallbacks(replaceOtherHome);
    }
}
