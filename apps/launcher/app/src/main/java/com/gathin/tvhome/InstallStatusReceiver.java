package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

public final class InstallStatusReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            askToConfirm(context, intent);
            return;
        }

        boolean declined = status == PackageInstaller.STATUS_FAILURE_ABORTED;
        if (status != PackageInstaller.STATUS_SUCCESS && !declined) {
            String reason = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            String message = context.getString(R.string.update_install_failed,
                    reason != null ? reason : String.valueOf(status));
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
    }

    @SuppressWarnings("deprecation")
    private static void askToConfirm(Context context, Intent intent) {
        Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
        if (confirm == null) return;

        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(confirm);
    }
}
