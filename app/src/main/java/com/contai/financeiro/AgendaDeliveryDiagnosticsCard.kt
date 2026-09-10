package com.contai.financeiro

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun AgendaDeliveryDiagnosticsCard() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("contai_agenda", Context.MODE_PRIVATE)
    val expectedAt = prefs.getLong("agenda_last_expected_at", 0L)
    val firedAt = prefs.getLong("agenda_last_fired_at", 0L)
    val hasDelay = prefs.contains("agenda_last_delivery_delay_ms")
    val delayMs = if (hasDelay) prefs.getLong("agenda_last_delivery_delay_ms", 0L) else 0L
    val scheduleMode = prefs.getString("last_alarm_schedule_mode", "SEM_REGISTRO").orEmpty()
    val blocked = prefs.getBoolean("agenda_last_notification_blocked", false)

    fun formatTime(timestamp: Long): String = if (timestamp <= 0L) {
        "--:--:--"
    } else {
        SimpleDateFormat("HH:mm:ss", Locale("pt", "BR")).format(Date(timestamp))
    }

    val delayLabel = when {
        !hasDelay -> "Ainda sem teste registrado"
        abs(delayMs) < 1_000L -> "${delayMs} ms"
        abs(delayMs) < 60_000L -> String.format(Locale("pt", "BR"), "%.1f s", delayMs / 1_000.0)
        else -> String.format(Locale("pt", "BR"), "%.1f min", delayMs / 60_000.0)
    }

    val modeLabel = when (scheduleMode) {
        "EXACT" -> "Exato"
        "FALLBACK" -> "Compatível"
        else -> "Ainda não registrado"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Precisão do último lembrete", style = MaterialTheme.typography.titleMedium)
            Text("Modo de agendamento: $modeLabel", style = MaterialTheme.typography.bodyMedium)
            Text("Horário previsto: ${formatTime(expectedAt)}", style = MaterialTheme.typography.bodySmall)
            Text("Horário disparado: ${formatTime(firedAt)}", style = MaterialTheme.typography.bodySmall)
            Text(
                "Atraso medido: $delayLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = if (hasDelay && abs(delayMs) >= 60_000L) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (blocked) {
                Text(
                    "A notificação foi bloqueada porque a permissão de notificações não estava liberada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
