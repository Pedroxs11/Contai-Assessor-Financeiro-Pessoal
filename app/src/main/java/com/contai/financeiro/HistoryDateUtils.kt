package com.contai.financeiro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

@Composable
fun HistoryDateHeader(label: String) {
    Spacer(Modifier.height(8.dp))
    Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
fun GroupedHistoryTransactions(
    transactions: List<TransactionRecord>,
    onConfirm: (Long) -> Unit,
    onCorrect: (TransactionRecord) -> Unit,
    onIgnore: (Long) -> Unit,
    onDelete: (TransactionRecord) -> Unit,
    showDateGroups: Boolean = true
) {
    var previousGroup: String? = null
    transactions.sortedByDescending { it.timestamp }.forEach { transaction ->
        val group = historyDateGroup(transaction.timestamp)
        if (showDateGroups && group != previousGroup) {
            HistoryDateHeader(group)
            previousGroup = group
        }
        TransactionHistoryCard(transaction, onConfirm, onCorrect, onIgnore, onDelete)
    }
}

@Composable
fun TransactionHistoryCard(
    transaction: TransactionRecord,
    onConfirm: (Long) -> Unit,
    onCorrect: (TransactionRecord) -> Unit,
    onIgnore: (Long) -> Unit,
    onDelete: (TransactionRecord) -> Unit
) {
    var menuExpanded by remember(transaction.timestamp) { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        val amountText = transaction.amount?.let { formatCurrency(it) } ?: "Valor não identificado"
        val statusText = when (transaction.status) {
            "CONFIRMADA" -> "Confirmada"
            "POSSIVEL" -> "Aguardando confirmação"
            else -> transaction.status.ifBlank { "Não identificado" }
        }
        val dateText = if (transaction.timestamp > 0L) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(transaction.timestamp)) else "Horário não disponível"
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(transaction.category, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(amountText, style = MaterialTheme.typography.titleMedium, color = when (transaction.type) { "ENTRADA" -> MaterialTheme.colorScheme.secondary; "DESPESA" -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurface })
                    if (transaction.status == "CONFIRMADA") {
                        IconButton(onClick = { menuExpanded = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("Editar") }, onClick = { menuExpanded = false; onCorrect(transaction) })
                            DropdownMenuItem(text = { Text("Excluir") }, onClick = { menuExpanded = false; onDelete(transaction) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp)); Text(friendlyAppName(transaction.source), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp)); Text("${transaction.type} • $statusText", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp)); Text(dateText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (transaction.status == "POSSIVEL") {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ onConfirm(transaction.timestamp) }, Modifier.weight(1f)) { Text("Confirmar") }
                    OutlinedButton({ onCorrect(transaction) }, Modifier.weight(1f)) { Text("Corrigir") }
                }
                Spacer(Modifier.height(8.dp)); TextButton({ onIgnore(transaction.timestamp) }, Modifier.fillMaxWidth()) { Text("Ignorar") }
            }
        }
    }
}
