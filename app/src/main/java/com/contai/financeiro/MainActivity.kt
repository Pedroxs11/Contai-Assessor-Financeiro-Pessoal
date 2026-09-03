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
import androidx.compose.ui.Alignment
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

fun friendlyAppName(packageName: String): String {
    return when (packageName.lowercase()) {
        "com.nu.production" -> "Nubank"
        "com.santander.app" -> "Santander"
        "br.com.digio.uber" -> "Uber Conta"
        else -> packageName
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
    var listenerLastAliveAt by remember { mutableStateOf(0L) }
    var debugLastEventAt by remember { mutableStateOf(0L) }
    var debugLastPackage by remember { mutableStateOf("") }
    var debugLastTitle by remember { mutableStateOf("") }
    var transactionHistory by remember { mutableStateOf(listOf<TransactionRecord>()) }
    var transactionToCorrect by remember { mutableStateOf<TransactionRecord?>(null) }
    var customIncomeCategories by remember { mutableStateOf(listOf<String>()) }
    var customExpenseCategories by remember { mutableStateOf(listOf<String>()) }
    var newCategoryName by remember { mutableStateOf("") }
    var selectedSection by remember { mutableStateOf("PENDENCIAS") }

    var showManualEntryDialog by remember { mutableStateOf(false) }
    var showValues by remember { mutableStateOf(true) }
    var manualType by remember { mutableStateOf("DESPESA") }
    var manualAmount by remember { mutableStateOf("") }
    var manualCategory by remember { mutableStateOf("Outros") }
    var manualDescription by remember { mutableStateOf("") }

    fun addCustomCategory(name: String, type: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return

        val prefs = context.getSharedPreferences(
            "contai_notifications",
            Context.MODE_PRIVATE
        )

        if (type == "ENTRADA") {
            val updated = (customIncomeCategories + cleanName)
                .distinct()
                .sorted()

            prefs.edit()
                .putStringSet("custom_income_categories", updated.toSet())
                .apply()

            customIncomeCategories = updated
        } else {
            val updated = (customExpenseCategories + cleanName)
                .distinct()
                .sorted()

            prefs.edit()
                .putStringSet("custom_expense_categories", updated.toSet())
                .apply()

            customExpenseCategories = updated
        }
    }

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
        listenerLastAliveAt = prefs.getLong("listener_last_alive_at", 0L)
        debugLastEventAt = prefs.getLong("debug_last_event_at", 0L)
        debugLastPackage = prefs.getString("debug_last_package", "") ?: ""
        debugLastTitle = prefs.getString("debug_last_title", "") ?: ""

        customIncomeCategories = prefs
            .getStringSet("custom_income_categories", emptySet())
            ?.toList()
            ?.sorted()
            ?: emptyList()

        customExpenseCategories = prefs
            .getStringSet("custom_expense_categories", emptySet())
            ?.toList()
            ?.sorted()
            ?: emptyList()

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

    LaunchedEffect(Unit) {
        while (true) {
            refreshData()
            delay(5_000)
        }
    }

    val listenerHealthy =
        notificationAccess &&
        serviceConnected &&
        listenerLastAliveAt > 0L &&
        System.currentTimeMillis() - listenerLastAliveAt <= 45_000

    fun ignoreTransaction(timestamp: Long) {
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
                item.put("classification", "IGNORADA")
                break
            }
        }

        prefs.edit()
            .putString("transaction_history", historyJson.toString())
            .apply()

        refreshData()
    }

    fun saveManualTransaction() {
        val normalizedAmount = manualAmount
            .trim()
            .replace("R$", "", ignoreCase = true)
            .replace(" ", "")
            .replace(".", "")
            .replace(",", ".")

        val amount = normalizedAmount
            .toDoubleOrNull()
            ?: return

        val prefs = context.getSharedPreferences(
            "contai_notifications",
            Context.MODE_PRIVATE
        )

        val historyJson = JSONArray(
            prefs.getString("transaction_history", "[]") ?: "[]"
        )

        val item = org.json.JSONObject()
            .put("timestamp", System.currentTimeMillis())
            .put("package", "MANUAL")
            .put("title", manualDescription.ifBlank { "Lançamento manual" })
            .put("text", manualDescription)
            .put("type", manualType)
            .put("category", manualCategory)
            .put("confidence", 100)
            .put("classification", "CONFIRMADA")
            .put("amount", amount)

        historyJson.put(item)

        prefs.edit()
            .putString("transaction_history", historyJson.toString())
            .apply()

        manualAmount = ""
        manualDescription = ""
        manualType = "DESPESA"
        manualCategory = "Outros"
        showManualEntryDialog = false

        refreshData()
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
        var selectedCategory by remember(transaction.timestamp) { mutableStateOf(transaction.category) }

        AlertDialog(
            onDismissRequest = {
                transactionToCorrect = null
            },
            title = {
                Text("Corrigir transação")
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 450.dp)
                        .verticalScroll(rememberScrollState())
                ) {
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
                    Text("Categoria")

                    val categories = if (selectedType == "ENTRADA") {
                        listOf("Salário", "Pix recebido", "Outros") + customIncomeCategories
                    } else {
                        listOf(
                            "Alimentação",
                            "Transporte",
                            "Combustível",
                            "Moradia",
                            "Saúde",
                            "Compras",
                            "Lazer",
                            "Outros"
                        ) + customExpenseCategories
                    }

                    categories.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = {
                                selectedCategory = category
                            },
                            label = {
                                Text(category)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("Nova categoria") },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val name = newCategoryName.trim()
                            if (name.isNotBlank()) {
                                addCustomCategory(name, selectedType)
                                selectedCategory = name
                                newCategoryName = ""
                            }
                        }
                    ) {
                        Text("Adicionar categoria")
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Origem: ${friendlyAppName(transaction.source)}")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val typedCategory = newCategoryName.trim()

                        if (typedCategory.isNotBlank()) {
                            addCustomCategory(typedCategory, selectedType)
                            selectedCategory = typedCategory
                            newCategoryName = ""
                        }

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
                                item.put("category", selectedCategory)
                                item.put("classification", "CONFIRMADA")

                                val normalizedLearningText =
                                    transaction.text
                                        .lowercase()
                                        .replace(
                                            Regex("""r\$\s*[0-9.]+,[0-9]{2}"""),
                                            "r$ valor"
                                        )
                                        .replace(Regex("""\s+"""), " ")
                                        .trim()

                                val learningKey =
                                    "${transaction.source}|${transaction.title}|$normalizedLearningText"
                                        .lowercase()
                                        .trim()

                                prefs.edit()
                                    .putString(
                                        "learned_$learningKey",
                                        "$selectedType|$selectedCategory"
                                    )
                                    .apply()

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

    if (showManualEntryDialog) {
        AlertDialog(
            onDismissRequest = {
                showManualEntryDialog = false
            },
            title = {
                Text("Adicionar manualmente")
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 450.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Tipo")

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = manualType == "ENTRADA",
                            onClick = {
                                manualType = "ENTRADA"
                                manualCategory = "Receitas"
                            },
                            label = { Text("Entrada") }
                        )

                        FilterChip(
                            selected = manualType == "DESPESA",
                            onClick = {
                                manualType = "DESPESA"
                                manualCategory = "Outros"
                            },
                            label = { Text("Despesa") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = manualAmount,
                        onValueChange = { manualAmount = it },
                        label = { Text("Valor") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Categoria")

                    val manualCategories =
                        if (manualType == "ENTRADA") {
                            listOf(
                                "Receitas",
                                "Salário",
                                "Pix recebido",
                                "Outros"
                            ) + customIncomeCategories
                        } else {
                            listOf(
                                "Alimentação",
                                "Transporte",
                                "Combustível",
                                "Moradia",
                                "Saúde",
                                "Compras",
                                "Lazer",
                                "Outros"
                            ) + customExpenseCategories
                        }

                    manualCategories
                        .distinct()
                        .forEach { category ->
                            FilterChip(
                                selected = manualCategory == category,
                                onClick = {
                                    manualCategory = category
                                },
                                label = {
                                    Text(category)
                                }
                            )
                        }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = manualDescription,
                        onValueChange = { manualDescription = it },
                        label = { Text("Descrição") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        saveManualTransaction()
                    },
                    enabled = manualAmount
                        .replace(",", ".")
                        .toDoubleOrNull() != null
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showManualEntryDialog = false
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
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

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Seu dinheiro, mais claro.",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        showManualEntryDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Adicionar lançamento")
                }

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = when {
                                    !notificationAccess -> "Captura sem permissão"
                                    listenerHealthy -> "Captura ativa"
                                    else -> "Captura interrompida"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = when {
                                    !notificationAccess ->
                                        "Ative o acesso às notificações"
                                    listenerHealthy ->
                                        "O Contai está acompanhando suas notificações"
                                    else ->
                                        "A captura automática precisa de atenção"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Text(
                            text = when {
                                !notificationAccess -> "⚠️"
                                listenerHealthy -> "●"
                                else -> "⚠️"
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                val confirmedForTotals = transactionHistory.filter {
                    it.status == "CONFIRMADA"
                }

                val totalIncome = confirmedForTotals
                    .filter { it.type == "ENTRADA" }
                    .sumOf { it.amount ?: 0.0 }

                val totalExpense = confirmedForTotals
                    .filter { it.type == "DESPESA" }
                    .sumOf { it.amount ?: 0.0 }

                val balance = totalIncome - totalExpense

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Saldo atual",
                                style = MaterialTheme.typography.titleMedium
                            )

                            TextButton(
                                onClick = { showValues = !showValues }
                            ) {
                                Text(if (showValues) "👁" else "🙈")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (showValues) {
                                "R$ " + String.format(Locale.getDefault(), "%.2f", balance)
                            } else {
                                "R$ ••••••"
                            },
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text("Entradas")
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (showValues) {
                                    "R$ " + String.format(Locale.getDefault(), "%.2f", totalIncome)
                                } else {
                                    "R$ ••••••"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text("Despesas")
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (showValues) {
                                    "R$ " + String.format(Locale.getDefault(), "%.2f", totalExpense)
                                } else {
                                    "R$ ••••••"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedSection == "PENDENCIAS",
                        onClick = { selectedSection = "PENDENCIAS" },
                        label = { Text("Pendências") }
                    )

                    FilterChip(
                        selected = selectedSection == "HISTORICO",
                        onClick = { selectedSection = "HISTORICO" },
                        label = { Text("Histórico") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (selectedSection == "PENDENCIAS") "Pendências" else "Histórico",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(12.dp))

                val displayedTransactions = if (selectedSection == "PENDENCIAS") {
                    transactionHistory.filter { it.status == "POSSIVEL" }
                } else {
                    transactionHistory.filter { it.status == "CONFIRMADA" }
                }

                if (displayedTransactions.isEmpty()) {
                    Text(
                        if (selectedSection == "PENDENCIAS")
                            "Nenhuma transação pendente."
                        else
                            "Nenhuma transação no histórico."
                    )
                } else {
                    displayedTransactions.forEach { transaction ->
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = transaction.category,
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    Text(
                                        text = amountText,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = friendlyAppName(transaction.source),
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "${transaction.type} • $statusText",
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = dateText,
                                    style = MaterialTheme.typography.bodySmall
                                )

                                if (transaction.status == "POSSIVEL") {
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                confirmTransaction(transaction.timestamp)
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Confirmar")
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                transactionToCorrect = transaction
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Corrigir")
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedButton(
                                        onClick = {
                                            ignoreTransaction(transaction.timestamp)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Ignorar")
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
