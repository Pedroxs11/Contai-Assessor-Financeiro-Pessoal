package com.contai.financeiro

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HistoryDayFilter(
    selectedDay: Long?,
    onDaySelected: (Long) -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    val base = selectedDay ?: System.currentTimeMillis()
    val calendar = Calendar.getInstance().apply { timeInMillis = base }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        val selected = Calendar.getInstance().apply {
                            timeInMillis = base
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            set(Calendar.HOUR_OF_DAY, 12)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        onDaySelected(selected.timeInMillis)
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        ) {
            val label = selectedDay?.let {
                SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(it))
            } ?: "Escolher data"
            Text("📅 $label")
        }

        if (selectedDay != null) {
            TextButton(onClick = onClear) {
                Text("Limpar")
            }
        }
    }
}

fun filterTransactionsByDay(
    transactions: List<TransactionRecord>,
    selectedDay: Long?
): List<TransactionRecord> {
    if (selectedDay == null) return transactions
    return transactions.filter { transaction ->
        isSameHistoryDay(transaction.effectiveMovementTimestamp, selectedDay)
    }
}
