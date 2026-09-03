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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun historyDateGroup(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    if (timestamp <= 0L) return "Sem data"

    val itemCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance().apply { timeInMillis = now }

    fun Calendar.sameDay(other: Calendar): Boolean =
        get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
            get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)

    if (itemCalendar.sameDay(today)) return "Hoje"

    val yesterday = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, -1)
    }
    if (itemCalendar.sameDay(yesterday)) return "Ontem"

    return SimpleDateFormat("dd 'de' MMMM", Locale("pt", "BR"))
        .format(Date(timestamp))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "BR")) else it.toString() }
}

@Composable
fun HistoryDateHeader(label: String) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun GroupedHistoryTransactions(
    transactions: List<TransactionRecord>,
    onConfirm: (Long) -> Unit,
    onCorrect: (TransactionRecord) -> Unit,
    onIgnore: (Long) -> Unit
) {
    var previousGroup: String? = null

    transactions.forEach { transaction ->
        val group = historyDateGroup(transaction.timestamp)
        if (group != previousGroup) {
            HistoryDateHeader(group)
            previousGroup = group
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            val amountText = transaction.amount?.let { formatCurrency(it) } ?: "Valor não identificado"
            val statusText = when (transaction.status) {
                "CONFIRMADA" -> "Confirmada"
                "POSSIVEL" -> "Aguardando confirmação"
                else -> transaction.status.ifBlank { "Não identificado" }
            }
            val dateText = if (transaction.timestamp > 0L) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(transaction.timestamp))
            } else {
                "Horário não disponível"
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(transaction.category, style = MaterialTheme.typography.titleMedium)
                    Text(
                        amountText,
                        style = MaterialTheme.typography.titleMedium,
                        color = when (transaction.type) {
                            "ENTRADA" -> MaterialTheme.colorScheme.secondary
                            "DESPESA" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(friendlyAppName(transaction.source), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${transaction.type} • $statusText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(dateText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (transaction.status == "POSSIVEL") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onConfirm(transaction.timestamp) }, modifier = Modifier.weight(1f)) { Text("Confirmar") }
                        OutlinedButton(onClick = { onCorrect(transaction) }, modifier = Modifier.weight(1f)) { Text("Corrigir") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { onIgnore(transaction.timestamp) }, modifier = Modifier.fillMaxWidth()) { Text("Ignorar") }
                }
            }
        }
    }
}
