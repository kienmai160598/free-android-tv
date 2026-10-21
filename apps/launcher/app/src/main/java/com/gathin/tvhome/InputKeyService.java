package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import java.util.Collections;
import java.util.Map;

/**
 * Maps the remote's Input button to the system input switcher, from any app. Remotes such as the Chromecast voice
 * remote send KEYCODE_TV for it, others (KTC and Kalley Realtek Google TV remotes) KEYCODE_TV_INPUT; on Google TV
 * the stock launcher handles both, so with the stock launcher disabled the button does nothing. Buttons listed in
 * input_key_keycodes (config.xml or a theme) open the input switcher the same way, and app_keys start an app (a
 * YouTube button that only the stock launcher answered), and running_apps_keycodes open the running apps panel.
 * The Menu button can open the inputs too, chosen in Launcher settings ({@link #MENU_OPENS_INPUTS}): Xiaomi's
 * China remotes have no Input button, only a three-line Menu one. Where the TV has no system input switcher
 * (Xiaomi), the button opens Home's own Inputs menu; while an input is on screen it stays Xiaomi's player
 * menu, since leaving the player makes the TV the HDMI-CEC active source and drops the box being watched. Every other key passes through untouched.
 * Opt-in: INPUT_KEY_SERVICE=1 build-and-install.sh (or -InputKeyService on Windows) enables it.
 */
public class InputKeyService extends AccessibilityService {

    private static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";
    /** Launcher settings: the Menu button opens the inputs (off by default; apps lose their Menu button). */
    static final String MENU_OPENS_INPUTS = "menu_opens_inputs";

    private int[] inputKeycodes = new int[0];
    private Map<Integer, String> appKeys = Collections.emptyMap();
    private int[] runningAppsKeycodes = new int[0];
    /** The app in front, from window changes; system overlays (the volume bar) leave it as it was. */
    private CharSequence frontPackage = "";
    private static final String[] OVERLAYS = {"com.android.systemui", "com.xiaomi.mitv.systemui"};

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // From config.xml or a theme's config.json; theme.sh rebinds this service after a push.
        Config config = Config.load(this);
        inputKeycodes = config.inputKeycodes;
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
        runningAppsKeycodes = getResources().getIntArray(R.array.running_apps_keycodes);
    }

    private boolean isRunningAppsKey(int keyCode) {
        for (int code : runningAppsKeycodes) if (keyCode == code) return true;
        return false;
    }

    private boolean isInputKey(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_TV || keyCode == KeyEvent.KEYCODE_TV_INPUT) return true;
        if (keyCode == KeyEvent.KEYCODE_MENU && !XiaomiInputs.isPlayer(frontPackage)
                && getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).getBoolean(MENU_OPENS_INPUTS, false)) {
            return true;
        }
        for (int code : inputKeycodes) if (keyCode == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (isRunningAppsKey(event.getKeyCode())) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                startActivity(new Intent(this, RunningAppsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return true;
        }
        String app = appKeys.get(event.getKeyCode());
        if (app == null && !isInputKey(event.getKeyCode())) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            if (app != null) {
                RemoteKeys.launch(this, app);
            } else {
                openInputs();
            }
        }
        return true;
    }

    /** The system's input switcher, or Home's Inputs menu where the firmware has none. */
    private void openInputs() {
        Intent inputs = new Intent(ACTION_VIEW_INPUTS);
        if (inputs.resolveActivity(getPackageManager()) == null) {
            inputs = new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_SHOW_INPUTS);
        }
        try {
            startActivity(inputs.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) {
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence app = event.getPackageName();
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || app == null) return;
        for (String overlay : OVERLAYS) if (overlay.contentEquals(app)) return;
        frontPackage = app;
    }

    @Override
    public void onInterrupt() {
    }
}
