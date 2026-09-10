package com.contai.financeiro

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray

private const val EXACT_ALARM_AGENDA_PREFS = "contai_agenda"
private const val EXACT_ALARM_AGENDA_ITEMS_KEY = "agenda_items"

class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (intent?.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (!alarmManager.canScheduleExactAlarms()) return

        val prefs = context.getSharedPreferences(EXACT_ALARM_AGENDA_PREFS, Context.MODE_PRIVATE)
        val items = JSONArray(prefs.getString(EXACT_ALARM_AGENDA_ITEMS_KEY, "[]") ?: "[]")
        val now = System.currentTimeMillis()

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val completed = item.optBoolean("completed", false)
            val dueAt = item.optLong("dueAt", 0L)
            val itemId = item.optLong("id", 0L)
            if (completed || dueAt <= now || itemId == 0L) continue

            val reminderIntent = Intent(context, AgendaReminderReceiver::class.java)
                .putExtra("itemId", itemId)
                .putExtra("title", item.optString("title", "Lembrete"))
                .putExtra("note", item.optString("note", item.optString("observation", "")))

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                itemId.hashCode(),
                reminderIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            AgendaAlarmScheduler.schedule(context, dueAt, pendingIntent)
        }

        prefs.edit()
            .putLong("exact_alarm_permission_granted_at", now)
            .apply()
    }
}
