package com.gathin.tvhome;

import android.content.Context;
import android.util.AttributeSet;

import androidx.recyclerview.widget.RecyclerView;

import java.util.function.IntSupplier;

/**
 * Home's grid. It runs from the top of the screen so rows can scroll up under the header, which floats over it;
 * the top {@link #underlap()} pixels are the header's, and tiles are sized and kept on screen as if the grid began
 * below them.
 */
public class HomeGrid extends RecyclerView {

    private int underlap;
    private IntSupplier underlapSource = () -> 0;

    public HomeGrid(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** How far the header reaches down over the grid. */
    int underlap() {
        return underlap;
    }

    /** The height below the header, which the tiles are sized for. */
    int roomBelowHeader() {
        return getHeight() - underlap;
    }

    /**
     * Where the header's bottom is, read at every layout: the whole screen is measured before any of it is laid
     * out, so the header's height is known before the first tiles are made.
     */
    void setUnderlapSource(IntSupplier source) {
        underlapSource = source;
    }

    /** Lays the grid out again when the header's bottom moved. */
    void headerChanged() {
        if (underlapSource.getAsInt() != underlap) requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        underlap = underlapSource.getAsInt();
        super.onLayout(changed, l, t, r, b);
    }
}
