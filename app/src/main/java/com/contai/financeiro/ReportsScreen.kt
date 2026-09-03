package com.contai.financeiro

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray

@Composable
fun ReportsScreen(hideValues: Boolean) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("contai_notifications", Context.MODE_PRIVATE)
    val history = JSONArray(prefs.getString("transaction_history", "[]") ?: "[]")

    var income = 0.0
    var expenses = 0.0
    var proceeds = 0.0
    val expensesByCategory = mutableMapOf<String, Double>()

    for (i in 0 until history.length()) {
        val item = history.optJSONObject(i) ?: continue
        if (item.optString("status") != "CONFIRMADA") continue
        val amount = item.optDouble("amount", 0.0)
        val investmentType = item.optString("investmentType", "")
        when {
            investmentType.isNotBlank() -> proceeds += amount
            item.optString("type") == "ENTRADA" -> income += amount
            item.optString("type") == "DESPESA" -> {
                expenses += amount
                val category = item.optString("category", "Outros").ifBlank { "Outros" }
                expensesByCategory[category] = (expensesByCategory[category] ?: 0.0) + amount
            }
        }
    }

    fun valueText(value: Double): String = if (hideValues) "R$ ••••" else formatCurrency(value)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Relatórios", style = MaterialTheme.typography.headlineMedium)
        Text("Visão geral dos seus lançamentos confirmados.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ReportCard("Entradas", valueText(income), Modifier.weight(1f))
            ReportCard("Despesas", valueText(expenses), Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ReportCard("Saldo", valueText(income - expenses), Modifier.weight(1f))
            ReportCard("Proventos", valueText(proceeds), Modifier.weight(1f))
        }

        IncomeExpenseChart(income = income, expenses = expenses, hideValues = hideValues)
        ExpensesByCategoryChart(expensesByCategory = expensesByCategory, hideValues = hideValues)
    }
}

@Composable
private fun IncomeExpenseChart(income: Double, expenses: Double, hideValues: Boolean) {
    val maxValue = maxOf(income, expenses, 1.0)
    val incomeRatio = (income / maxValue).toFloat().coerceIn(0f, 1f)
    val expenseRatio = (expenses / maxValue).toFloat().coerceIn(0f, 1f)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Entradas × Despesas", style = MaterialTheme.typography.titleMedium)
            Text("Comparativo dos lançamentos confirmados.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            ChartBar(
                label = "Entradas",
                ratio = incomeRatio,
                value = if (hideValues) "R$ ••••" else formatCurrency(income),
                usePrimary = true
            )
            ChartBar(
                label = "Despesas",
                ratio = expenseRatio,
                value = if (hideValues) "R$ ••••" else formatCurrency(expenses),
                usePrimary = false
            )
        }
    }
}

@Composable
private fun ExpensesByCategoryChart(expensesByCategory: Map<String, Double>, hideValues: Boolean) {
    val sortedCategories = expensesByCategory.entries.sortedByDescending { it.value }
    val maxValue = maxOf(sortedCategories.maxOfOrNull { it.value } ?: 0.0, 1.0)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Despesas por categoria", style = MaterialTheme.typography.titleMedium)
            Text("Veja onde seu dinheiro está sendo mais utilizado.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (sortedCategories.isEmpty()) {
                Text("Nenhuma despesa confirmada ainda.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                sortedCategories.forEach { (category, value) ->
                    ChartBar(
                        label = category,
                        ratio = (value / maxValue).toFloat().coerceIn(0f, 1f),
                        value = if (hideValues) "R$ ••••" else formatCurrency(value),
                        usePrimary = false
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartBar(label: String, ratio: Float, value: String, usePrimary: Boolean) {
    val barColor = if (usePrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.labelLarge)
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(12.dp).background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(999.dp)
            ),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .height(12.dp)
                    .background(barColor, RoundedCornerShape(999.dp))
            )
        }
    }
}

@Composable
private fun ReportCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
