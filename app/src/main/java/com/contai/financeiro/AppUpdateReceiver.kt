package com.contai.financeiro

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService

class AppUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            context.getSharedPreferences("contai_notifications", Context.MODE_PRIVATE)
                .edit()
                .putString("listener_lifecycle_event", "rebindRequestedAfterUpdate")
                .putLong("listener_lifecycle_at", System.currentTimeMillis())
                .apply()

            NotificationListenerService.requestRebind(
                ComponentName(context, FinanceNotificationListener::class.java)
            )
            CaptureWatchdog.schedule(context)
        }
    }
}
