package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Resources;

/**
 * Loads Home's four font families side by side before its layout needs them. Each one parses the same
 * variable font on its own, about 17 ms apiece on a TCL G07, and one after another they were the slowest
 * part of inflating Home. Resources keeps what it loaded, so the layout then finds them ready.
 */
final class Fonts {
    private Fonts() { }

    private static final int[] FAMILIES = {R.font.sans_display, R.font.sans_headline, R.font.sans_medium,
            R.font.sans_regular};

    static void preload(Context context) {
        Resources res = context.getResources();
        Thread[] loaders = new Thread[FAMILIES.length];
        for (int i = 0; i < FAMILIES.length; i++) {
            int family = FAMILIES[i];
            loaders[i] = new Thread(() -> {
                try {
                    res.getFont(family);
                } catch (Resources.NotFoundException e) {
                    // The layout loads it again and falls back the way it always did.
                }
            }, "font-preload");
            loaders[i].start();
        }
        try {
            for (Thread loader : loaders) loader.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
