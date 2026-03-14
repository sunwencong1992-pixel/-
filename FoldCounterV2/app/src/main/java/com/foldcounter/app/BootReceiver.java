package com.foldcounter.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

/**
 * 开机自启：手机重启后自动启动后台服务，无需手动打开 APP。
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            Intent serviceIntent = new Intent(context, FoldCounterService.class);
            ContextCompat.startForegroundService(context, serviceIntent);
        }
    }
}
