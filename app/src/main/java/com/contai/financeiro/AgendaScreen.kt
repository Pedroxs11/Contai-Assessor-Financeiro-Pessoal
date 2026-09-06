package com.contai.financeiro

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedButton
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

private fun parseAgendaDateTime(dateValue: String, timeValue: String): Long? = runCatching {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).apply {
        isLenient = false
    }.parse("${dateValue.trim()} ${timeValue.trim()}")?.time
}.getOrNull()

private fun formatAgendaDateTime(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale("pt", "BR")).format(Date(timestamp))

private fun formatAgendaDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(timestamp))

private fun formatAgendaTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(timestamp))

private fun agendaPendingIntent(context: Context, item: AgendaItem): PendingIntent {
    val intent = Intent(context, AgendaReminderReceiver::class.java)
        .putExtra("itemId", item.id)
        .putExtra("title", item.title)
        .apply { item.amount?.let { putExtra("amount", it) } }

    return PendingIntent.getBroadcast(
        context,
        item.id.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun scheduleAgendaReminder(context: Context, item: AgendaItem) {
    if (item.completed || item.dueAt <= System.currentTimeMillis()) return
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    alarmManager.setAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        item.dueAt,
        agendaPendingIntent(context, item)
    )
}

private fun cancelAgendaReminder(context: Context, item: AgendaItem) {
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    alarmManager.cancel(agendaPendingIntent(context, item))
}

@Composable
fun AgendaScreen() {
    val context = LocalContext.current
    var items by remember { mutableStateOf(loadAgendaItems(context)) }
    var showEditorDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<AgendaItem?>(null) }
    var deletingItem by remember { mutableStateOf<AgendaItem?>(null) }
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    var timeText by remember { mutableStateOf("09:00") }

    fun clearEditor() {
        editingItem = null
        title = ""
        amountText = ""
        dateText = ""
        timeText = "09:00"
        showEditorDialog = false
    }

    fun openNewReminder() {
        editingItem = null
        title = ""
        amountText = ""
        dateText = ""
        timeText = "09:00"
        showEditorDialog = true
    }

    fun openEditReminder(item: AgendaItem) {
        editingItem = item
        title = item.title
        amountText = item.amount?.toString()?.replace('.', ',') ?: ""
        dateText = formatAgendaDate(item.dueAt)
        timeText = formatAgendaTime(item.dueAt)
        showEditorDialog = true
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )

    val now = System.currentTimeMillis()
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val tomorrowStart = todayStart + 24 * 60 * 60 * 1000L
    val activeItems = items.filterNot { it.completed }
    val completedItems = items.filter { it.completed }.sortedByDescending { it.dueAt }
    val overdueItems = activeItems.filter { it.dueAt < now }
    val todayItems = activeItems.filter { it.dueAt in todayStart until tomorrowStart }
    val upcomingItems = activeItems.filter { it.dueAt >= tomorrowStart }

    deletingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text("Excluir lembrete?") },
            text = { Text("O lembrete “${item.title}” será removido da Agenda.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        cancelAgendaReminder(context, item)
                        val updated = items.filterNot { it.id == item.id }
                        saveAgendaItems(context, updated)
                        items = updated
                        deletingItem = null
                    }
                ) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingItem = null }) { Text("Cancelar") }
            }
        )
    }

    if (showEditorDialog) {
        val parsedDateTime = parseAgendaDateTime(dateText, timeText)
        val parsedAmount = parseBrazilianAmount(amountText).takeIf { amountText.isNotBlank() }
        val invalidAmount = amountText.isNotBlank() && parsedAmount == null
        val currentEditingItem = editingItem

        AlertDialog(
            onDismissRequest = { clearEditor() },
            title = { Text(if (currentEditingItem == null) "Novo lembrete" else "Editar lembrete") },
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
                    if (invalidAmount) {
                        Text(
                            "Digite um valor válido, por exemplo 250,00.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = { Text("Data") },
                        placeholder = { Text("dd/mm/aaaa") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { timeText = it },
                        label = { Text("Horário") },
                        placeholder = { Text("hh:mm") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if ((dateText.isNotBlank() || timeText.isNotBlank()) && parsedDateTime == null) {
                        Text(
                            "Use data e horário válidos, por exemplo 10/09/2026 às 09:00.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (parsedDateTime != null && parsedDateTime <= System.currentTimeMillis()) {
                        Text(
                            "O lembrete precisa estar no futuro.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val dueAt = parsedDateTime ?: return@Button
                        if (dueAt <= System.currentTimeMillis()) return@Button

                        val savedItem = if (currentEditingItem == null) {
                            AgendaItem(
                                id = System.currentTimeMillis(),
                                title = title.trim(),
                                amount = parsedAmount,
                                dueAt = dueAt
                            )
                        } else {
                            currentEditingItem.copy(
                                title = title.trim(),
                                amount = parsedAmount,
                                dueAt = dueAt
                            )
                        }

                        currentEditingItem?.let { cancelAgendaReminder(context, it) }
                        val updated = if (currentEditingItem == null) {
                            (items + savedItem).sortedBy { it.dueAt }
                        } else {
                            items.map { if (it.id == currentEditingItem.id) savedItem else it }
                                .sortedBy { it.dueAt }
                        }
                        saveAgendaItems(context, updated)
                        scheduleAgendaReminder(context, savedItem)
                        items = updated
                        clearEditor()
                    },
                    enabled = title.isNotBlank() &&
                        parsedDateTime != null &&
                        parsedDateTime > System.currentTimeMillis() &&
                        !invalidAmount
                ) {
                    Text(if (currentEditingItem == null) "Salvar" else "Salvar alterações")
                }
            },
            dismissButton = {
                TextButton(onClick = { clearEditor() }) { Text("Cancelar") }
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
                    "Crie lembretes com data e horário para contas, vencimentos e compromissos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        openNewReminder()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
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
        AgendaInfoCard(
            title = "Atrasados",
            value = "${overdueItems.size} pendente(s)",
            modifier = Modifier.fillMaxWidth(),
            emphasized = overdueItems.isNotEmpty()
        )

        Spacer(Modifier.height(4.dp))
        Text("Seus lembretes", style = MaterialTheme.typography.titleMedium)

        if (activeItems.isEmpty()) {
            AgendaEmptyCard(
                title = "Nada agendado",
                description = "Crie seu primeiro lembrete financeiro para testar a Agenda no dia a dia."
            )
        } else {
            activeItems.forEach { item ->
                val isOverdue = item.dueAt < now
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = if (isOverdue) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.65f))
                    } else {
                        null
                    }
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (isOverdue) {
                                "Atrasado • ${formatAgendaDateTime(item.dueAt)}"
                            } else {
                                formatAgendaDateTime(item.dueAt)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isOverdue) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                        item.amount?.let {
                            Text(
                                formatCurrency(it),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { openEditReminder(item) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Editar")
                            }
                            OutlinedButton(
                                onClick = { deletingItem = item },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Excluir", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Button(
                            onClick = {
                                cancelAgendaReminder(context, item)
                                val updated = items.map {
                                    if (it.id == item.id) it.copy(completed = true) else it
                                }
                                saveAgendaItems(context, updated)
                                items = updated
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Marcar como concluído")
                        }
                    }
                }
            }
        }

        if (completedItems.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("Concluídos", style = MaterialTheme.typography.titleMedium)
            completedItems.take(5).forEach { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Concluído • ${formatAgendaDateTime(item.dueAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        item.amount?.let {
                            Text(formatCurrency(it), style = MaterialTheme.typography.titleSmall)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val reopened = item.copy(completed = false)
                                    val updated = items.map { if (it.id == item.id) reopened else it }
                                    saveAgendaItems(context, updated)
                                    scheduleAgendaReminder(context, reopened)
                                    items = updated
                                },
                                enabled = item.dueAt > System.currentTimeMillis(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reabrir")
                            }
                            OutlinedButton(
                                onClick = { deletingItem = item },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Excluir", color = MaterialTheme.colorScheme.error)
                            }
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
                Text("Teste operacional", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Crie, edite ou exclua um lembrete e teste a notificação com o app fechado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AgendaInfoCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = if (emphasized) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.65f))
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = if (emphasized) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = if (emphasized) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AgendaEmptyCard(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
