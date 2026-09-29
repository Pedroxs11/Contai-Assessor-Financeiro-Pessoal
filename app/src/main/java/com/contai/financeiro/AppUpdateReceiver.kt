package com.contai.financeiro

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService

class AppUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val shouldRequestRebind = when (intent?.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> true
            else -> false
        }

        if (shouldRequestRebind) {
            NotificationListenerService.requestRebind(
                ComponentName(
                    context,
                    FinanceNotificationListener::class.java
                )
            )
        }
    }
}
