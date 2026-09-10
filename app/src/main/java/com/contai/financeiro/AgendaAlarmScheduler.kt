package com.contai.financeiro

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build

object AgendaAlarmScheduler {
    fun schedule(context: Context, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        val canUseExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canUseExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    fun cancel(context: Context, pendingIntent: PendingIntent) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
    }
}
