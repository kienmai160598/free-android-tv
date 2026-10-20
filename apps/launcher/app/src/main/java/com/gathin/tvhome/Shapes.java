package com.gathin.tvhome;

/**
 * Material 3 Expressive corner scale in dp (androidx material3 ShapeTokens), and the morphs built on it.
 * Expressive shapes change with state, and the change itself runs on a spatial spring.
 */
final class Shapes {

    static final float EXTRA_SMALL = 4f;
    static final float SMALL = 8f;
    static final float MEDIUM = 12f;
    static final float LARGE = 16f;
    static final float LARGE_INCREASED = 20f;
    static final float EXTRA_LARGE = 28f;
    static final float EXTRA_LARGE_INCREASED = 32f;
    static final float EXTRA_EXTRA_LARGE = 48f;

    /**
     * App tiles at their 180dp design width: one shape everywhere, focused or not, so every tile reads as
     * the same app shape; focus is shown by the active indicator behind the tile and a spring zoom.
     * The containers around tiles follow from it (outer radius = inner radius + padding):
     * indicator 20 + 4 = 24, dock 20 + 12 = 32 (extra-large-increased), 8dp of dock left around the indicator.
     */
    static final float TILE_REST = LARGE_INCREASED;
    static final float TILE_FOCUSED = LARGE_INCREASED;

    /** The dock's container and popup panels. */
    static final float CONTAINER = EXTRA_LARGE_INCREASED;

    /** M3 focus indicator: a stroke outside the component, offset from its edge. */
    static final float FOCUS_RING_WIDTH = 3f;
    static final float FOCUS_RING_OFFSET = 2f;

    private Shapes() {
    }
}
