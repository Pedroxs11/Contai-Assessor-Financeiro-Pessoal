package com.contai.financeiro

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val lastNotificationText = prefs.getString("debug_last_text", "").orEmpty()
    val lastParserAt = prefs.getLong("capture_last_parser_at", 0L)
    val lastParserResult = prefs.getString("capture_last_parser_result", "Sem registro").orEmpty()
    val lastSavedAt = prefs.getLong("capture_last_saved_at", 0L)
    val lastSavedPackage = prefs.getString("capture_last_saved_package", "").orEmpty()
    val recentEventsJson = prefs.getString("capture_recent_events", "[]") ?: "[]"
    val watchdogRecoveryAttempts = prefs.getInt("watchdog_recovery_attempts", 0)
    val watchdogLastRebindAt = prefs.getLong("watchdog_last_rebind_at", 0L)
    val watchdogLastCheckAt = prefs.getLong("watchdog_last_check_at", 0L)
    val aliveRecently = connected && lastAliveAt > 0L && System.currentTimeMillis() - lastAliveAt <= 45_000L
    val isXiaomiFamily = Build.MANUFACTURER.contains("xiaomi", true) || Build.BRAND.contains("xiaomi", true) || Build.BRAND.contains("redmi", true) || Build.BRAND.contains("poco", true)
    var recoveryFeedback by remember { mutableStateOf<String?>(null) }
    var showRecentEvents by remember { mutableStateOf(false) }

    fun formattedTime(timestamp: Long): String = if (timestamp <= 0L) "Ainda não registrado" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
    fun shortTime(timestamp: Long): String = if (timestamp <= 0L) "--:--" else SimpleDateFormat("HH:mm:ss", Locale("pt", "BR")).format(Date(timestamp))
    fun compactDebugText(value: String, limit: Int = 220): String {
        val normalized = value.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return "Sem texto recebido"
        return if (normalized.length <= limit) normalized else normalized.take(limit - 3) + "..."
    }

    val recentEvents = remember(recentEventsJson) {
        buildList {
            val json = runCatching { JSONArray(recentEventsJson) }.getOrElse { JSONArray() }
            for (i in json.length() - 1 downTo 0) {
                json.optJSONObject(i)?.let { item ->
                    add(
                        listOf(
                            item.optLong("timestamp", 0L).toString(),
                            item.optString("package", ""),
                            item.optString("title", ""),
                            item.optString("text", ""),
                            item.optString("classification", ""),
                            item.optString("type", ""),
                            if (item.has("amount")) item.optDouble("amount").toString() else ""
                        )
                    )
                }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Captura automática", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Status e diagnóstico do leitor de notificações.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (aliveRecently) "ATIVA" else "INTERROMPIDA", style = MaterialTheme.typography.labelLarge, color = if (aliveRecently) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }

            Text("1. Serviço Android: ${if (aliveRecently) "respondendo" else "sem resposta recente"}", style = MaterialTheme.typography.bodySmall)
            Text("2. Última notificação entregue: ${formattedTime(lastNotificationAt)}", style = MaterialTheme.typography.bodySmall)
            Text("3. Último processamento: ${formattedTime(lastParserAt)} • $lastParserResult", style = MaterialTheme.typography.bodySmall)
            Text("4. Último lançamento salvo: ${formattedTime(lastSavedAt)}${if (lastSavedPackage.isNotBlank()) " • $lastSavedPackage" else ""}", style = MaterialTheme.typography.bodySmall)

            Text("Último sinal do serviço: ${formattedTime(lastAliveAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Watchdog: ${if (watchdogRecoveryAttempts > 0) "$watchdogRecoveryAttempts tentativa(s) seguida(s) de recuperação" else "sem recuperação pendente"}",
                style = MaterialTheme.typography.bodySmall,
                color = if (watchdogRecoveryAttempts > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (watchdogLastRebindAt > 0L) {
                Text("Última tentativa automática: ${formattedTime(watchdogLastRebindAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (watchdogLastCheckAt > 0L) {
                Text("Última checagem saudável: ${formattedTime(watchdogLastCheckAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (lastNotificationAt > 0L) {
                val origin = when {
                    lastNotificationTitle.isNotBlank() && lastNotificationPackage.isNotBlank() -> "$lastNotificationTitle • $lastNotificationPackage"
                    lastNotificationTitle.isNotBlank() -> lastNotificationTitle
                    lastNotificationPackage.isNotBlank() -> lastNotificationPackage
                    else -> "Origem não identificada"
                }
                Text("Origem da última notificação: $origin", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Conteúdo recebido: ${compactDebugText(lastNotificationText)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Último evento do serviço: $lastEvent • ${formattedTime(lastEventAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (recentEvents.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showRecentEvents = !showRecentEvents },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showRecentEvents) "Ocultar últimos eventos" else "Ver últimos eventos (${recentEvents.size})")
                }

                if (showRecentEvents) {
                    Text("Últimas notificações recebidas", style = MaterialTheme.typography.labelLarge)
                    recentEvents.forEachIndexed { index, event ->
                        val timestamp = event[0].toLongOrNull() ?: 0L
                        val packageName = event[1]
                        val title = event[2]
                        val text = event[3]
                        val classification = event[4]
                        val type = event[5]
                        val amount = event[6].toDoubleOrNull()
                        val amountText = amount?.let { " • ${formatCurrency(it)}" }.orEmpty()

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${index + 1}. ${shortTime(timestamp)} • ${friendlyAppName(packageName)}", style = MaterialTheme.typography.labelMedium)
                                if (title.isNotBlank()) Text(compactDebugText(title, 90), style = MaterialTheme.typography.bodySmall)
                                Text(compactDebugText(text, 180), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$classification • $type$amountText", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            if (isXiaomiFamily) {
                Text("Neste aparelho, o sistema pode encerrar apps em segundo plano mesmo com a permissão ativa. Se a captura parar ao fechar o app, libere o Contai das restrições de bateria/autoinicialização.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Abrir configurações do app") }
            }

            if (!aliveRecently) {
                OutlinedButton(onClick = {
                    val listenerComponent = ComponentName(context, FinanceNotificationListener::class.java)
                    val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
                    val listenerEnabled = enabledListeners.split(':').any { it == listenerComponent.flattenToString() }
                    if (!listenerEnabled) { recoveryFeedback = "Ative primeiro o acesso às notificações do Contai."; return@OutlinedButton }
                    val requestAt = System.currentTimeMillis()
                    prefs.edit().putString("listener_lifecycle_event", "manualRecoveryCycleRequested").putLong("listener_lifecycle_at", requestAt).apply()
                    recoveryFeedback = "Reiniciando a captura..."
                    val handler = Handler(Looper.getMainLooper())
                    if (Build.VERSION.SDK_INT >= 34) {
                        NotificationListenerService.requestUnbind(listenerComponent)
                        handler.postDelayed({ NotificationListenerService.requestRebind(listenerComponent) }, 700L)
                    } else {
                        val packageManager = context.packageManager
                        packageManager.setComponentEnabledSetting(listenerComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                        handler.postDelayed({
                            packageManager.setComponentEnabledSetting(listenerComponent, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                            NotificationListenerService.requestRebind(listenerComponent)
                        }, 700L)
                    }
                    handler.postDelayed({
                        val refreshedAliveAt = prefs.getLong("listener_last_alive_at", 0L)
                        val refreshedConnected = prefs.getBoolean("service_connected", false)
                        val recovered = refreshedConnected && refreshedAliveAt >= requestAt && System.currentTimeMillis() - refreshedAliveAt <= 45_000L
                        recoveryFeedback = if (recovered) "Captura reativada." else "O Android não reativou a captura. Abra o acesso às notificações e desligue/ligue o Contai."
                    }, 5_000L)
                }, modifier = Modifier.fillMaxWidth()) { Text("Reiniciar captura") }
            }

            recoveryFeedback?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }, modifier = Modifier.fillMaxWidth()) {
                Text("Abrir acesso às notificações")
            }
        }
    }
}
