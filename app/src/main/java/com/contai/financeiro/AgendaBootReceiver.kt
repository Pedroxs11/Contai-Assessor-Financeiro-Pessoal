package com.contai.financeiro

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray

private const val BOOT_AGENDA_PREFS = "contai_agenda"
private const val BOOT_AGENDA_ITEMS_KEY = "agenda_items"

class AgendaBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences(BOOT_AGENDA_PREFS, Context.MODE_PRIVATE)
        val items = JSONArray(prefs.getString(BOOT_AGENDA_ITEMS_KEY, "[]") ?: "[]")
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val completed = item.optBoolean("completed", false)
            val dueAt = item.optLong("dueAt", 0L)
            val itemId = item.optLong("id", 0L)
            val title = item.optString("title", "Lembrete financeiro")

            if (completed || dueAt <= now || itemId == 0L) continue

            val reminderIntent = Intent(context, AgendaReminderReceiver::class.java)
                .putExtra("itemId", itemId)
                .putExtra("title", title)
                .apply {
                    if (item.has("amount")) {
                        putExtra("amount", item.optDouble("amount", 0.0))
                    }
                }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                itemId.hashCode(),
                reminderIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                dueAt,
                pendingIntent
            )
        }
    }
}
