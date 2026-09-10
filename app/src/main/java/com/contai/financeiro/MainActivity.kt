package com.contai.financeiro

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.contai.financeiro.ui.theme.ContaiTheme
import kotlinx.coroutines.delay
import org.json.JSONArray

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ContaiTheme { ContaiApp() } }
    }
}

fun friendlyAppName(packageName: String): String {
    return when (packageName.lowercase()) {
        "com.nu.production" -> "Nubank"
        "com.santander.app" -> "Santander"
        "br.com.digio.uber" -> "Uber Conta"
        "manual" -> "Lançamento manual"
        else -> packageName
    }
}

fun parseBrazilianAmount(value: String): Double? = value
    .trim()
    .replace("R$", "", ignoreCase = true)
    .replace(" ", "")
    .replace(".", "")
    .replace(",", ".")
    .toDoubleOrNull()

@Composable
fun ContaiApp(hideValuesByDefault: Boolean = false) {
    val context = LocalContext.current
    var notificationAccess by remember { mutableStateOf(false) }
    var serviceConnected by remember { mutableStateOf(false) }
    var listenerLastAliveAt by remember { mutableStateOf(0L) }
    var transactionHistory by remember { mutableStateOf(listOf<TransactionRecord>()) }
    var transactionToCorrect by remember { mutableStateOf<TransactionRecord?>(null) }
    var transactionToDelete by remember { mutableStateOf<TransactionRecord?>(null) }
    var customIncomeCategories by remember { mutableStateOf(listOf<String>()) }
    var customExpenseCategories by remember { mutableStateOf(listOf<String>()) }
    var newCategoryName by remember { mutableStateOf("") }
    var showValues by remember { mutableStateOf(!hideValuesByDefault) }

    LaunchedEffect(hideValuesByDefault) {
        showValues = !hideValuesByDefault
    }

    fun addCustomCategory(name: String, type: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val prefs = context.getSharedPreferences("contai_notifications", Context.MODE_PRIVATE)
        if (type == "ENTRADA") {
            val updated = (customIncomeCategories + cleanName).distinct().sorted()
            prefs.edit().putStringSet("custom_income_categories", updated.toSet()).apply()
            customIncomeCategories = updated
        } else {
            val updated = (customExpenseCategories + cleanName).distinct().sorted()
            prefs.edit().putStringSet("custom_expense_categories", updated.toSet()).apply()
            customExpenseCategories = updated
        }
    }

    fun refreshData() {
        val prefs = context.getSharedPreferences("contai_notifications", Context.MODE_PRIVATE)
        serviceConnected = prefs.getBoolean("service_connected", false)
        listenerLastAliveAt = prefs.getLong("listener_last_alive_at", 0L)
        customIncomeCategories = prefs.getStringSet("custom_income_categories", emptySet())?.toList()?.sorted() ?: emptyList()
        customExpenseCategories = prefs.getStringSet("custom_expense_categories", emptySet())?.toList()?.sorted() ?: emptyList()

        val historyJson = JSONArray(prefs.getString("transaction_history", "[]") ?: "[]")
        val historyItems = mutableListOf<TransactionRecord>()
        for (i in historyJson.length() - 1 downTo 0) {
            val item = historyJson.getJSONObject(i)
            val amount = if (item.has("amount")) item.optDouble("amount") else null
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
                    confidence = item.optInt("confidence", 0),
                    investmentType = item.optString("investmentType", ""),
                    movementTimestamp = item.optLong("movementTimestamp", item.optLong("timestamp", 0L))
                )
            )
        }
        transactionHistory = historyItems

        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        notificationAccess = enabledListeners.contains(context.packageName)
    }

    fun ignoreTransaction(timestamp: Long) {
        val prefs = context.getSharedPreferences("contai_notifications", Context.MODE_PRIVATE)
        val historyJson = JSONArray(prefs.getString("transaction_history", "[]") ?: "[]")
        for (i in 0 until historyJson.length()) {
            val item = historyJson.getJSONObject(i)
            if (item.optLong("timestamp", 0L) == timestamp) {
                item.put("classification", "IGNORADA")
                break
            }
        }
        prefs.edit().putString("transaction_history", historyJson.toString()).apply()
        refreshData()
    }

    fun deleteTransaction(timestamp: Long) {
        val prefs = context.getSharedPreferences("contai_notifications", Context.MODE_PRIVATE)
        val historyJson = JSONArray(prefs.getString("transaction_history", "[]") ?: "[]")
        val updated = JSONArray()
        for (i in 0 until historyJson.length()) {
            val item = historyJson.getJSONObject(i)
            if (item.optLong("timestamp", 0L) != timestamp) updated.put(item)
        }
        prefs.edit().putString("transaction_history", updated.toString()).apply()
        transactionToDelete = null
        refreshData()
    }

    fun confirmTransaction(timestamp: Long) {
        val prefs = context.getSharedPreferences("contai_notifications", Context.MODE_PRIVATE)
        val historyJson = JSONArray(prefs.getString("transaction_history", "[]") ?: "[]")
        for (i in 0 until historyJson.length()) {
            val item = historyJson.getJSONObject(i)
            if (item.optLong("timestamp", 0L) == timestamp) {
                item.put("classification", "CONFIRMADA")
                break
            }
        }
        prefs.edit().putString("transaction_history", historyJson.toString()).apply()
        refreshData()
    }

    LaunchedEffect(Unit) {
        while (true) {
            refreshData()
            delay(5_000)
        }
    }

    LaunchedEffect(Unit) {
        NotificationListenerService.requestRebind(
            ComponentName(context, FinanceNotificationListener::class.java)
        )
        delay(2_500)
        refreshData()
    }

    val listenerHealthy = notificationAccess && serviceConnected && listenerLastAliveAt > 0L &&
        System.currentTimeMillis() - listenerLastAliveAt <= 45_000

    if (transactionToDelete != null) {
        val transaction = transactionToDelete!!
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("Excluir transação?") },
            text = { Text("Essa ação removerá definitivamente este lançamento.") },
            confirmButton = {
                Button(onClick = { deleteTransaction(transaction.timestamp) }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) { Text("Cancelar") }
            }
        )
    }

    if (transactionToCorrect != null) {
        val transaction = transactionToCorrect!!
        var selectedType by remember(transaction.timestamp) { mutableStateOf(transaction.type) }
        var selectedCategory by remember(transaction.timestamp) { mutableStateOf(transaction.category) }

        AlertDialog(
            onDismissRequest = { transactionToCorrect = null },
            title = { Text("Corrigir transação") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())
                ) {
                    Text("Valor: ${formatCurrency(transaction.amount ?: 0.0)}")
                    Spacer(Modifier.height(12.dp))
                    Text("Tipo")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedType == "ENTRADA",
                            onClick = { selectedType = "ENTRADA" },
                            label = { Text("Receita") }
                        )
                        FilterChip(
                            selected = selectedType == "DESPESA",
                            onClick = { selectedType = "DESPESA" },
                            label = { Text("Despesa") }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Categoria")
                    val categories = if (selectedType == "ENTRADA") {
                        listOf("Receitas", "Pix recebido", "Salário", "Extra", "Outros") + customIncomeCategories
                    } else {
                        listOf("Alimentação", "Transporte", "Combustível", "Moradia", "Saúde", "Compras", "Lazer", "Outros") + customExpenseCategories
                    }
                    categories.distinct().forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category) }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("Nova categoria") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        val name = newCategoryName.trim()
                        if (name.isNotBlank()) {
                            addCustomCategory(name, selectedType)
                            selectedCategory = name
                            newCategoryName = ""
                        }
                    }) { Text("Adicionar categoria") }
                    Spacer(Modifier.height(12.dp))
                    Text("Origem: ${friendlyAppName(transaction.source)}")
                }
            },
            confirmButton = {
                Button(onClick = {
                    val typedCategory = newCategoryName.trim()
                    if (typedCategory.isNotBlank()) {
                        addCustomCategory(typedCategory, selectedType)
                        selectedCategory = typedCategory
                        newCategoryName = ""
                    }

                    val prefs = context.getSharedPreferences("contai_notifications", Context.MODE_PRIVATE)
                    val historyJson = JSONArray(prefs.getString("transaction_history", "[]") ?: "[]")
                    for (i in 0 until historyJson.length()) {
                        val item = historyJson.getJSONObject(i)
                        if (item.optLong("timestamp", 0L) == transaction.timestamp) {
                            item.put("type", selectedType)
                            item.put("category", selectedCategory)
                            item.put("classification", "CONFIRMADA")

                            val normalizedLearningText = transaction.text.lowercase()
                                .replace(Regex("""r\$\s*[0-9.]+,[0-9]{2}"""), "r$ valor")
                                .replace(Regex("""\s+"""), " ")
                                .trim()
                            val learningKey = "${transaction.source}|${transaction.title}|$normalizedLearningText"
                                .lowercase()
                                .trim()
                            prefs.edit().putString(
                                "learned_$learningKey",
                                "$selectedType|$selectedCategory"
                            ).apply()
                            break
                        }
                    }
                    prefs.edit().putString("transaction_history", historyJson.toString()).apply()
                    transactionToCorrect = null
                    refreshData()
                }) { Text("Continuar") }
            },
            dismissButton = {
                TextButton(onClick = { transactionToCorrect = null }) { Text("Cancelar") }
            }
        )
    }

    val confirmedForTotals = transactionHistory.filter { it.status == "CONFIRMADA" }
    val totalIncome = confirmedForTotals
        .filter { it.type == "ENTRADA" && it.investmentType.isBlank() }
        .sumOf { it.amount ?: 0.0 }
    val totalExpense = confirmedForTotals
        .filter { it.type == "DESPESA" }
        .sumOf { it.amount ?: 0.0 }
    val currentMonth = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.DAY_OF_MONTH, 1)
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val monthlyProceeds = confirmedForTotals
        .filter { it.investmentType.isNotBlank() && it.effectiveMovementTimestamp >= currentMonth }
        .sumOf { it.amount ?: 0.0 }
    val balance = totalIncome - totalExpense
    val pendingTransactions = transactionHistory.filter { it.status == "POSSIVEL" }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text("Contai", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Seu dinheiro, mais claro.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                when {
                                    !notificationAccess -> "Captura sem permissão"
                                    listenerHealthy -> "Captura ativa"
                                    else -> "Captura interrompida"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                when {
                                    !notificationAccess -> "Ative o acesso às notificações"
                                    listenerHealthy -> "O Contai está acompanhando suas notificações"
                                    else -> "A captura automática precisa de atenção"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            if (listenerHealthy) "●" else "⚠️",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (listenerHealthy) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Saldo atual", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "Visão geral",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { showValues = !showValues }) {
                                Text(if (showValues) "👁" else "🙈")
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (showValues) formatCurrency(balance) else "R$ ••••••",
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Saldo entre receitas e despesas confirmadas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("Receitas", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (showValues) formatCurrency(totalIncome) else "R$ ••••••",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("Despesas", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (showValues) formatCurrency(totalExpense) else "R$ ••••••",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Proventos do mês",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (showValues) formatCurrency(monthlyProceeds) else "R$ ••••••",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            "Investimentos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("Pendências", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))

                if (pendingTransactions.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "✓",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Tudo certo por aqui", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Quando uma transação precisar da sua confirmação, ela aparecerá aqui.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    GroupedHistoryTransactions(
                        transactions = pendingTransactions,
                        onConfirm = { confirmTransaction(it) },
                        onCorrect = { transactionToCorrect = it },
                        onIgnore = { ignoreTransaction(it) },
                        onDelete = { transactionToDelete = it },
                        showDateGroups = false
                    )
                }
            }
        }
    }
}
