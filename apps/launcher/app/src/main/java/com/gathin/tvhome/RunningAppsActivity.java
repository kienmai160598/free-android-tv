package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The running apps panel, opened by a remote button through InputKeyService: a side sheet over whatever
 * is on screen listing the apps that still have a process, with the memory each holds. OK switches to
 * one, holding OK force-stops it, Stop all stops every one. Reading and stopping go through
 * {@link LocalAdb}, since Android 14 lets no normal app do either. Translucent and in a task of its
 * own like the quick settings panel; the button again, Back or anything taking the screen closes it.
 */
public final class RunningAppsActivity extends Activity {

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LocalAdb adb;
    private Config config;
    private View scrim;
    private LinearLayout panel, rows, footer;
    private TextView summary, hint;
    /** Packages with a launcher entry, minus this launcher and the keyboard; read once, off the main thread. */
    private volatile Set<String> listed;
    /** Waiting on adb: the TV's allow prompt pauses and stops this activity, which must not close it then. */
    private boolean busy;
    private SideSheet sideSheet;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        overridePendingTransition(0, 0);
        Palette.loadDarkMode(this);
        setContentView(R.layout.activity_running_apps);
        config = Config.load(this);
        adb = new LocalAdb(this);
        scrim = findViewById(R.id.scrim);
        panel = findViewById(R.id.panel);
        rows = findViewById(R.id.rows);
        footer = findViewById(R.id.footer);
        summary = findViewById(R.id.summary);
        hint = findViewById(R.id.hint);
        sideSheet = new SideSheet(this, scrim, panel, config);
        FocusScrollView rowsScroll = findViewById(R.id.rowsScroll);
        rowsScroll.setMotion(config.animations, config.reducedMotion);
        scrim.setOnClickListener(v -> sideSheet.close());
        reskin();
        sideSheet.slideIn();
        load(0);
    }

    /** The button pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        sideSheet.close();
    }

    @Override
    public void onBackPressed() {
        sideSheet.close();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!busy && !isFinishing()) finish();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    /** Reads the list; {@code focus} is the row to focus once it shows. */
    private void load(int focus) {
        busy = true;
        if (rows.getChildCount() == 0) showMessage(getString(R.string.running_connecting), getString(R.string.running_allow), false);
        worker.execute(() -> {
            String error;
            try {
                if (listed == null) listed = launchable();
                List<BackgroundApps.App> apps = BackgroundApps.list(adb, listed::contains);
                runOnUiThread(() -> {
                    busy = false;
                    showApps(apps, focus);
                });
                return;
            } catch (LocalAdb.OffException e) {
                error = getString(R.string.running_off);
            } catch (LocalAdb.NotAllowedException e) {
                error = getString(R.string.running_denied);
            } catch (IOException e) {
                error = getString(R.string.running_failed);
            }
            String message = error;
            runOnUiThread(() -> {
                busy = false;
                rows.removeAllViews();
                showMessage(message, null, true);
            });
        });
    }

    private Set<String> launchable() {
        PackageManager pm = getPackageManager();
        Set<String> packages = new HashSet<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo r : pm.queryIntentActivities(main, 0)) packages.add(r.activityInfo.packageName);
        }
        packages.remove(getPackageName());
        String ime = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        if (ime != null) packages.remove(ime.split("/")[0]);
        return packages;
    }

    private void showMessage(String text, String detail, boolean retry) {
        if (sideSheet.closing()) return;
        summary.setText(text);
        hint.setText(detail);
        hint.setVisibility(detail == null ? View.GONE : View.VISIBLE);
        footer.removeAllViews();
        if (retry) addRow(footer, getDrawable(R.drawable.ic_refresh), getString(R.string.try_again), null, null,
                v -> load(0), null);
        reskin();
        if (retry) footer.getChildAt(0).requestFocus();
    }

    private void showApps(List<BackgroundApps.App> apps, int focus) {
        if (sideSheet.closing()) return;
        rows.removeAllViews();
        footer.removeAllViews();
        if (apps.isEmpty()) {
            showMessage(getString(R.string.running_none), null, false);
            return;
        }
        long total = 0;
        PackageManager pm = getPackageManager();
        List<String> packages = new ArrayList<>();
        for (BackgroundApps.App app : apps) {
            total += app.memoryKb;
            packages.add(app.pkg);
            CharSequence label;
            Drawable icon;
            try {
                label = pm.getApplicationLabel(pm.getApplicationInfo(app.pkg, 0));
                icon = pm.getApplicationIcon(app.pkg);
            } catch (PackageManager.NameNotFoundException e) {
                continue;
            }
            String memory = Formatter.formatShortFileSize(this, app.memoryKb * 1024);
            // The most recent task is the app this panel opened over (over Home, the launcher is, and it is not listed).
            String status = app.recency == 0 ? getString(R.string.running_on_screen, memory) : memory;
            addRow(rows, icon, label, status, app.pkg, v -> {
                RemoteKeys.launch(this, app.pkg);
                finish();
            }, v -> {
                stop(Collections.singletonList(app.pkg), rows.indexOfChild(v));
                return true;
            });
        }
        summary.setText(getString(R.string.running_summary,
                getResources().getQuantityString(R.plurals.running_count, apps.size(), apps.size()),
                Formatter.formatShortFileSize(this, total * 1024)));
        hint.setText(R.string.running_hint);
        hint.setVisibility(View.VISIBLE);
        addRow(footer, getDrawable(R.drawable.ic_clear_all), getString(R.string.running_stop_all), null, null,
                v -> stop(packages, 0), null);
        reskin();
        View target = rows.getChildAt(Math.max(0, Math.min(focus, rows.getChildCount() - 1)));
        if (target != null) target.requestFocus();
    }

    private void stop(List<String> packages, int focusAfter) {
        if (busy) return;
        busy = true;
        // The rows go at once; the list read after the stop confirms it.
        for (int i = rows.getChildCount() - 1; i >= 0; i--) {
            View row = rows.getChildAt(i);
            if (packages.contains(row.getTag(R.id.running_app))) row.animate().alpha(0f).setDuration(Motion.FAST_EFFECTS.durationMs());
        }
        worker.execute(() -> {
            try {
                BackgroundApps.stop(adb, packages);
            } catch (IOException ignored) {
                // The list read next shows what is still running.
            }
            runOnUiThread(() -> load(focusAfter));
        });
    }

    /** A menu row; {@code pkg} is the app it stands for, or null for an action row. */
    private void addRow(LinearLayout parent, Drawable icon, CharSequence title, String status, String pkg,
                        View.OnClickListener click, View.OnLongClickListener longClick) {
        View row = getLayoutInflater().inflate(R.layout.item_input, parent, false);
        ImageView image = row.findViewById(R.id.icon);
        image.setImageDrawable(icon);
        if (pkg != null) {
            ViewGroup.LayoutParams lp = image.getLayoutParams();
            lp.width = lp.height = (int) (28 * getResources().getDisplayMetrics().density);
            image.setTag(R.id.running_app_icon, true);
        }
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        TextView statusView = row.findViewById(R.id.status);
        if (status != null) statusView.setText(status);
        else statusView.setVisibility(View.GONE);
        row.setOnClickListener(click);
        if (longClick != null) row.setOnLongClickListener(longClick);
        row.setTag(R.id.running_app, pkg);
        parent.addView(row);
    }

    /** Skins the sheet again after its rows change; app icons keep their own colours. */
    private void reskin() {
        M3Skin.apply(panel, Palette.currentOrBaseline(), config.animations, config.reducedMotion);
        untint(panel);
    }

    private static void untint(View v) {
        if (v instanceof ImageView && Boolean.TRUE.equals(v.getTag(R.id.running_app_icon))) {
            ((ImageView) v).setImageTintList(null);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) untint(g.getChildAt(i));
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }
}
