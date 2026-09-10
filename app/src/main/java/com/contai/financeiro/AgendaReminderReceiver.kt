package com.contai.financeiro

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import org.json.JSONArray

// Channel v2: Android preserva as configuracoes do canal antigo mesmo apos atualizar o app.
// Um novo ID garante que som/vibracao passem a valer para quem ja instalou versoes anteriores.
private const val AGENDA_CHANNEL_ID = "agenda_reminders_v2"
private const val AGENDA_CHANNEL_NAME = "Lembretes da Agenda"
private const val AGENDA_PREFS = "contai_agenda"
private const val AGENDA_ITEMS_KEY = "agenda_items"

class AgendaReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title")?.ifBlank { "Lembrete" }
            ?: "Lembrete"
        val note = intent.getStringExtra("note").orEmpty()
        val itemId = intent.getLongExtra("itemId", System.currentTimeMillis())
        val firedAt = System.currentTimeMillis()

        recordDeliveryDiagnostic(context, itemId, firedAt)

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            context.getSharedPreferences(AGENDA_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("agenda_last_notification_blocked", true)
                .apply()
            return
        }

        context.getSharedPreferences(AGENDA_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("agenda_last_notification_blocked", false)
            .apply()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                AGENDA_CHANNEL_ID,
                AGENDA_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisos e lembretes da Agenda do Contai"
                enableVibration(true)
                setSound(defaultSound, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, ContaiShellActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            itemId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = note.ifBlank { "Você tem um lembrete agendado." }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, AGENDA_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
                .setSound(defaultSound)
                .setVibrate(longArrayOf(0L, 250L, 150L, 250L))
                .setPriority(Notification.PRIORITY_HIGH)
        }

        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(openAppPendingIntent)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(itemId.hashCode(), notification)
    }

    private fun recordDeliveryDiagnostic(context: Context, itemId: Long, firedAt: Long) {
        val prefs = context.getSharedPreferences(AGENDA_PREFS, Context.MODE_PRIVATE)
        val items = runCatching {
            JSONArray(prefs.getString(AGENDA_ITEMS_KEY, "[]") ?: "[]")
        }.getOrElse { JSONArray() }

        var dueAt = 0L
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            if (item.optLong("id", Long.MIN_VALUE) == itemId) {
                dueAt = item.optLong("dueAt", 0L)
                break
            }
        }

        val delayMs = if (dueAt > 0L) firedAt - dueAt else Long.MIN_VALUE
        prefs.edit()
            .putLong("agenda_last_fired_item_id", itemId)
            .putLong("agenda_last_fired_at", firedAt)
            .putLong("agenda_last_expected_at", dueAt)
            .apply {
                if (delayMs != Long.MIN_VALUE) {
                    putLong("agenda_last_delivery_delay_ms", delayMs)
                } else {
                    remove("agenda_last_delivery_delay_ms")
                }
            }
            .apply()
    }
}
