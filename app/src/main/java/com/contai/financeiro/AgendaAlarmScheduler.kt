package com.contai.financeiro

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object AgendaAlarmScheduler {
    private const val PREFS = "contai_agenda"

    fun canUseExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        return alarmManager.canScheduleExactAlarms()
    }

    fun exactAlarmSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Agenda o lembrete e devolve true quando conseguiu usar alarme exato.
     * Se o Android negar o acesso especial, cai automaticamente no modo compatível.
     */
    fun schedule(context: Context, triggerAtMillis: Long, pendingIntent: PendingIntent): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        val exactGranted = canUseExact(context)
        if (exactGranted) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                prefs.edit()
                    .putBoolean("agenda_last_alarm_exact", true)
                    .putLong("agenda_last_alarm_scheduled_at", now)
                    .putLong("agenda_last_alarm_trigger_at", triggerAtMillis)
                    .remove("agenda_last_alarm_fallback_reason")
                    .apply()
                return true
            } catch (_: SecurityException) {
                // A permissão pode ser revogada entre a checagem e o agendamento.
            }
        }

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
        prefs.edit()
            .putBoolean("agenda_last_alarm_exact", false)
            .putLong("agenda_last_alarm_scheduled_at", now)
            .putLong("agenda_last_alarm_trigger_at", triggerAtMillis)
            .putString(
                "agenda_last_alarm_fallback_reason",
                if (exactGranted) "security_exception" else "exact_alarm_not_granted"
            )
            .apply()
        return false
    }

    fun cancel(context: Context, pendingIntent: PendingIntent) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
    }
}
