package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
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

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private TextView animationsChoice;
    private TextView advancedChoice;
    private M3Switch animationsSwitch;
    private M3Switch advancedSwitch;
    private M3Switch darkSwitch;
    private M3Switch widgetsSwitch;
    private int columns;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Palette.loadDarkMode(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(Palette.currentOrBaseline().surface));
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        columns = config.columns;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            LinearLayout row = (LinearLayout) columnChoices[0].getParent();
            row.removeAllViews();
            for (int i = columnChoices.length - 1; i >= 0; i--) row.addView(columnChoices[i]);
        }
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
        Palette palette = Palette.currentOrBaseline();
        animationsSwitch = new M3Switch(density, palette, config.animations);
        advancedSwitch = new M3Switch(density, palette, config.animations);
        animationsChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, animationsSwitch, null);
        advancedChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, advancedSwitch, null);
        ((TextView) findViewById(R.id.tvSettingsChoice)).setText(getString(R.string.tv_settings));
        TextView darkChoice = findViewById(R.id.darkThemeChoice);
        darkChoice.setText(getString(R.string.dark_theme));
        darkSwitch = new M3Switch(density, palette, config.animations);
        darkSwitch.setChecked(Palette.darkMode());
        darkChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, darkSwitch, null);
        darkChoice.setOnClickListener(v -> {
            boolean dark = !Palette.darkMode();
            choices.edit().putBoolean(Palette.DARK_KEY, dark).apply();
            darkSwitch.setChecked(dark);
            // Republish the scheme in the new appearance: Home behind follows through its listener, and
            // this page re-skins in place so focus and the switch's motion carry on.
            Palette.setDarkMode(dark);
            reskin(config);
        });
        TextView widgetsChoice = findViewById(R.id.widgetsChoice);
        widgetsChoice.setText(getString(R.string.widgets));
        boolean widgetsDefault = getResources().getBoolean(R.bool.widgets);
        widgetsSwitch = new M3Switch(density, palette, config.animations);
        widgetsSwitch.setChecked(choices.getBoolean(WidgetShelf.PREF_KEY, widgetsDefault));
        widgetsChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, widgetsSwitch, null);
        widgetsChoice.setOnClickListener(v -> {
            // Home reads it when it shows again.
            boolean on = !choices.getBoolean(WidgetShelf.PREF_KEY, widgetsDefault);
            choices.edit().putBoolean(WidgetShelf.PREF_KEY, on).apply();
            widgetsSwitch.setChecked(on);
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_SETTINGS)));
        refresh();
        View content = ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        M3Skin.apply(content, Palette.currentOrBaseline(), config.animations, config.reducedMotion);
        columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    /** Paints this page again in the current scheme (after a dark/light change). */
    private void reskin(Config config) {
        Palette p = Palette.currentOrBaseline();
        View content = ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        M3Skin.apply(content, p, config.animations, config.reducedMotion);
        for (M3Switch s : new M3Switch[]{animationsSwitch, advancedSwitch, darkSwitch, widgetsSwitch}) s.setPalette(p);
        getWindow().setBackgroundDrawable(new ColorDrawable(p.surface));
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        setResult(RESULT_OK);
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        // Up from the first list item goes back to the current choice, not whichever is geometrically above.
        animationsChoice.setNextFocusUpId(columnChoices[Math.max(0, Math.min(columns - 4, 3))].getId());
        animationsChoice.setText(item(title(getString(R.string.animations_state, "")), null));
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
