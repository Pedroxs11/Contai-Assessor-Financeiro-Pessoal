package com.contai.financeiro

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService

class AppUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            NotificationListenerService.requestRebind(
                ComponentName(
                    context,
                    FinanceNotificationListener::class.java
                )
            )
        }
    }
}
