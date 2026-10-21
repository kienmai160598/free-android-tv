package com.gathin.tvhome;

import android.app.Activity;
import android.widget.EditText;
import android.view.inputmethod.EditorInfo;
import android.view.WindowManager;
import android.view.Window;
import android.view.Gravity;
import android.view.KeyEvent;
import android.graphics.drawable.GradientDrawable;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
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

/**
 * Remote-friendly settings owned by the launcher, as a side sheet over Home: grouped lists under group
 * labels (Home, You, Motion, System, About), each row with its icon. System settings stay one row away.
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
        sideSheet.slideIn();
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

    /** About: this launcher's version, whether Home opens it, and whether Google TV's own home is off. */
    private void showAbout() {
        PackageManager pm = getPackageManager();
        TextView version = findViewById(R.id.versionInfo);
        String name = "";
        try {
            PackageInfo info = pm.getPackageInfo(getPackageName(), 0);
            name = info.versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        version.setText(item(getString(R.string.about_version), name));
        version.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_info), null, null, null);

        TextView home = findViewById(R.id.defaultInfo);
        ResolveInfo r = pm.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY);
        boolean isDefault = r != null && r.activityInfo != null && getPackageName().equals(r.activityInfo.packageName);
        home.setText(item(getString(R.string.about_default),
                getString(isDefault ? R.string.about_default_yes : R.string.about_default_no)));
        home.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_home), null, null, null);
        home.setOnClickListener(v -> open(new Intent(Settings.ACTION_HOME_SETTINGS)));

        TextView stock = findViewById(R.id.stockInfo);
        int state;
        try {
            int setting = pm.getApplicationEnabledSetting(STOCK_LAUNCHER);
            boolean off = setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    || setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                    || setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
            state = off ? R.string.about_stock_off : R.string.about_stock_on;
        } catch (IllegalArgumentException e) {
            state = R.string.about_stock_none;
        }
        stock.setText(item(getString(R.string.about_stock), getString(state)));
        stock.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_tv), null, null, null);
        // Its App info page: where it can be turned back on (tvctl's undo does the same from a computer).
        stock.setOnClickListener(v -> open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", STOCK_LAUNCHER, null))));
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
        Palette p = Palette.currentOrBaseline();
        float d = getResources().getDisplayMetrics().density;
        LinearLayout panel = dialogPanel(R.string.columns_label, p, d);

        LinearLayout options = dialogOptionList(panel, d);
        TextView current = null;
        TextView[] rows = new TextView[COLUMN_OPTIONS.length];
        for (int index = 0; index < COLUMN_OPTIONS.length; index++) {
            int value = COLUMN_OPTIONS[index];
            int radio = value == columns ? R.drawable.ic_radio_on : R.drawable.ic_radio_off;
            TextView row = optionRow(String.valueOf(value), radio);
            options.addView(row);
            rows[index] = row;
            if (value == columns) current = row;
        }

        Dialog dialog = showDialog(panel, p, config, d);
        for (int index = 0; index < rows.length; index++) {
            int value = COLUMN_OPTIONS[index];
            rows[index].setOnClickListener(v -> {
                setColumns(value);
                dialog.dismiss();
            });
        }
        (current != null ? current : rows[0]).requestFocus();
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

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0));
            window.setLayout((int) (440 * d), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
        return dialog;
    }

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
