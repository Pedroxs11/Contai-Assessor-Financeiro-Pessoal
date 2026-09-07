package com.contai.financeiro

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService

private const val WATCHDOG_PREFS = "contai_notifications"
private const val WATCHDOG_REQUEST_CODE = 7319
private const val WATCHDOG_INTERVAL_MS = 10 * 60 * 1000L
private const val WATCHDOG_STALE_AFTER_MS = 2 * 60 * 1000L

object CaptureWatchdog {
    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            WATCHDOG_REQUEST_CODE,
            Intent(context, CaptureWatchdogReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + WATCHDOG_INTERVAL_MS,
            pendingIntent
        )
    }
}

class CaptureWatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            val listenerComponent = ComponentName(
                context,
                FinanceNotificationListener::class.java
            )

            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ).orEmpty()

            val listenerEnabled = enabledListeners
                .split(':')
                .any { it == listenerComponent.flattenToString() }

            if (listenerEnabled) {
                val prefs = context.getSharedPreferences(WATCHDOG_PREFS, Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()
                val lastAliveAt = prefs.getLong("listener_last_alive_at", 0L)
                val stale = lastAliveAt == 0L || now - lastAliveAt > WATCHDOG_STALE_AFTER_MS

                if (stale) {
                    prefs.edit()
                        .putString("listener_lifecycle_event", "watchdogRebindRequested")
                        .putLong("listener_lifecycle_at", now)
                        .putLong("watchdog_last_rebind_at", now)
                        .apply()

                    NotificationListenerService.requestRebind(listenerComponent)
                } else {
                    prefs.edit()
                        .putLong("watchdog_last_check_at", now)
                        .apply()
                }
            }
        } finally {
            CaptureWatchdog.schedule(context)
        }
    }
}
