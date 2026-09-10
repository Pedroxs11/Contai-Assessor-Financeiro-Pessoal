package com.contai.financeiro

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object AgendaAlarmScheduler {
    private const val PREFS = "contai_agenda"
    private const val EXACT_ALARM_PROMPT_SHOWN = "exact_alarm_prompt_shown"

    fun schedule(context: Context, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        val canUseExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        val scheduledExact = if (canUseExact) {
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }.isSuccess
        } else {
            false
        }

        if (!scheduledExact) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
            maybeRequestExactAlarmAccess(context)
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("last_alarm_schedule_mode", if (scheduledExact) "EXACT" else "FALLBACK")
            .putLong("last_alarm_scheduled_at", System.currentTimeMillis())
            .putLong("last_alarm_trigger_at", triggerAtMillis)
            .apply()
    }

    private fun maybeRequestExactAlarmAccess(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context !is Activity) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(EXACT_ALARM_PROMPT_SHOWN, false)) return

        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
        }

        val opened = runCatching {
            context.startActivity(intent)
        }.isSuccess

        if (opened) {
            prefs.edit().putBoolean(EXACT_ALARM_PROMPT_SHOWN, true).apply()
        }
    }

    fun cancel(context: Context, pendingIntent: PendingIntent) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
    }
}
