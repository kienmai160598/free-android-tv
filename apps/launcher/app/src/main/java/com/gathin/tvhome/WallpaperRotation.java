package com.gathin.tvhome;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

final class WallpaperRotation {
    static List<File> images(File directory) {
        List<File> result = new ArrayList<>();
        File[] entries = directory == null ? null : directory.listFiles();
        if (entries == null) return result;
        Arrays.sort(entries, Comparator.comparing(File::getName));
        for (File entry : entries) {
            String name = entry.getName().toLowerCase(Locale.ROOT);
            if (entry.isFile() && entry.length() > 0 && entry.length() <= Contract.Theme.MAX_WALLPAPER_BYTES
                    && hasImageExtension(name)) {
                result.add(entry);
                if (result.size() == Contract.Theme.MAX_WALLPAPERS) break;
            }
        }
        return result;
    }

    private static boolean hasImageExtension(String name) {
        for (String extension : Contract.Theme.IMAGE_EXTENSIONS) {
            if (name.endsWith(extension)) return true;
        }
        return false;
    }

    static File find(List<File> images, String name) {
        for (File image : images) if (image.getName().equals(name)) return image;
        return null;
    }

    static File pick(List<File> images, String previous, Random random) {
        List<File> candidates = new ArrayList<>(images);
        if (candidates.size() > 1) candidates.removeIf(image -> image.getName().equals(previous));
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    static boolean due(long previous, long now, long interval) {
        return interval > 0 && (previous == 0 || now < previous || now - previous >= interval);
    }
}
