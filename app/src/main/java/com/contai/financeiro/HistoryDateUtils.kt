package com.contai.financeiro

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

fun historyDateGroup(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    if (timestamp <= 0L) return "Sem data"
    val item = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    fun Calendar.sameDay(other: Calendar) = get(Calendar.YEAR) == other.get(Calendar.YEAR) && get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
    if (item.sameDay(today)) return "Hoje"
    val yesterday = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -1) }
    if (item.sameDay(yesterday)) return "Ontem"
    return SimpleDateFormat("dd 'de' MMMM", Locale("pt", "BR")).format(Date(timestamp)).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "BR")) else it.toString() }
}

fun isSameHistoryDay(timestamp: Long, selectedDay: Long): Boolean {
    val first = Calendar.getInstance().apply { timeInMillis = timestamp }
    val second = Calendar.getInstance().apply { timeInMillis = selectedDay }
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}

@Composable fun HistoryDateHeader(label: String) { Spacer(Modifier.height(8.dp)); Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }

@Composable
fun GroupedHistoryTransactions(transactions: List<TransactionRecord>, onConfirm: (Long) -> Unit, onCorrect: (TransactionRecord) -> Unit, onIgnore: (Long) -> Unit, onDelete: (TransactionRecord) -> Unit, showDateGroups: Boolean = true) {
    var previousGroup: String? = null
    transactions.sortedByDescending { it.effectiveMovementTimestamp }.forEach { transaction ->
        val group = historyDateGroup(transaction.effectiveMovementTimestamp)
        if (showDateGroups && group != previousGroup) { HistoryDateHeader(group); previousGroup = group }
        TransactionHistoryCard(transaction, onConfirm, onCorrect, onIgnore, onDelete)
    }
}

@Composable
fun TransactionHistoryCard(transaction: TransactionRecord, onConfirm: (Long) -> Unit, onCorrect: (TransactionRecord) -> Unit, onIgnore: (Long) -> Unit, onDelete: (TransactionRecord) -> Unit) {
    var menuExpanded by remember(transaction.timestamp) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        val amountText = transaction.amount?.let { formatCurrency(it) } ?: "Valor não identificado"
        val statusText = when (transaction.status) { "CONFIRMADA" -> "Confirmada"; "POSSIVEL" -> "Aguardando confirmação"; else -> transaction.status.ifBlank { "Não identificado" } }
        val dateText = if (transaction.effectiveMovementTimestamp > 0L) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(transaction.effectiveMovementTimestamp)) else "Horário não disponível"
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(transaction.category, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(amountText, style = MaterialTheme.typography.titleMedium, color = when (transaction.type) { "ENTRADA" -> MaterialTheme.colorScheme.secondary; "DESPESA" -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurface })
                    if (transaction.status == "CONFIRMADA") { IconButton(onClick = { menuExpanded = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }; DropdownMenu(menuExpanded, { menuExpanded = false }) { DropdownMenuItem({ Text("Editar") }, { menuExpanded = false; onCorrect(transaction) }); DropdownMenuItem({ Text("Excluir") }, { menuExpanded = false; onDelete(transaction) }) } }
                }
            }
            Spacer(Modifier.height(6.dp)); Text(friendlyAppName(transaction.source), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp)); Text("${transaction.type} • $statusText", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp)); Text(dateText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (transaction.status == "POSSIVEL") { Spacer(Modifier.height(12.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ onConfirm(transaction.timestamp) }, Modifier.weight(1f)) { Text("Confirmar") }; OutlinedButton({ onCorrect(transaction) }, Modifier.weight(1f)) { Text("Corrigir") } }; Spacer(Modifier.height(8.dp)); TextButton({ onIgnore(transaction.timestamp) }, Modifier.fillMaxWidth()) { Text("Ignorar") } }
        }
    }
}
