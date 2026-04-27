package com.example.aura;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class ServiceRestartReceiver extends BroadcastReceiver {

    public static final String ACTION_RESTART_SERVICE = "com.example.aura.ACTION_RESTART_SERVICE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || ACTION_RESTART_SERVICE.equals(action)) {
            try {
                Intent svcIntent = new Intent(context, ChatNotificationForegroundService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, svcIntent);
                } else {
                    context.startService(svcIntent);
                }
            } catch (Exception e) {
                Log.w("ServiceRestartRcvr", "Failed to restart service", e);
            }
        }
    }
}
