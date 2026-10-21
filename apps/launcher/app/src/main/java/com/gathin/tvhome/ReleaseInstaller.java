package com.gathin.tvhome;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.IntConsumer;

final class ReleaseInstaller {

    private static final String SESSION_FILE = "km-launcher.apk";

    private ReleaseInstaller() {
    }

    static void install(Context context, LauncherRelease release, IntConsumer percentDone)
            throws IOException {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        params.setSize(release.apkSize);
        int sessionId = installer.createSession(params);

        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            try {
                String downloadedSha256 = download(release, session, percentDone);
                if (!downloadedSha256.equalsIgnoreCase(release.apkSha256)) {
                    throw new IOException("checksum mismatch");
                }

                session.commit(statusReceiver(context, sessionId).getIntentSender());
            } catch (IOException | RuntimeException e) {
                session.abandon();
                throw e;
            }
        }
    }

    private static String download(LauncherRelease release, PackageInstaller.Session session,
            IntConsumer percentDone) throws IOException {
        HttpURLConnection connection = LauncherRelease.open(release.apkUrl);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + connection.getResponseCode());
            }

            MessageDigest sha256 = sha256();
            try (InputStream body = connection.getInputStream();
                 OutputStream apk = session.openWrite(SESSION_FILE, 0, release.apkSize)) {
                byte[] buffer = new byte[64 * 1024];
                long written = 0;
                int reportedPercent = -1;
                for (int read; (read = body.read(buffer)) > 0; ) {
                    apk.write(buffer, 0, read);
                    sha256.update(buffer, 0, read);
                    written += read;
                    if (written > release.apkSize) throw new IOException("download too large");

                    int percent = (int) (written * 100 / release.apkSize);
                    if (percent != reportedPercent) percentDone.accept(percent);
                    reportedPercent = percent;
                }

                if (written != release.apkSize) throw new IOException("download incomplete");
                session.fsync(apk);
            }

            return hex(sha256.digest());
        } finally {
            connection.disconnect();
        }
    }

    private static PendingIntent statusReceiver(Context context, int sessionId) {
        Intent status = new Intent(context, InstallStatusReceiver.class);
        int mutable = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
        return PendingIntent.getBroadcast(context, sessionId, status,
                PendingIntent.FLAG_UPDATE_CURRENT | mutable);
    }

    private static MessageDigest sha256() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) text.append(String.format("%02x", value & 0xff));
        return text.toString();
    }
}
