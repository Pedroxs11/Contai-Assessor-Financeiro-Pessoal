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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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

        val enabledListeners =
            Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ).orEmpty()

        notificationAccess =
            enabledListeners.contains(context.packageName)
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
            }
        }
    }
}
