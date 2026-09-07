package com.contai.financeiro

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
fun CaptureDiagnosticsCard() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("contai_notifications", Context.MODE_PRIVATE)
    val connected = prefs.getBoolean("service_connected", false)
    val lastAliveAt = prefs.getLong("listener_last_alive_at", 0L)
    val lastEvent = prefs.getString("listener_lifecycle_event", "Sem registro") ?: "Sem registro"
    val lastEventAt = prefs.getLong("listener_lifecycle_at", 0L)
    val lastNotificationAt = prefs.getLong("debug_last_event_at", 0L)
    val lastNotificationPackage = prefs.getString("debug_last_package", "").orEmpty()
    val lastNotificationTitle = prefs.getString("debug_last_title", "").orEmpty()
    val aliveRecently = connected && lastAliveAt > 0L && System.currentTimeMillis() - lastAliveAt <= 45_000L
    var recoveryFeedback by remember { mutableStateOf<String?>(null) }

    fun formattedTime(timestamp: Long): String = if (timestamp <= 0L) {
        "Ainda não registrado"
    } else {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Captura automática", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Status e diagnóstico do leitor de notificações.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    if (aliveRecently) "ATIVA" else "INTERROMPIDA",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (aliveRecently) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            Text(
                "Último sinal do serviço: ${formattedTime(lastAliveAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Última notificação recebida: ${formattedTime(lastNotificationAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (lastNotificationAt > 0L) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (lastNotificationAt > 0L) {
                val origin = when {
                    lastNotificationTitle.isNotBlank() && lastNotificationPackage.isNotBlank() ->
                        "$lastNotificationTitle • $lastNotificationPackage"
                    lastNotificationTitle.isNotBlank() -> lastNotificationTitle
                    lastNotificationPackage.isNotBlank() -> lastNotificationPackage
                    else -> "Origem não identificada"
                }
                Text(
                    "Origem da última notificação: $origin",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Último evento do serviço: $lastEvent • ${formattedTime(lastEventAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!aliveRecently) {
                OutlinedButton(
                    onClick = {
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

                        if (!listenerEnabled) {
                            recoveryFeedback = "Ative primeiro o acesso às notificações do Contai."
                            return@OutlinedButton
                        }

                        val requestAt = System.currentTimeMillis()
                        prefs.edit()
                            .putString("listener_lifecycle_event", "manualRecoveryCycleRequested")
                            .putLong("listener_lifecycle_at", requestAt)
                            .apply()
                        recoveryFeedback = "Reiniciando a captura..."

                        val handler = Handler(Looper.getMainLooper())

                        if (Build.VERSION.SDK_INT >= 34) {
                            NotificationListenerService.requestUnbind(listenerComponent)
                            handler.postDelayed({
                                NotificationListenerService.requestRebind(listenerComponent)
                            }, 700L)
                        } else {
                            val packageManager = context.packageManager
                            packageManager.setComponentEnabledSetting(
                                listenerComponent,
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                PackageManager.DONT_KILL_APP
                            )
                            handler.postDelayed({
                                packageManager.setComponentEnabledSetting(
                                    listenerComponent,
                                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                                    PackageManager.DONT_KILL_APP
                                )
                                NotificationListenerService.requestRebind(listenerComponent)
                            }, 700L)
                        }

                        handler.postDelayed({
                            val refreshedAliveAt = prefs.getLong("listener_last_alive_at", 0L)
                            val refreshedConnected = prefs.getBoolean("service_connected", false)
                            val recovered = refreshedConnected &&
                                refreshedAliveAt >= requestAt &&
                                System.currentTimeMillis() - refreshedAliveAt <= 45_000L

                            recoveryFeedback = if (recovered) {
                                "Captura reativada."
                            } else {
                                "O Android não reativou a captura. Abra o acesso às notificações e desligue/ligue o Contai."
                            }
                        }, 5_000L)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reiniciar captura")
                }
            }

            recoveryFeedback?.let { feedback ->
                Text(
                    feedback,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Abrir acesso às notificações")
            }
        }
    }
}
