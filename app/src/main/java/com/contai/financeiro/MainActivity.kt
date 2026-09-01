package com.contai.financeiro

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ContaiApp()
        }
    }
}

@Composable
fun ContaiApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var lastPackage by remember { mutableStateOf("") }
    var lastTitle by remember { mutableStateOf("") }
    var lastText by remember { mutableStateOf("") }
    var lastAmount by remember { mutableStateOf("") }
    var lastType by remember { mutableStateOf("") }
    var lastConfidence by remember { mutableStateOf(0) }
    var lastClassification by remember { mutableStateOf("") }
    var notificationAccess by remember { mutableStateOf(false) }
    var serviceConnected by remember { mutableStateOf(false) }
    var debugLastEventAt by remember { mutableStateOf(0L) }
    var debugLastPackage by remember { mutableStateOf("") }
    var debugLastTitle by remember { mutableStateOf("") }
    var transactionHistory by remember { mutableStateOf(listOf<TransactionRecord>()) }
    var transactionToCorrect by remember { mutableStateOf<TransactionRecord?>(null) }

    fun refreshData() {
        val prefs = context.getSharedPreferences(
            "contai_notifications",
            Context.MODE_PRIVATE
        )

        lastPackage = prefs.getString("last_package", "") ?: ""
        lastTitle = prefs.getString("last_title", "") ?: ""
        lastText = prefs.getString("last_text", "") ?: ""
        lastAmount = prefs.getString("last_amount", "") ?: ""
        lastType = prefs.getString("last_type", "") ?: ""
        lastConfidence = prefs.getInt("last_confidence", 0)
        lastClassification = prefs.getString("last_classification", "") ?: ""
        serviceConnected = prefs.getBoolean("service_connected", false)
        debugLastEventAt = prefs.getLong("debug_last_event_at", 0L)
        debugLastPackage = prefs.getString("debug_last_package", "") ?: ""
        debugLastTitle = prefs.getString("debug_last_title", "") ?: ""

        val historyJson = JSONArray(
            prefs.getString("transaction_history", "[]") ?: "[]"
        )

        val historyItems = mutableListOf<TransactionRecord>()

        for (i in historyJson.length() - 1 downTo 0) {
            val item = historyJson.getJSONObject(i)

            val amount = if (item.has("amount")) {
                item.optDouble("amount")
            } else {
                null
            }

            val type = item.optString("type", "NAO_IDENTIFICADO")

            val category = item.optString("category").ifBlank {
                when (type) {
                    "ENTRADA" -> "Receitas"
                    "DESPESA" -> "Outros"
                    else -> "Não categorizado"
                }
            }

            historyItems.add(
                TransactionRecord(
                    amount = amount,
                    type = type,
                    timestamp = item.optLong("timestamp", 0L),
                    category = category,
                    status = item.optString("classification", ""),
                    source = item.optString("package", ""),
                    title = item.optString("title", ""),
                    text = item.optString("text", ""),
                    confidence = item.optInt("confidence", 0)
                )
            )
        }

        transactionHistory = historyItems

        val enabledListeners =
            Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ).orEmpty()

        notificationAccess =
            enabledListeners.contains(context.packageName)
    }

    fun confirmTransaction(timestamp: Long) {
        val prefs = context.getSharedPreferences(
            "contai_notifications",
            Context.MODE_PRIVATE
        )

        val historyJson = JSONArray(
            prefs.getString("transaction_history", "[]") ?: "[]"
        )

        for (i in 0 until historyJson.length()) {
            val item = historyJson.getJSONObject(i)

            if (item.optLong("timestamp", 0L) == timestamp) {
                item.put("classification", "CONFIRMADA")
                break
            }
        }

        prefs.edit()
            .putString("transaction_history", historyJson.toString())
            .apply()

        refreshData()
    }

    if (transactionToCorrect != null) {
        val transaction = transactionToCorrect!!
        var selectedType by remember(transaction.timestamp) { mutableStateOf(transaction.type) }

        AlertDialog(
            onDismissRequest = {
                transactionToCorrect = null
            },
            title = {
                Text("Corrigir transação")
            },
            text = {
                Column {
                    Text("Valor: R$ ${transaction.amount ?: 0.0}")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Tipo")

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedType == "ENTRADA",
                            onClick = { selectedType = "ENTRADA" },
                            label = { Text("Entrada") }
                        )

                        FilterChip(
                            selected = selectedType == "DESPESA",
                            onClick = { selectedType = "DESPESA" },
                            label = { Text("Despesa") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Categoria: ${transaction.category}")
                    Text("Origem: ${transaction.source}")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val prefs = context.getSharedPreferences(
                            "contai_notifications",
                            Context.MODE_PRIVATE
                        )

                        val historyJson = JSONArray(
                            prefs.getString("transaction_history", "[]") ?: "[]"
                        )

                        for (i in 0 until historyJson.length()) {
                            val item = historyJson.getJSONObject(i)

                            if (item.optLong("timestamp", 0L) == transaction.timestamp) {
                                item.put("type", selectedType)
                                item.put("classification", "CONFIRMADA")
                                break
                            }
                        }

                        prefs.edit()
                            .putString("transaction_history", historyJson.toString())
                            .apply()

                        transactionToCorrect = null
                        refreshData()
                    }
                ) {
                    Text("Continuar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        transactionToCorrect = null
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        NotificationListenerService.requestRebind(
            ComponentName(
                context,
                FinanceNotificationListener::class.java
            )
        )

        delay(2500)
        refreshData()
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {

                Text(
                    text = "Contai",
                    style = MaterialTheme.typography.headlineLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("Seu assessor financeiro pessoal")

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (notificationAccess) {
                        "Acesso às notificações: ATIVO"
                    } else {
                        "Acesso às notificações: INATIVO"
                    },
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (serviceConnected) {
                        "Serviço de captura: CONECTADO"
                    } else {
                        "Serviço de captura: DESCONECTADO"
                    },
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Diagnóstico do listener")
                Text("Último app recebido: ${debugLastPackage.ifBlank { "nenhum" }}")
                Text("Último título recebido: ${debugLastTitle.ifBlank { "nenhum" }}")
                Text("Último evento: ${if (debugLastEventAt > 0L) debugLastEventAt.toString() else "nenhum"}")

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                        )
                        context.startActivity(intent)
                    }
                ) {
                    Text("Abrir acesso às notificações")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        NotificationListenerService.requestRebind(
                            ComponentName(
                                context,
                                FinanceNotificationListener::class.java
                            )
                        )

                        scope.launch {
                            delay(2500)
                            refreshData()
                        }
                    }
                ) {
                    Text("Reconectar captura")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        refreshData()
                    }
                ) {
                    Text("Atualizar")
                }

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "Última notificação capturada",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (
                            lastPackage.isBlank() &&
                            lastTitle.isBlank() &&
                            lastText.isBlank()
                        ) {
                            Text("Nenhuma notificação capturada ainda.")
                        } else {
                            Text("App: $lastPackage")
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Título: $lastTitle")
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Texto: $lastText")
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Valor: ${if (lastAmount.isBlank()) "não identificado" else "R$ " + lastAmount.replace(".", ",")}")
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Tipo: ${if (lastType.isBlank()) "Não identificado" else lastType}")
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Confiança: $lastConfidence%")
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Status: ${when (lastClassification) {
                                "CONFIRMADA" -> "Confirmada"
                                "POSSIVEL" -> "Aguardando confirmação"
                                else -> "Não financeira"
                            }}")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Histórico de transações",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (transactionHistory.isEmpty()) {
                    Text("Nenhuma transação no histórico.")
                } else {
                    transactionHistory.forEach { transaction ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            val amountText = transaction.amount?.let {
                                "R$ " + it.toString().replace(".", ",")
                            } ?: "Valor não identificado"

                            val statusText = when (transaction.status) {
                                "CONFIRMADA" -> "Confirmada"
                                "POSSIVEL" -> "Aguardando confirmação"
                                else -> transaction.status.ifBlank { "Não identificado" }
                            }

                            val dateText = if (transaction.timestamp > 0L) {
                                SimpleDateFormat(
                                    "dd/MM/yyyy HH:mm",
                                    Locale.getDefault()
                                ).format(Date(transaction.timestamp))
                            } else {
                                "Data não disponível"
                            }

                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "$amountText\n${transaction.type} • ${transaction.category}\n$statusText\n${transaction.source}\n$dateText"
                                )

                                if (transaction.status == "POSSIVEL") {
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                confirmTransaction(transaction.timestamp)
                                            }
                                        ) {
                                            Text("Confirmar")
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                transactionToCorrect = transaction
                                            }
                                        ) {
                                            Text("Corrigir")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }
}
