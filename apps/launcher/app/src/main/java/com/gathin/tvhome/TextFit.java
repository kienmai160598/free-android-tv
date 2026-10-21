package com.gathin.tvhome;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

/**
 * Single-line widget text that shrinks to fit its width, down to MIN_SP, instead of ending in "…": a card is
 * a fixed size and its lines change with the weather, the language and the page. Refits whenever the text or
 * the width changes. Platform auto-size is not used: with a wrap_content height it is unreliable.
 */
final class TextFit {
    private TextFit() { }

    private static final float MIN_SP = 9f;
    /** TextView ends a line in "…" a few pixels before measureText says it is too wide. */
    private static final float SLACK_DP = 4f;

    /** One line that fills its own width (a label, a detail line). */
    static void attach(TextView line) {
        float base = line.getTextSize();
        Runnable fit = () -> {
            float room = line.getWidth() - line.getPaddingLeft() - line.getPaddingRight();
            setSize(line, base * scale(room, width(line, base) + slack(line)));
        };
        refitOn(line, line, fit);
    }

    /**
     * A meter row's name and value, side by side in row: the name takes what the value leaves, so a long value
     * cut the name short. Both shrink together until the two fit in the room they share.
     */
    static void attachRow(TextView name, TextView value, View row) {
        float nameBase = name.getTextSize(), valueBase = value.getTextSize();
        Runnable fit = () -> {
            float room = name.getWidth() - name.getPaddingLeft() - name.getPaddingRight()
                    + value.getWidth() - value.getPaddingLeft() - value.getPaddingRight();
            float scale = scale(room, width(name, nameBase) + width(value, valueBase) + slack(name));
            setSize(name, nameBase * scale);
            setSize(value, valueBase * scale);
        };
        refitOn(value, row, fit);
        refitOn(name, row, fit);
    }

    private static void refitOn(TextView text, View sized, Runnable fit) {
        text.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                sized.post(fit);
            }
        });
        sized.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) v.post(fit);
        });
    }

    /** Down to the floor of MIN_SP; 1 when it already fits or the width is not known yet. */
    private static float scale(float room, float needed) {
        return room <= 0 || needed <= room ? 1f : room / needed;
    }

    private static float slack(View view) {
        return SLACK_DP * view.getResources().getDisplayMetrics().density;
    }

    private static float width(TextView text, float sizePx) {
        float now = text.getPaint().getTextSize();
        text.getPaint().setTextSize(sizePx);
        float width = text.getPaint().measureText(text.getText().toString());
        text.getPaint().setTextSize(now);
        return width;
    }

    private static void setSize(TextView text, float px) {
        float min = MIN_SP * text.getResources().getDisplayMetrics().scaledDensity;
        float size = Math.max(min, px);
        if (Math.abs(text.getTextSize() - size) > 0.5f) text.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
    }
}
