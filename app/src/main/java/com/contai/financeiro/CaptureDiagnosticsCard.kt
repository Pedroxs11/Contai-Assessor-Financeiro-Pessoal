package com.contai.financeiro

import android.content.Context
import android.content.Intent
import android.provider.Settings
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
    val aliveRecently = connected && lastAliveAt > 0L && System.currentTimeMillis() - lastAliveAt <= 45_000L

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
                "Último sinal: ${formattedTime(lastAliveAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Último evento: $lastEvent • ${formattedTime(lastEventAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
