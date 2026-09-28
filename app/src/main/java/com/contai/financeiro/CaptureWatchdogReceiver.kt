package com.contai.financeiro

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService

private const val WATCHDOG_PREFS = "contai_notifications"
private const val WATCHDOG_REQUEST_CODE = 7319
private const val WATCHDOG_INTERVAL_MS = 5 * 60 * 1000L
private const val WATCHDOG_RECHECK_AFTER_RECOVERY_MS = 60 * 1000L
private const val WATCHDOG_STALE_AFTER_MS = 90 * 1000L
private const val WATCHDOG_FAST_RECOVERY_LIMIT = 3

object CaptureWatchdog {
    fun schedule(context: Context, delayMs: Long = WATCHDOG_INTERVAL_MS) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            WATCHDOG_REQUEST_CODE,
            Intent(context, CaptureWatchdogReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + delayMs,
            pendingIntent
        )
    }
}

class CaptureWatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        var nextDelayMs = WATCHDOG_INTERVAL_MS
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
                    val attempts = prefs.getInt("watchdog_recovery_attempts", 0) + 1
                    val useFastRecovery = attempts <= WATCHDOG_FAST_RECOVERY_LIMIT
                    prefs.edit()
                        .putString("listener_lifecycle_event", "watchdogRecoveryCycleRequested")
                        .putLong("listener_lifecycle_at", now)
                        .putLong("watchdog_last_rebind_at", now)
                        .putInt("watchdog_recovery_attempts", attempts)
                        .putString("watchdog_last_state", if (useFastRecovery) "RECOVERY_FAST" else "RECOVERY_BACKOFF")
                        .putLong("watchdog_last_check_at", now)
                        .apply()

                    if (Build.VERSION.SDK_INT >= 34) {
                        NotificationListenerService.requestUnbind(listenerComponent)
                        Handler(Looper.getMainLooper()).postDelayed({
                            NotificationListenerService.requestRebind(listenerComponent)
                        }, 700L)
                    } else {
                        NotificationListenerService.requestRebind(listenerComponent)
                    }

                    // Depois de uma recuperação, verifica novamente mais cedo para não esperar
                    // outros cinco minutos caso o fabricante mantenha o listener suspenso.
                    nextDelayMs = if (useFastRecovery) {
                        WATCHDOG_RECHECK_AFTER_RECOVERY_MS
                    } else {
                        // Evita ciclo agressivo infinito em OEMs que mantêm o listener suspenso.
                        WATCHDOG_INTERVAL_MS
                    }
                } else {
                    prefs.edit()
                        .putLong("watchdog_last_check_at", now)
                        .putString("watchdog_last_state", "HEALTHY")
                        .putInt("watchdog_recovery_attempts", 0)
                        .apply()
                }
            } else {
                context.getSharedPreferences(WATCHDOG_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putLong("watchdog_last_check_at", System.currentTimeMillis())
                    .putString("watchdog_last_state", "PERMISSION_DISABLED")
                    .apply()
            }
        } finally {
            CaptureWatchdog.schedule(context, nextDelayMs)
        }
    }
}
