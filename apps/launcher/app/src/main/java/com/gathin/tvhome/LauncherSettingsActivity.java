package com.gathin.tvhome;

import android.app.Activity;
import android.widget.EditText;
import android.view.inputmethod.EditorInfo;
import android.view.WindowManager;
import android.view.Window;
import android.view.Gravity;
import android.view.KeyEvent;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.TypefaceSpan;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;

/**
 * Remote-friendly settings owned by the launcher, as a side sheet over Home: grouped lists under group
 * labels (Home, Appearance, Screensaver, You, Motion, System, About), each row with its icon. System settings
 * stay one row away.
 */
public final class LauncherSettingsActivity extends Activity {
    private SharedPreferences choices;
    private static final int[] COLUMN_OPTIONS = {4, 5, 6, 7};
    private static final String TURN_OFF_COMMAND = "input keyevent KEYCODE_SLEEP";
    private static final String RESTART_COMMAND = "reboot";
    private TextView columnsChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private M3Switch animationsSwitch;
    private M3Switch advancedSwitch;
    private M3Switch widgetsSwitch;
    private TextView nameChoice;
    private TextView widgetsChoice;
    private TextView tvSettingsChoice;
    private TextView colorChoice;
    private TextView backdropChoice;
    private TextView screensaverTimeChoice;
    private TextView screensaverStyleChoice;
    /** A theme's wallpaper is showing: it sets the scheme and covers the backdrop, so both rows rest. */
    private boolean wallpaper;
    private static final int[] BACKDROP_NAMES = {
            R.string.look_backdrop_shapes, R.string.look_backdrop_sky, R.string.look_backdrop_plain};
    private static final int[] SCREENSAVER_STYLE_NAMES = {R.string.screensaver_style_shapes,
            R.string.screensaver_style_glow, R.string.screensaver_style_minimal, R.string.screensaver_style_large};
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SideSheet sideSheet;
    private Palette palette;
    /** Google TV's own home screen, which tvctl turns off when this launcher takes over. */
    private static final String STOCK_LAUNCHER = "com.google.android.apps.tv.launcherx";
    /** The name Home greets with ("Good night, Kien"); greeting_name in config.xml until set here. */
    static final String NAME_KEY = "greeting_name";
    private int columns;
    private boolean animations;
    private boolean advancedEffects;
    private TextView updateChoice;
    private LauncherRelease offeredRelease;
    private boolean updateBusy;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        overridePendingTransition(0, 0);
        Palette.loadDarkMode(this);
        palette = Palette.currentOrBaseline();
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        columns = config.columns;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        columnsChoice = findViewById(R.id.columnsChoice);
        columnsChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_grid_view), null, null, null);
        columnsChoice.setOnClickListener(v -> chooseColumns(config));
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            setResult(RESULT_OK);
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        // A plain switch: the row's supporting text says what it does, no dialog in the way.
        advancedChoice.setOnClickListener(v -> setAdvanced(!advancedEffects));
        float density = getResources().getDisplayMetrics().density;
        animationsSwitch = new M3Switch(density, palette, config.animations);
        advancedSwitch = new M3Switch(density, palette, config.animations);
        animationsChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_animation), null, animationsSwitch, null);
        advancedChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_auto_awesome), null, advancedSwitch, null);
        tvSettingsChoice = findViewById(R.id.tvSettingsChoice);
        tvSettingsChoice.setText(getString(R.string.tv_settings));
        tvSettingsChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_settings), null,
                icon(R.drawable.ic_open_in_new), null);
        TextView rebootChoice = findViewById(R.id.rebootChoice);
        rebootChoice.setText(item(getString(R.string.reboot), getString(R.string.reboot_desc)));
        rebootChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_power), null, null, null);
        rebootChoice.setOnClickListener(v -> confirmPower(config));

        nameChoice = findViewById(R.id.nameChoice);
        nameChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_person), null, null, null);
        showName();
        nameChoice.setOnClickListener(v -> editName(config));
        widgetsChoice = findViewById(R.id.widgetsChoice);
        widgetsChoice.setText(item(getString(R.string.widgets), getString(R.string.widgets_desc)));
        boolean widgetsDefault = getResources().getBoolean(R.bool.widgets);
        widgetsSwitch = new M3Switch(density, palette, config.animations);
        widgetsSwitch.setChecked(choices.getBoolean(WidgetShelf.PREF_KEY, widgetsDefault));
        widgetsChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_widgets), null, widgetsSwitch, null);
        widgetsChoice.setOnClickListener(v -> {
            // Home reads it when it shows again.
            boolean on = !choices.getBoolean(WidgetShelf.PREF_KEY, widgetsDefault);
            choices.edit().putBoolean(WidgetShelf.PREF_KEY, on).apply();
            widgetsSwitch.setChecked(on);
        });
        tvSettingsChoice.setOnClickListener(v -> open(TvSettings.main(this)));
        showMenuInputs(config, density);
        showLook(config);
        showScreensaver(config);
        showHomeStatus();
        showUpdate();
        showAbout();
        refresh();
        View sheet = findViewById(R.id.sheet);
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(palette.surface);
        sheetBg.setCornerRadius(Shapes.EXTRA_LARGE * density);
        sheet.setBackground(sheetBg);
        sheet.setClipToOutline(true);
        M3Skin.apply(sheet, palette, config.animations, config.reducedMotion);
        View scrim = findViewById(R.id.scrim);
        sideSheet = new SideSheet(this, scrim, sheet, config);
        scrim.setOnClickListener(v -> sideSheet.close());
        FocusScrollView settingsScroll = findViewById(R.id.settingsScroll);
        settingsScroll.setMotion(config.animations, config.reducedMotion);
        sideSheet.slideIn((ViewGroup) settingsScroll.getChildAt(0));
        columnsChoice.requestFocus();
    }

    private boolean confirmKeyPressedHere;

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isConfirmKey(event.getKeyCode())) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                confirmKeyPressedHere = true;
            } else if (event.getAction() == KeyEvent.ACTION_UP && !confirmKeyPressedHere) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private static boolean isConfirmKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        sideSheet.close();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    /** A row's icon: onSurfaceVariant at rest, the focused list item's content colour on focus. */
    private Drawable icon(int res) {
        Drawable d = getDrawable(res).mutate();
        d.setTintList(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{palette.inverseOnSurface, palette.onSurfaceVariant}));
        return d;
    }

    /** System: whether Home opens this launcher, and Google TV's own home where the TV has one; both open settings. */
    private void showHomeStatus() {
        PackageManager pm = getPackageManager();
        TextView home = findViewById(R.id.defaultInfo);
        ResolveInfo r = pm.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY);
        boolean isDefault = r != null && r.activityInfo != null && getPackageName().equals(r.activityInfo.packageName);
        home.setText(item(getString(R.string.about_default),
                getString(isDefault ? R.string.about_default_yes : R.string.about_default_no)));
        home.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_home), null, null, null);
        home.setOnClickListener(v -> open(new Intent(Settings.ACTION_HOME_SETTINGS)));

        TextView stock = findViewById(R.id.stockInfo);
        int setting;
        try {
            setting = pm.getApplicationEnabledSetting(STOCK_LAUNCHER);
        } catch (IllegalArgumentException e) {
            stock.setVisibility(View.GONE);   // not on this TV: nothing to open
            return;
        }
        boolean off = setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                || setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                || setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
        stock.setText(item(getString(R.string.about_stock),
                getString(off ? R.string.about_stock_off : R.string.about_stock_on)));
        stock.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_tv), null, null, null);
        // Its App info page: where it can be turned back on (tvctl's undo does the same from a computer).
        stock.setOnClickListener(v -> open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", STOCK_LAUNCHER, null))));
    }

    /**
     * About, view only: an outlined card, M3's container for static content, of entries (a label over its value)
     * with no icons or arrows. It takes focus so the remote reaches it, but OK does nothing: focus only turns its
     * outline into the primary focus ring, where the rows that act fill in.
     */
    private void showUpdate() {
        updateChoice = findViewById(R.id.updateChoice);
        updateChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(
                icon(R.drawable.ic_system_update), null, null, null);
        updateChoice.setOnClickListener(v -> onUpdateChosen());
        checkForUpdate();
    }

    private void checkForUpdate() {
        String installed = versionName();
        updateBusy = true;
        showUpdateState(getString(R.string.update_checking, installed));
        worker.execute(() -> {
            try {
                LauncherRelease latest = LauncherRelease.latest();
                runOnUiThread(() -> showRelease(latest, installed));
            } catch (IOException e) {
                runOnUiThread(() -> showUpdateFailure(R.string.update_check_failed));
            }
        });
    }

    private void showRelease(LauncherRelease latest, String installed) {
        updateBusy = false;
        offeredRelease = latest.isNewerThan(installed) ? latest : null;
        if (offeredRelease == null) {
            showUpdateState(getString(R.string.update_current, installed));
            return;
        }

        showUpdateState(getString(R.string.update_available, latest.version,
                latest.apkSize / 1024));
    }

    private void onUpdateChosen() {
        if (updateBusy) return;

        if (offeredRelease == null) {
            checkForUpdate();
            return;
        }

        if (!getPackageManager().canRequestPackageInstalls()) {
            askToAllowInstalls();
            return;
        }

        downloadAndInstall(offeredRelease);
    }

    private void downloadAndInstall(LauncherRelease release) {
        updateBusy = true;
        showUpdateState(getString(R.string.update_downloading, 0));
        worker.execute(() -> {
            try {
                ReleaseInstaller.install(getApplicationContext(), release, percent ->
                        runOnUiThread(() ->
                                showUpdateState(getString(R.string.update_downloading, percent))));
                runOnUiThread(() -> {
                    updateBusy = false;
                    showUpdateState(getString(R.string.update_installing));
                });
            } catch (IOException e) {
                runOnUiThread(() -> showUpdateFailure(R.string.update_download_failed));
            }
        });
    }

    private void askToAllowInstalls() {
        Intent allow = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.fromParts("package", getPackageName(), null));
        try {
            startActivity(allow);
        } catch (ActivityNotFoundException | SecurityException e) {
            showUpdateState(getString(R.string.update_needs_permission));
        }
    }

    private void showUpdateFailure(int message) {
        updateBusy = false;
        showUpdateState(getString(message));
    }

    private void showUpdateState(String status) {
        updateChoice.setText(item(getString(R.string.update_title), status));
    }

    private void showAbout() {
        showAboutRow(R.id.aboutVersion, R.drawable.ic_info, R.string.about_version, versionName());
        showAboutRow(R.id.aboutAuthor, R.drawable.ic_person, R.string.about_author,
                getString(R.string.about_author_name));
        showAboutRow(R.id.aboutEmail, R.drawable.ic_mail, R.string.about_email,
                getString(R.string.about_email_value));
        showAboutRow(R.id.aboutSource, R.drawable.ic_code, R.string.about_source,
                getString(R.string.about_source_value));
    }

    private void showAboutRow(int rowId, int iconRes, int label, String value) {
        TextView row = findViewById(rowId);
        row.setText(item(getString(label), value));
        row.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(iconRes), null, null, null);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private String name() {
        return choices.getString(NAME_KEY, getString(R.string.greeting_name)).trim();
    }

    private void showName() {
        String name = name();
        nameChoice.setText(item(getString(R.string.your_name), name.isEmpty() ? getString(R.string.your_name_none) : name));
    }

    private void editName(Config config) {
        Palette p = Palette.currentOrBaseline();
        float d = getResources().getDisplayMetrics().density;
        LinearLayout panel = dialogPanel(R.string.your_name, p, d);

        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setText(name());
        field.setSelection(field.getText().length());
        field.setHint(R.string.your_name);
        field.setTextSize(18f);
        field.setTextColor(p.onSurface);
        field.setHintTextColor(p.onSurfaceVariant);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        GradientDrawable box = new GradientDrawable();
        box.setCornerRadius(Shapes.EXTRA_SMALL * d);
        box.setStroke((int) (2 * d), p.primary);
        field.setBackground(box);
        int fieldPadding = (int) (16 * d);
        field.setPadding(fieldPadding, fieldPadding, fieldPadding, fieldPadding);
        LinearLayout.LayoutParams fieldLayout = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fieldLayout.topMargin = (int) (16 * d);
        panel.addView(field, fieldLayout);

        LinearLayout buttons = dialogButtonRow(panel, d);
        TextView cancel = dialogButton(android.R.string.cancel, d);
        TextView save = dialogButton(R.string.save, d);
        buttons.addView(cancel);
        buttons.addView(save);
        ((LinearLayout.LayoutParams) save.getLayoutParams()).setMarginStart((int) (8 * d));

        Dialog dialog = showDialog(panel, p, config, d);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }

        Runnable commit = () -> {
            choices.edit().putString(NAME_KEY, field.getText().toString().trim()).apply();
            showName();
            dialog.dismiss();
        };
        save.setOnClickListener(v -> commit.run());
        cancel.setOnClickListener(v -> dialog.dismiss());
        field.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            commit.run();
            return true;
        });
        field.requestFocus();
    }

    private void chooseColumns(Config config) {
        CharSequence[] labels = new CharSequence[COLUMN_OPTIONS.length];
        int selected = 0;
        for (int index = 0; index < COLUMN_OPTIONS.length; index++) {
            labels[index] = String.valueOf(COLUMN_OPTIONS[index]);
            if (COLUMN_OPTIONS[index] == columns) selected = index;
        }
        chooseOne(R.string.columns_label, labels, null, selected, index -> setColumns(COLUMN_OPTIONS[index]), config);
    }

    /** A radio list in a dialog, each row optionally with a swatch at its end; a pick closes it. */
    private void chooseOne(int titleText, CharSequence[] labels, Drawable[] swatches, int selected,
                           IntConsumer pick, Config config) {
        Palette p = Palette.currentOrBaseline();
        float d = getResources().getDisplayMetrics().density;
        LinearLayout panel = dialogPanel(titleText, p, d);

        LinearLayout options = dialogOptionList(panel, d);
        TextView[] rows = new TextView[labels.length];
        for (int index = 0; index < labels.length; index++) {
            int radio = index == selected ? R.drawable.ic_radio_on : R.drawable.ic_radio_off;
            TextView row = optionRow(labels[index], radio);
            if (swatches != null) {
                row.setCompoundDrawablesRelativeWithIntrinsicBounds(row.getCompoundDrawablesRelative()[0], null,
                        swatches[index], null);
            }
            options.addView(row);
            rows[index] = row;
        }

        Dialog dialog = showDialog(panel, p, config, d);
        for (int index = 0; index < rows.length; index++) {
            int value = index;
            rows[index].setOnClickListener(v -> {
                pick.accept(value);
                dialog.dismiss();
            });
        }
        rows[selected >= 0 && selected < rows.length ? selected : 0].requestFocus();
    }

    /** The Menu button opens the inputs (InputKeyService); only on a TV with HDMI or other inputs. */
    private void showMenuInputs(Config config, float density) {
        TextView row = findViewById(R.id.menuInputsChoice);
        if (!hasInputs()) {
            row.setVisibility(View.GONE);
            return;
        }
        row.setText(item(getString(R.string.menu_inputs), getString(R.string.menu_inputs_desc)));
        M3Switch toggle = new M3Switch(density, palette, config.animations);
        toggle.setChecked(choices.getBoolean(InputKeyService.MENU_OPENS_INPUTS, false));
        row.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_input), null, toggle, null);
        row.setOnClickListener(v -> {
            boolean on = !choices.getBoolean(InputKeyService.MENU_OPENS_INPUTS, false);
            choices.edit().putBoolean(InputKeyService.MENU_OPENS_INPUTS, on).apply();
            toggle.setChecked(on);
        });
    }

    /** HDMI, AV or another passthrough input the TV lists and has not hidden, as Home's Inputs pill counts them. */
    private boolean hasInputs() {
        TvInputManager inputs = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (inputs == null) return false;
        for (TvInputInfo info : inputs.getTvInputList()) {
            if (info.isPassthroughInput() && !info.isHidden(this)) return true;
        }
        return false;
    }

    /** Appearance: the scheme's seed colour and what is behind the apps; Home repaints behind the sheet. */
    private void showLook(Config config) {
        colorChoice = findViewById(R.id.colorChoice);
        colorChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_contrast), null, null, null);
        colorChoice.setOnClickListener(v -> chooseColor(config));
        backdropChoice = findViewById(R.id.backdropChoice);
        backdropChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_picture), null, null, null);
        backdropChoice.setOnClickListener(v -> chooseBackdrop(config));
        wallpaper = !config.wallpapers.isEmpty();
        for (TextView row : new TextView[]{colorChoice, backdropChoice}) {
            row.setEnabled(!wallpaper);
            row.setAlpha(wallpaper ? 0.38f : 1f);
        }
        showLookValues();
    }

    private void showLookValues() {
        int seed = seedIndex();
        String[] names = getResources().getStringArray(R.array.look_seed_names);
        colorChoice.setText(item(getString(R.string.look_color),
                seed < 0 ? getString(R.string.look_color_theme) : names[seed]));
        int backdrop = indexOf(HomeLook.BACKDROPS, HomeLook.backdrop(choices));
        backdropChoice.setText(item(getString(R.string.look_backdrop),
                getString(wallpaper ? R.string.look_backdrop_wallpaper : BACKDROP_NAMES[backdrop])));
    }

    /** The chosen seed's place in HomeLook.SEEDS, or -1 for the theme's colour. */
    private int seedIndex() {
        if (!choices.contains(Contract.Key.BACKGROUND_COLOR)) return -1;
        int chosen = choices.getInt(Contract.Key.BACKGROUND_COLOR, 0);
        for (int index = 0; index < HomeLook.SEEDS.length; index++) if (HomeLook.SEEDS[index] == chosen) return index;
        return -1;
    }

    private void chooseColor(Config config) {
        String[] names = getResources().getStringArray(R.array.look_seed_names);
        CharSequence[] labels = new CharSequence[HomeLook.SEEDS.length + 1];
        Drawable[] swatches = new Drawable[labels.length];
        labels[0] = getString(R.string.look_color_theme);
        for (int index = 0; index < HomeLook.SEEDS.length; index++) {
            labels[index + 1] = names[index];
            swatches[index + 1] = swatch(Palette.fromSeed(HomeLook.SEEDS[index]).primary);
        }
        chooseOne(R.string.look_color, labels, swatches, seedIndex() + 1, index -> {
            SharedPreferences.Editor editor = choices.edit();
            if (index == 0) editor.remove(Contract.Key.BACKGROUND_COLOR);
            else editor.putInt(Contract.Key.BACKGROUND_COLOR, HomeLook.SEEDS[index - 1]);
            editor.apply();
            showLookValues();
        }, config);
    }

    private void chooseBackdrop(Config config) {
        CharSequence[] labels = new CharSequence[BACKDROP_NAMES.length];
        for (int index = 0; index < labels.length; index++) labels[index] = getString(BACKDROP_NAMES[index]);
        int selected = indexOf(HomeLook.BACKDROPS, HomeLook.backdrop(choices));
        chooseOne(R.string.look_backdrop, labels, null, selected, index -> {
            choices.edit().putString(HomeLook.BACKDROP_KEY, HomeLook.BACKDROPS[index]).apply();
            showLookValues();
        }, config);
    }

    /** Screensaver: when it starts (a system setting) and ClockDream's style (this launcher's). */
    private void showScreensaver(Config config) {
        screensaverTimeChoice = findViewById(R.id.screensaverTimeChoice);
        screensaverTimeChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_timer), null, null,
                null);
        screensaverTimeChoice.setOnClickListener(v -> chooseScreensaverTime(config));
        screensaverStyleChoice = findViewById(R.id.screensaverStyleChoice);
        screensaverStyleChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_w_clear_night), null,
                null, null);
        screensaverStyleChoice.setOnClickListener(v -> chooseScreensaverStyle(config));
        showScreensaverValues();
    }

    private void showScreensaverValues() {
        String after = minutesText(Screensaver.minutes(this));
        screensaverTimeChoice.setText(item(getString(R.string.screensaver_after), after));
        int style = indexOf(Screensaver.STYLES, Screensaver.style(choices));
        screensaverStyleChoice.setText(item(getString(R.string.screensaver_style),
                getString(SCREENSAVER_STYLE_NAMES[style])));
    }

    private String minutesText(int minutes) {
        return minutes == 0 ? getString(R.string.screensaver_never)
                : getResources().getQuantityString(R.plurals.screensaver_minutes, minutes, minutes);
    }

    private void chooseScreensaverTime(Config config) {
        CharSequence[] labels = new CharSequence[Screensaver.MINUTES.length];
        int current = Screensaver.minutes(this), selected = -1;
        for (int index = 0; index < labels.length; index++) {
            labels[index] = minutesText(Screensaver.MINUTES[index]);
            if (Screensaver.MINUTES[index] == current) selected = index;
        }
        chooseOne(R.string.screensaver_after, labels, null, selected, index -> {
            if (!Screensaver.setMinutes(this, Screensaver.MINUTES[index])) {
                Toast.makeText(this, R.string.screensaver_denied, Toast.LENGTH_LONG).show();
            }
            showScreensaverValues();
        }, config);
    }

    private void chooseScreensaverStyle(Config config) {
        CharSequence[] labels = new CharSequence[SCREENSAVER_STYLE_NAMES.length];
        for (int index = 0; index < labels.length; index++) labels[index] = getString(SCREENSAVER_STYLE_NAMES[index]);
        int selected = indexOf(Screensaver.STYLES, Screensaver.style(choices));
        chooseOne(R.string.screensaver_style, labels, null, selected, index -> {
            choices.edit().putString(Screensaver.STYLE_KEY, Screensaver.STYLES[index]).apply();
            showScreensaverValues();
        }, config);
    }

    private static int indexOf(String[] values, String value) {
        for (int index = 0; index < values.length; index++) if (values[index].equals(value)) return index;
        return 0;
    }

    /** A filled circle in a colour, the size of a row's icon. */
    private Drawable swatch(int color) {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color);
        int size = Math.round(20 * getResources().getDisplayMetrics().density);
        dot.setSize(size, size);
        return dot;
    }

    private void confirmPower(Config config) {
        Palette p = Palette.currentOrBaseline();
        float d = getResources().getDisplayMetrics().density;
        LinearLayout panel = dialogHeroPanel(R.drawable.ic_power, R.string.reboot, R.string.boot_options_message, p, d);

        LinearLayout options = dialogOptionList(panel, d);
        TextView restart = optionRow(item(getString(R.string.reboot_action), getString(R.string.restart_desc)),
                R.drawable.ic_refresh);
        TextView turnOff = optionRow(item(getString(R.string.power_off_action), getString(R.string.power_off_desc)),
                R.drawable.ic_power);
        options.addView(restart);
        options.addView(turnOff);

        LinearLayout buttons = dialogButtonRow(panel, d);
        TextView cancel = dialogButton(android.R.string.cancel, d);
        buttons.addView(cancel);

        Dialog dialog = showDialog(panel, p, config, d);
        cancel.setOnClickListener(v -> dialog.dismiss());
        restart.setOnClickListener(v -> {
            dialog.dismiss();
            runPowerCommand(RESTART_COMMAND);
        });
        turnOff.setOnClickListener(v -> {
            dialog.dismiss();
            runPowerCommand(TURN_OFF_COMMAND);
        });
        cancel.requestFocus();
    }

    private void runPowerCommand(String command) {
        LocalAdb adb = new LocalAdb(this);
        worker.execute(() -> {
            int problem;
            try {
                adb.shell(command);
                return;
            } catch (LocalAdb.OffException e) {
                problem = R.string.running_off;
            } catch (LocalAdb.NotAllowedException e) {
                problem = R.string.running_denied;
            } catch (IOException e) {
                return;
            }
            int message = problem;
            runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
        });
    }

    private void setColumns(int value) {
        if (columns == value) return;
        columns = value;
        choices.edit().putInt("columns", value).apply();
        refresh();
    }

    private LinearLayout dialogHeroPanel(int iconRes, int titleText, int messageText, Palette p, float d) {
        LinearLayout panel = dialogPanel(titleText, p, d);
        TextView title = (TextView) panel.getChildAt(0);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ShapeBadge badge = new ShapeBadge(this, null);
        badge.setPalette(p);
        badge.setIcon(getDrawable(iconRes).mutate());
        int badgeSize = (int) (48 * d);
        LinearLayout.LayoutParams badgeLayout = new LinearLayout.LayoutParams(badgeSize, badgeSize);
        badgeLayout.gravity = Gravity.CENTER_HORIZONTAL;
        badgeLayout.bottomMargin = (int) (16 * d);
        panel.addView(badge, 0, badgeLayout);

        TextView message = new TextView(this);
        message.setText(messageText);
        message.setTextSize(14f);
        message.setTextColor(p.onSurfaceVariant);
        message.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams messageLayout = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        messageLayout.topMargin = (int) (8 * d);
        panel.addView(message, messageLayout);
        return panel;
    }

    private LinearLayout dialogOptionList(LinearLayout panel, float d) {
        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        options.setClipChildren(false);
        options.setClipToPadding(false);
        LinearLayout.LayoutParams optionsLayout = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        optionsLayout.topMargin = (int) (20 * d);
        panel.addView(options, optionsLayout);
        return options;
    }

    private TextView optionRow(CharSequence text, int iconRes) {
        TextView row = new TextView(this, null, 0, R.style.SettingsListItem);
        row.setText(text);
        row.setFocusable(true);
        row.setClickable(true);
        row.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(iconRes), null, null, null);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private LinearLayout dialogButtonRow(LinearLayout panel, float d) {
        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.END);
        buttons.setClipChildren(false);
        buttons.setClipToPadding(false);
        LinearLayout.LayoutParams buttonsLayout = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonsLayout.topMargin = (int) (24 * d);
        panel.addView(buttons, buttonsLayout);
        return buttons;
    }

    private LinearLayout dialogPanel(int titleText, Palette p, float d) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setTag("m3:panel");
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        int padding = (int) (24 * d);
        panel.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(22f);
        title.setTextColor(p.onSurface);
        panel.addView(title);
        return panel;
    }

    private Dialog showDialog(LinearLayout panel, Palette p, Config config, float d) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        M3Skin.apply(panel, p, config.animations, config.reducedMotion);
        panel.setElevation(0f);   // flat: the dialog's dim already sets it apart

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0));
            window.setLayout((int) (440 * d), ViewGroup.LayoutParams.WRAP_CONTENT);
            if (config.animations) window.setWindowAnimations(0);   // the panel's own spring instead
        }
        dialog.show();
        if (config.animations) popIn(panel, config);
        return dialog;
    }

    /** M3 Expressive's dialog entrance: the panel grows from 90% on the default spatial spring as it fades in. */
    private static void popIn(View panel, Config config) {
        Motion.Spec spatial = Motion.DEFAULT_SPATIAL.reduced(config.reducedMotion), effects = Motion.DEFAULT_EFFECTS;
        panel.setScaleX(POP_FROM);
        panel.setScaleY(POP_FROM);
        panel.setAlpha(0f);
        spatial.drive(panel.animate().scaleX(1f).scaleY(1f));
        effects.fade(panel, 1f, 0);
    }

    private static final float POP_FROM = 0.9f;

    private TextView dialogButton(int text, float d) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTag("m3:button");
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        b.setTextSize(15f);
        int h = (int) (24 * d);
        b.setPadding(h, 0, h, 0);
        b.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (40 * d)));
        return b;
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        setResult(RESULT_OK);
        refresh();
    }

    private void refresh() {
        columnsChoice.setText(item(getString(R.string.columns_label), String.valueOf(columns)));
        animationsChoice.setText(item(title(getString(R.string.animations_state, "")),
                getString(R.string.animations_desc)));
        animationsSwitch.setChecked(animations);
        advancedChoice.setText(item(getString(R.string.advanced_effects), getString(R.string.advanced_effects_warning)));
        advancedSwitch.setChecked(advancedEffects);
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.38f);
        M3Skin.refresh(findViewById(android.R.id.content));
    }

    /** "Animations: %1$s" with the value left out, in whatever form the language puts it. */
    private static String title(String formattedWithBlank) {
        return formattedWithBlank.replaceAll("[\\s:：\\-–—]+$", "").replaceAll("^[\\s:：\\-–—]+", "");
    }

    /** A list item's text: the title, and under it the supporting text, smaller and in the regular weight. */
    private CharSequence item(String title, String supporting) {
        if (supporting == null) return title;
        SpannableStringBuilder text = new SpannableStringBuilder(title).append('\n');
        int start = text.length();
        text.append(supporting);
        text.setSpan(new RelativeSizeSpan(0.78f), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (Build.VERSION.SDK_INT >= 28) {
            text.setSpan(new TypefaceSpan(getResources().getFont(R.font.sans_regular)), start, text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text;
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
