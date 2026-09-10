package com.contai.financeiro

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import java.util.Calendar

private enum class ReportPeriod(val label: String) {
    MONTH("Este mês"), ALL("Tudo")
}

private enum class ReportGroup(val label: String) {
    ALL("Todos"), INCOME("Receitas"), EXPENSE("Despesas"), PROCEEDS("Proventos")
}

private data class ReportItem(
    val record: TransactionRecord,
    val category: String
)

@Composable
fun ReportsScreen(hideValues: Boolean) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("contai_notifications", Context.MODE_PRIVATE)
    val history = JSONArray(prefs.getString("transaction_history", "[]") ?: "[]")
    var period by remember { mutableStateOf(ReportPeriod.MONTH) }
    var selectedGroup by remember { mutableStateOf(ReportGroup.ALL) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val monthStart = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    var income = 0.0
    var expenses = 0.0
    var proceeds = 0.0
    val incomeByCategory = mutableMapOf<String, Double>()
    val expensesByCategory = mutableMapOf<String, Double>()
    val records = mutableListOf<ReportItem>()

    for (i in 0 until history.length()) {
        val item = history.optJSONObject(i) ?: continue
        val classification = item.optString("classification", item.optString("status", ""))
        if (classification != "CONFIRMADA") continue

        val timestamp = item.optLong("movementTimestamp", item.optLong("timestamp", 0L))
        if (period == ReportPeriod.MONTH && timestamp < monthStart) continue

        val amount = item.optDouble("amount", 0.0)
        val type = item.optString("type", "")
        val investmentType = item.optString("investmentType", "")
        val category = item.optString("category", "").ifBlank {
            if (type == "ENTRADA") "Receitas" else "Outros"
        }

        val record = TransactionRecord(
            amount = if (item.has("amount")) item.optDouble("amount") else null,
            type = type,
            timestamp = item.optLong("timestamp", 0L),
            category = category,
            status = classification,
            source = item.optString("package", ""),
            title = item.optString("title", ""),
            text = item.optString("text", ""),
            confidence = item.optInt("confidence", 0),
            investmentType = investmentType,
            movementTimestamp = timestamp
        )
        records += ReportItem(record, category)

        when {
            investmentType.isNotBlank() -> proceeds += amount
            type == "ENTRADA" -> {
                income += amount
                incomeByCategory[category] = (incomeByCategory[category] ?: 0.0) + amount
            }
            type == "DESPESA" -> {
                expenses += amount
                expensesByCategory[category] = (expensesByCategory[category] ?: 0.0) + amount
            }
        }
    }

    fun valueText(value: Double): String = if (hideValues) "R$ ••••" else formatCurrency(value)

    val filteredRecords = records.map { it.record }.filter { record ->
        val groupMatches = when (selectedGroup) {
            ReportGroup.ALL -> true
            ReportGroup.INCOME -> record.type == "ENTRADA" && record.investmentType.isBlank()
            ReportGroup.EXPENSE -> record.type == "DESPESA"
            ReportGroup.PROCEEDS -> record.investmentType.isNotBlank()
        }
        val categoryMatches = selectedCategory == null || record.category == selectedCategory
        groupMatches && categoryMatches
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Relatórios", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Veja os totais e toque em uma categoria para abrir os lançamentos.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportPeriod.entries.forEach { option ->
                FilterChip(
                    selected = period == option,
                    onClick = {
                        period = option
                        selectedCategory = null
                    },
                    label = { Text(option.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ReportCard("Receitas", valueText(income), Modifier.weight(1f), MaterialTheme.colorScheme.secondary) {
                selectedGroup = ReportGroup.INCOME
                selectedCategory = null
            }
            ReportCard("Despesas", valueText(expenses), Modifier.weight(1f), MaterialTheme.colorScheme.error) {
                selectedGroup = ReportGroup.EXPENSE
                selectedCategory = null
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ReportCard("Saldo", valueText(income - expenses), Modifier.weight(1f), MaterialTheme.colorScheme.primary) {
                selectedGroup = ReportGroup.ALL
                selectedCategory = null
            }
            ReportCard("Proventos", valueText(proceeds), Modifier.weight(1f), MaterialTheme.colorScheme.primary) {
                selectedGroup = ReportGroup.PROCEEDS
                selectedCategory = null
            }
        }

        IncomeExpenseChart(income = income, expenses = expenses, hideValues = hideValues)

        CategorySection(
            title = "Receitas por categoria",
            subtitle = "Pix, salário, extra e outras receitas.",
            values = incomeByCategory,
            hideValues = hideValues,
            accentColor = MaterialTheme.colorScheme.secondary,
            onCategoryClick = { category ->
                selectedGroup = ReportGroup.INCOME
                selectedCategory = category
            }
        )

        CategorySection(
            title = "Despesas por categoria",
            subtitle = "Combustível, alimentação, lazer e demais gastos.",
            values = expensesByCategory,
            hideValues = hideValues,
            accentColor = MaterialTheme.colorScheme.error,
            onCategoryClick = { category ->
                selectedGroup = ReportGroup.EXPENSE
                selectedCategory = category
            }
        )

        ProceedsCard(proceeds = proceeds, hideValues = hideValues, periodLabel = period.label)

        Text("Histórico e filtros", style = MaterialTheme.typography.titleLarge)
        Text(
            selectedCategory?.let { "Mostrando: $it" } ?: "Escolha um tipo ou toque em uma categoria acima.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ReportGroup.ALL, ReportGroup.INCOME).forEach { group ->
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = selectedGroup == group && selectedCategory == null,
                        onClick = {
                            selectedGroup = group
                            selectedCategory = null
                        },
                        label = { Text(group.label) }
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ReportGroup.EXPENSE, ReportGroup.PROCEEDS).forEach { group ->
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = selectedGroup == group && selectedCategory == null,
                        onClick = {
                            selectedGroup = group
                            selectedCategory = null
                        },
                        label = { Text(group.label) }
                    )
                }
            }
        }

        if (selectedCategory != null) {
            FilterChip(
                selected = true,
                onClick = { selectedCategory = null },
                label = { Text("${selectedCategory}  ×") }
            )
        }

        if (filteredRecords.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    "Nenhum lançamento encontrado neste filtro.",
                    modifier = Modifier.padding(18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            GroupedHistoryTransactions(
                transactions = filteredRecords,
                onConfirm = {},
                onCorrect = {},
                onIgnore = {},
                onDelete = {},
                showDateGroups = true,
                showActions = false
            )
        }
    }
}

@Composable
private fun IncomeExpenseChart(income: Double, expenses: Double, hideValues: Boolean) {
    val maxValue = maxOf(income, expenses, 1.0)
    val incomeRatio = (income / maxValue).toFloat().coerceIn(0f, 1f)
    val expenseRatio = (expenses / maxValue).toFloat().coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Receitas × Despesas", style = MaterialTheme.typography.titleMedium)
            Text(
                "Comparação visual do período selecionado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth().height(190.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                VerticalChartBar(
                    label = "Receitas",
                    value = if (hideValues) "R$ ••••" else formatCurrency(income),
                    ratio = incomeRatio,
                    barColor = MaterialTheme.colorScheme.secondary
                )
                VerticalChartBar(
                    label = "Despesas",
                    value = if (hideValues) "R$ ••••" else formatCurrency(expenses),
                    ratio = expenseRatio,
                    barColor = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun VerticalChartBar(
    label: String,
    value: String,
    ratio: Float,
    barColor: androidx.compose.ui.graphics.Color
) {
    val barHeight = (112f * ratio).coerceAtLeast(6f).dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(62.dp)
                .height(barHeight)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                .background(barColor)
        )
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CategorySection(
    title: String,
    subtitle: String,
    values: Map<String, Double>,
    hideValues: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    onCategoryClick: (String) -> Unit
) {
    val sortedCategories = values.entries.sortedByDescending { it.value }
    val maxValue = maxOf(sortedCategories.maxOfOrNull { it.value } ?: 0.0, 1.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (sortedCategories.isEmpty()) {
                Text(
                    "Nenhum lançamento confirmado neste período.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                sortedCategories.forEach { (category, value) ->
                    ChartBar(
                        label = category,
                        ratio = (value / maxValue).toFloat().coerceIn(0f, 1f),
                        value = if (hideValues) "R$ ••••" else formatCurrency(value),
                        accentColor = accentColor,
                        onClick = { onCategoryClick(category) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProceedsCard(proceeds: Double, hideValues: Boolean, periodLabel: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Proventos", style = MaterialTheme.typography.titleMedium)
            Text(
                "Dividendos, JCP e rendimentos • $periodLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (hideValues) "R$ ••••" else formatCurrency(proceeds),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ChartBar(
    label: String,
    ratio: Float,
    value: String,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.labelLarge)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(999.dp)),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .height(10.dp)
                    .background(accentColor, RoundedCornerShape(999.dp))
            )
        }
    }
}

@Composable
private fun ReportCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, style = MaterialTheme.typography.titleLarge, color = accentColor)
        }
    }
}
