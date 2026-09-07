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
import android.os.Build

private const val AGENDA_CHANNEL_ID = "agenda_reminders"
private const val AGENDA_CHANNEL_NAME = "Lembretes da Agenda"

class AgendaReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title")?.ifBlank { "Lembrete" }
            ?: "Lembrete"
        val note = intent.getStringExtra("note").orEmpty()
        val itemId = intent.getLongExtra("itemId", System.currentTimeMillis())

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AGENDA_CHANNEL_ID,
                AGENDA_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisos e lembretes da Agenda do Contai"
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
        }

        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(itemId.hashCode(), notification)
    }
}
