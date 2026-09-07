package com.contai.financeiro

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
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
import androidx.compose.foundation.layout.heightIn
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
private const val RECURRENCE_NONE = "NONE"
private const val RECURRENCE_WEEKLY = "WEEKLY"
private const val RECURRENCE_MONTHLY = "MONTHLY"

private data class AgendaItem(
    val id: Long,
    val title: String,
    val note: String = "",
    val dueAt: Long,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val recurrence: String = RECURRENCE_NONE
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
                    note = item.optString("note", item.optString("observation", "")),
                    dueAt = item.optLong("dueAt", 0L),
                    completed = item.optBoolean("completed", false),
                    completedAt = if (item.has("completedAt")) item.optLong("completedAt") else null,
                    recurrence = item.optString("recurrence", RECURRENCE_NONE)
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
                .put("note", item.note)
                .put("dueAt", item.dueAt)
                .put("completed", item.completed)
                .put("recurrence", item.recurrence)
                .apply { item.completedAt?.let { put("completedAt", it) } }
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

private fun nextWeeklyOccurrence(timestamp: Long, after: Long = System.currentTimeMillis()): Long {
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    do {
        calendar.add(Calendar.WEEK_OF_YEAR, 1)
    } while (calendar.timeInMillis <= after)
    return calendar.timeInMillis
}

private fun nextMonthlyOccurrence(timestamp: Long, after: Long = System.currentTimeMillis()): Long {
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    val preferredDay = calendar.get(Calendar.DAY_OF_MONTH)
    do {
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.add(Calendar.MONTH, 1)
        calendar.set(
            Calendar.DAY_OF_MONTH,
            minOf(preferredDay, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        )
    } while (calendar.timeInMillis <= after)
    return calendar.timeInMillis
}

private fun agendaPendingIntent(context: Context, item: AgendaItem): PendingIntent {
    val intent = Intent(context, AgendaReminderReceiver::class.java)
        .putExtra("itemId", item.id)
        .putExtra("title", item.title)
        .putExtra("note", item.note)

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
    var note by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    var timeText by remember { mutableStateOf("") }
    var recurrence by remember { mutableStateOf(RECURRENCE_NONE) }

    fun clearEditor() {
        editingItem = null
        title = ""
        note = ""
        dateText = ""
        timeText = ""
        recurrence = RECURRENCE_NONE
        showEditorDialog = false
    }

    fun openNewReminder() {
        val suggestedAt = System.currentTimeMillis() + 60 * 60 * 1000L
        editingItem = null
        title = ""
        note = ""
        dateText = formatAgendaDate(suggestedAt)
        timeText = formatAgendaTime(suggestedAt)
        recurrence = RECURRENCE_NONE
        showEditorDialog = true
    }

    fun openEditReminder(item: AgendaItem) {
        editingItem = item
        title = item.title
        note = item.note
        dateText = formatAgendaDate(item.dueAt)
        timeText = formatAgendaTime(item.dueAt)
        recurrence = item.recurrence
        showEditorDialog = true
    }

    fun openDatePicker() {
        val baseTimestamp = parseAgendaDateTime(dateText, timeText)
            ?: (System.currentTimeMillis() + 60 * 60 * 1000L)
        val calendar = Calendar.getInstance().apply { timeInMillis = baseTimestamp }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                dateText = String.format(
                    Locale("pt", "BR"),
                    "%02d/%02d/%04d",
                    dayOfMonth,
                    month + 1,
                    year
                )
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun openTimePicker() {
        val baseTimestamp = parseAgendaDateTime(dateText, timeText)
            ?: (System.currentTimeMillis() + 60 * 60 * 1000L)
        val calendar = Calendar.getInstance().apply { timeInMillis = baseTimestamp }
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                timeText = String.format(
                    Locale("pt", "BR"),
                    "%02d:%02d",
                    hourOfDay,
                    minute
                )
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
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
    val completedItems = items.filter { it.completed }.sortedByDescending { it.completedAt ?: it.dueAt }
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
        val currentEditingItem = editingItem

        AlertDialog(
            onDismissRequest = { clearEditor() },
            title = { Text(if (currentEditingItem == null) "Novo lembrete" else "Editar lembrete") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 470.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título") },
                        placeholder = { Text("Ex.: Ligar para a Márcia") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Observação (opcional)") },
                        placeholder = { Text("Ex.: confirmar o compromisso antes do horário") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )

                    Text("Quando", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { openDatePicker() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(dateText.ifBlank { "Data" })
                        }
                        OutlinedButton(
                            onClick = { openTimePicker() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(timeText.ifBlank { "Horário" })
                        }
                    }

                    Text("Repetição", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RecurrenceButton(
                            label = "Único",
                            selected = recurrence == RECURRENCE_NONE,
                            onClick = { recurrence = RECURRENCE_NONE },
                            modifier = Modifier.weight(1f)
                        )
                        RecurrenceButton(
                            label = "Semanal",
                            selected = recurrence == RECURRENCE_WEEKLY,
                            onClick = { recurrence = RECURRENCE_WEEKLY },
                            modifier = Modifier.weight(1f)
                        )
                        RecurrenceButton(
                            label = "Mensal",
                            selected = recurrence == RECURRENCE_MONTHLY,
                            onClick = { recurrence = RECURRENCE_MONTHLY },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        when (recurrence) {
                            RECURRENCE_WEEKLY -> "Ao concluir, o próximo lembrete será criado para a semana seguinte."
                            RECURRENCE_MONTHLY -> "Ao concluir, o próximo lembrete será criado para o mês seguinte."
                            else -> "Este lembrete acontecerá apenas uma vez."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

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
                                note = note.trim(),
                                dueAt = dueAt,
                                recurrence = recurrence
                            )
                        } else {
                            currentEditingItem.copy(
                                title = title.trim(),
                                note = note.trim(),
                                dueAt = dueAt,
                                recurrence = recurrence
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
                        parsedDateTime > System.currentTimeMillis()
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
            "Organize compromissos e lembretes do dia a dia.",
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
                    "Organização",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Crie lembretes únicos, semanais ou mensais com uma observação opcional.",
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
                description = "Crie seu primeiro lembrete para organizar a Agenda do dia a dia."
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
                    } else null
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (isOverdue) "Atrasado • ${formatAgendaDateTime(item.dueAt)}"
                            else formatAgendaDateTime(item.dueAt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isOverdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                        if (item.note.isNotBlank()) {
                            Text(
                                item.note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        when (item.recurrence) {
                            RECURRENCE_WEEKLY -> Text(
                                "Repete toda semana",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            RECURRENCE_MONTHLY -> Text(
                                "Repete todo mês",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { openEditReminder(item) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Editar") }
                            OutlinedButton(
                                onClick = { deletingItem = item },
                                modifier = Modifier.weight(1f)
                            ) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                        }
                        Button(
                            onClick = {
                                cancelAgendaReminder(context, item)
                                val completed = item.copy(
                                    completed = true,
                                    completedAt = System.currentTimeMillis()
                                )
                                val updatedItems = items.map {
                                    if (it.id == item.id) completed else it
                                }.toMutableList()

                                val nextDueAt = when (item.recurrence) {
                                    RECURRENCE_WEEKLY -> nextWeeklyOccurrence(item.dueAt)
                                    RECURRENCE_MONTHLY -> nextMonthlyOccurrence(item.dueAt)
                                    else -> null
                                }

                                nextDueAt?.let { dueAt ->
                                    val nextItem = item.copy(
                                        id = System.currentTimeMillis(),
                                        dueAt = dueAt,
                                        completed = false,
                                        completedAt = null
                                    )
                                    updatedItems.add(nextItem)
                                    scheduleAgendaReminder(context, nextItem)
                                }

                                val updated = updatedItems.sortedBy { it.dueAt }
                                saveAgendaItems(context, updated)
                                items = updated
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                when (item.recurrence) {
                                    RECURRENCE_WEEKLY -> "Concluir e criar próxima semana"
                                    RECURRENCE_MONTHLY -> "Concluir e criar próximo mês"
                                    else -> "Concluir"
                                }
                            )
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
                            "Concluído • ${formatAgendaDateTime(item.completedAt ?: item.dueAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (item.note.isNotBlank()) {
                            Text(
                                item.note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        when (item.recurrence) {
                            RECURRENCE_WEEKLY -> Text(
                                "Recorrência semanal mantida no próximo lembrete",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            RECURRENCE_MONTHLY -> Text(
                                "Recorrência mensal mantida no próximo lembrete",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        OutlinedButton(
                            onClick = { deletingItem = item },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
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
                    "Crie um lembrete para alguns minutos à frente, feche o app e confirme se a notificação aparece perto do horário definido.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecurrenceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
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
        } else null
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = if (emphasized) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = if (emphasized) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
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
