package com.contai.financeiro

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val AGENDA_PREFS = "contai_agenda"
private const val AGENDA_ITEMS_KEY = "agenda_items"

private data class AgendaItem(
    val id: Long,
    val title: String,
    val amount: Double?,
    val dueAt: Long,
    val completed: Boolean = false
)

private fun loadAgendaItems(context: Context): List<AgendaItem> {
    val prefs = context.getSharedPreferences(AGENDA_PREFS, Context.MODE_PRIVATE)
    val json = JSONArray(prefs.getString(AGENDA_ITEMS_KEY, "[]") ?: "[]")
    return buildList {
        for (i in 0 until json.length()) {
            val item = json.optJSONObject(i) ?: continue
            add(
                AgendaItem(
                    id = item.optLong("id", 0L),
                    title = item.optString("title", "Lembrete"),
                    amount = if (item.has("amount")) item.optDouble("amount") else null,
                    dueAt = item.optLong("dueAt", 0L),
                    completed = item.optBoolean("completed", false)
                )
            )
        }
    }.sortedBy { it.dueAt }
}

private fun saveAgendaItems(context: Context, items: List<AgendaItem>) {
    val json = JSONArray()
    items.forEach { item ->
        json.put(
            JSONObject()
                .put("id", item.id)
                .put("title", item.title)
                .put("dueAt", item.dueAt)
                .put("completed", item.completed)
                .apply { item.amount?.let { put("amount", it) } }
        )
    }
    context.getSharedPreferences(AGENDA_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(AGENDA_ITEMS_KEY, json.toString())
        .apply()
}

private fun parseAgendaDate(value: String): Long? = runCatching {
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).apply {
        isLenient = false
    }.parse(value.trim())?.time
}.getOrNull()

private fun formatAgendaDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(timestamp))

@Composable
fun AgendaScreen() {
    val context = LocalContext.current
    var items by remember { mutableStateOf(loadAgendaItems(context)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }

    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val tomorrowStart = todayStart + 24 * 60 * 60 * 1000L
    val activeItems = items.filterNot { it.completed }
    val todayItems = activeItems.filter { it.dueAt in todayStart until tomorrowStart }
    val upcomingItems = activeItems.filter { it.dueAt >= tomorrowStart }

    if (showAddDialog) {
        val parsedDate = parseAgendaDate(dateText)
        val parsedAmount = amountText
            .trim()
            .replace("R$", "", ignoreCase = true)
            .replace(" ", "")
            .replace(".", "")
            .replace(",", ".")
            .takeIf { it.isNotBlank() }
            ?.toDoubleOrNull()

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Novo lembrete") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Valor opcional") },
                        placeholder = { Text("Ex.: 250,00") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = { Text("Data") },
                        placeholder = { Text("dd/mm/aaaa") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (dateText.isNotBlank() && parsedDate == null) {
                        Text(
                            "Use uma data válida no formato dd/mm/aaaa.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val dueAt = parsedDate ?: return@Button
                        val newItem = AgendaItem(
                            id = System.currentTimeMillis(),
                            title = title.trim(),
                            amount = parsedAmount,
                            dueAt = dueAt
                        )
                        val updated = (items + newItem).sortedBy { it.dueAt }
                        saveAgendaItems(context, updated)
                        items = updated
                        title = ""
                        amountText = ""
                        dateText = ""
                        showAddDialog = false
                    },
                    enabled = title.isNotBlank() && parsedDate != null
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancelar") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Agenda", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Organize compromissos e lembretes financeiros.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Planejamento financeiro",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Crie lembretes locais para contas, vencimentos e compromissos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("+ Novo lembrete")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AgendaInfoCard("Hoje", "${todayItems.size} lembrete(s)", Modifier.weight(1f))
            AgendaInfoCard("Próximos", "${upcomingItems.size} agendado(s)", Modifier.weight(1f))
        }

        Spacer(Modifier.height(4.dp))

        Text("Seus lembretes", style = MaterialTheme.typography.titleMedium)

        if (activeItems.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Nada agendado", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Crie seu primeiro lembrete financeiro para testar a Agenda no dia a dia.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            activeItems.forEach { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            formatAgendaDate(item.dueAt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        item.amount?.let {
                            Text(
                                formatCurrency(it),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Próximo passo", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Adicionar horário, marcar como concluído e disparar a notificação local do lembrete.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AgendaInfoCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
