package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class LauncherRelease {

    private static final String LATEST_URL =
            "https://api.github.com/repos/kienmai160598/freeTV/releases/latest";
    private static final String APK_ASSET = "km-launcher.apk";
    private static final String SHA256_PREFIX = "sha256:";
    private static final int TIMEOUT_MS = 15_000;
    private static final int MAX_JSON_BYTES = 256 * 1024;

    final String version;
    final String apkUrl;
    final long apkSize;
    final String apkSha256;

    private LauncherRelease(String version, String apkUrl, long apkSize, String apkSha256) {
        this.version = version;
        this.apkUrl = apkUrl;
        this.apkSize = apkSize;
        this.apkSha256 = apkSha256;
    }

    static LauncherRelease latest() throws IOException {
        HttpURLConnection connection = open(LATEST_URL);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            return parse(new JSONObject(readBody(connection)));
        } catch (JSONException e) {
            throw new IOException("unreadable release", e);
        } finally {
            connection.disconnect();
        }
    }

    boolean isNewerThan(String installedVersion) {
        return isNewer(version, installedVersion);
    }

    static boolean isNewer(String offeredVersion, String installedVersion) {
        int[] offered = versionParts(offeredVersion);
        int[] installed = versionParts(installedVersion);
        for (int i = 0; i < Math.max(offered.length, installed.length); i++) {
            int offeredPart = i < offered.length ? offered[i] : 0;
            int installedPart = i < installed.length ? installed[i] : 0;
            if (offeredPart != installedPart) return offeredPart > installedPart;
        }

        return false;
    }

    static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", "tvhome-launcher");
        return connection;
    }

    static int[] versionParts(String version) {
        String numbers = version.replaceFirst("^[vV]", "").replaceFirst("[^0-9.].*$", "");
        String[] parts = numbers.split("\\.");
        int[] values = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            values[i] = parts[i].isEmpty() ? 0 : Integer.parseInt(parts[i]);
        }

        return values;
    }

    private static LauncherRelease parse(JSONObject release) throws JSONException, IOException {
        String tag = release.getString("tag_name");
        JSONArray assets = release.getJSONArray("assets");
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (!APK_ASSET.equals(asset.optString("name"))) continue;

            String digest = asset.optString("digest");
            if (!digest.startsWith(SHA256_PREFIX)) throw new IOException("release has no checksum");
            return new LauncherRelease(tag.replaceFirst("^[vV]", ""),
                    asset.getString("browser_download_url"), asset.getLong("size"),
                    digest.substring(SHA256_PREFIX.length()));
        }

        throw new IOException("release has no " + APK_ASSET);
    }

    private static String readBody(HttpURLConnection connection) throws IOException {
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP " + connection.getResponseCode());
        }

        try (InputStream body = connection.getInputStream()) {
            ByteArrayOutputStream collected = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            for (int read; (read = body.read(buffer)) > 0; ) {
                collected.write(buffer, 0, read);
                if (collected.size() > MAX_JSON_BYTES) throw new IOException("release too large");
            }

            return collected.toString(StandardCharsets.UTF_8.name());
        }
    }
}
