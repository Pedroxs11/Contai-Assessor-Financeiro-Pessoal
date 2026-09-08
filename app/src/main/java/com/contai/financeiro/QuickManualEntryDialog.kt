package com.contai.financeiro

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun QuickManualEntryDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("contai_notifications", Context.MODE_PRIVATE) }
    var type by remember { mutableStateOf("DESPESA") }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Outros") }
    var description by remember { mutableStateOf("") }
    var newCategoryName by remember { mutableStateOf("") }
    var movementTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var customIncomeCategories by remember { mutableStateOf(prefs.getStringSet("custom_income_categories", emptySet())?.toList()?.sorted().orEmpty()) }
    var customExpenseCategories by remember { mutableStateOf(prefs.getStringSet("custom_expense_categories", emptySet())?.toList()?.sorted().orEmpty()) }

    val amount = amountText.trim().replace("R$", "", true).replace(" ", "").replace(".", "").replace(",", ".").toDoubleOrNull()
    val categories = if (type == "ENTRADA") (listOf("Receitas", "Salário", "Pix recebido", "Outros") + customIncomeCategories).distinct() else (listOf("Alimentação", "Transporte", "Combustível", "Moradia", "Saúde", "Compras", "Lazer", "Outros") + customExpenseCategories).distinct()

    fun addCustomCategory() {
        val cleanName = newCategoryName.trim(); if (cleanName.isBlank()) return
        if (type == "ENTRADA") { val updated = (customIncomeCategories + cleanName).distinct().sorted(); prefs.edit().putStringSet("custom_income_categories", updated.toSet()).apply(); customIncomeCategories = updated }
        else { val updated = (customExpenseCategories + cleanName).distinct().sorted(); prefs.edit().putStringSet("custom_expense_categories", updated.toSet()).apply(); customExpenseCategories = updated }
        category = cleanName; newCategoryName = ""
    }

    fun openDatePicker() {
        val selected = Calendar.getInstance().apply { timeInMillis = movementTimestamp }
        DatePickerDialog(context, { _, year, month, day ->
            val chosen = Calendar.getInstance().apply { timeInMillis = movementTimestamp }
            chosen.set(year, month, day)
            movementTimestamp = chosen.timeInMillis
        }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH)).show()
    }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Adicionar lançamento") }, text = {
        Column(Modifier.height(460.dp).verticalScroll(rememberScrollState())) {
            Text("Tipo")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(type == "ENTRADA", { type = "ENTRADA"; category = "Receitas" }, label = { Text("Entrada") })
                FilterChip(type == "DESPESA", { type = "DESPESA"; category = "Outros" }, label = { Text("Despesa") })
            }
            Spacer(Modifier.height(12.dp)); OutlinedTextField(amountText, { amountText = it }, label = { Text("Valor") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(12.dp)); OutlinedTextField(description, { description = it }, label = { Text("Estabelecimento/descrição (opcional)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp)); Text("Categoria")
            categories.forEach { item -> FilterChip(category == item, { category = item }, label = { Text(item) }) }
            Spacer(Modifier.height(12.dp)); OutlinedTextField(newCategoryName, { newCategoryName = it }, label = { Text("Nova categoria") }, placeholder = { Text("Ex.: Educação") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp)); Button({ addCustomCategory() }, enabled = newCategoryName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Adicionar categoria") }
            Spacer(Modifier.height(12.dp)); Text("Data da movimentação"); Spacer(Modifier.height(6.dp))
            OutlinedButton({ openDatePicker() }, modifier = Modifier.fillMaxWidth()) { Text(SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(movementTimestamp))) }
        }
    }, confirmButton = {
        Button(enabled = amount != null && amount > 0.0, onClick = {
            val validAmount = amount ?: return@Button
            val createdAt = System.currentTimeMillis()
            val history = JSONArray(prefs.getString("transaction_history", "[]") ?: "[]")
            history.put(JSONObject()
                .put("timestamp", movementTimestamp)
                .put("movementTimestamp", movementTimestamp)
                .put("createdTimestamp", createdAt)
                .put("package", "MANUAL")
                .put("title", description.ifBlank { "Lançamento manual" })
                .put("text", description)
                .put("type", type)
                .put("category", category)
                .put("confidence", 100)
                .put("classification", "CONFIRMADA")
                .put("amount", validAmount))
            prefs.edit().putString("transaction_history", history.toString()).apply(); onSaved()
        }) { Text("Salvar") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
