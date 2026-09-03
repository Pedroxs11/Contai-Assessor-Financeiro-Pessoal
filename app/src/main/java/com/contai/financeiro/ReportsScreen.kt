package com.contai.financeiro

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    for (i in 0 until history.length()) {
        val item = history.optJSONObject(i) ?: continue
        if (item.optString("status") != "CONFIRMADA") continue
        val amount = item.optDouble("amount", 0.0)
        val investmentType = item.optString("investmentType", "")
        when {
            investmentType.isNotBlank() -> proceeds += amount
            item.optString("type") == "ENTRADA" -> income += amount
            item.optString("type") == "DESPESA" -> expenses += amount
        }
    }

    fun valueText(value: Double): String = if (hideValues) "R$ ••••" else formatCurrency(value)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Relatórios", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Visão geral dos seus lançamentos confirmados.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReportCard("Entradas", valueText(income), Modifier.weight(1f))
            ReportCard("Despesas", valueText(expenses), Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReportCard("Saldo", valueText(income - expenses), Modifier.weight(1f))
            ReportCard("Proventos", valueText(proceeds), Modifier.weight(1f))
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Próximo passo", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Aqui entraremos com gráficos de entradas x despesas, categorias e evolução do saldo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
