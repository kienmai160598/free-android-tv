package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LauncherReleaseTest {

    @Test
    public void readsTagsAndBuildSuffixes() {
        assertArrayEquals(new int[]{1, 0, 2}, LauncherRelease.versionParts("v1.0.2"));
        assertArrayEquals(new int[]{1, 0, 1}, LauncherRelease.versionParts("1.0.1 (dev)"));
        assertArrayEquals(new int[]{1, 0, 0}, LauncherRelease.versionParts("1.0.0 (beta)"));
    }

    @Test
    public void offersOnlyAHigherVersion() {
        assertTrue(LauncherRelease.isNewer("1.0.2", "1.0.1 (dev)"));
        assertTrue(LauncherRelease.isNewer("1.1", "1.0.9"));
        assertTrue(LauncherRelease.isNewer("1.0.10", "1.0.9"));
        assertFalse(LauncherRelease.isNewer("1.0.1", "1.0.1 (dev)"));
        assertFalse(LauncherRelease.isNewer("1.0", "1.0.0"));
        assertFalse(LauncherRelease.isNewer("1.0.0", "1.0.1"));
    }
}
