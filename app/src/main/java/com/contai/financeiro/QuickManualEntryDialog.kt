package com.contai.financeiro

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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

@Composable
fun QuickManualEntryDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("contai_notifications", Context.MODE_PRIVATE)
    }

    var type by remember { mutableStateOf("DESPESA") }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Outros") }
    var description by remember { mutableStateOf("") }

    val normalizedAmount = amountText
        .trim()
        .replace("R$", "", ignoreCase = true)
        .replace(" ", "")
        .replace(".", "")
        .replace(",", ".")
    val amount = normalizedAmount.toDoubleOrNull()

    val customIncomeCategories = remember {
        prefs.getStringSet("custom_income_categories", emptySet())
            ?.toList()
            ?.sorted()
            .orEmpty()
    }
    val customExpenseCategories = remember {
        prefs.getStringSet("custom_expense_categories", emptySet())
            ?.toList()
            ?.sorted()
            .orEmpty()
    }

    val categories = if (type == "ENTRADA") {
        (listOf("Receitas", "Salário", "Pix recebido", "Outros") + customIncomeCategories)
            .distinct()
    } else {
        (listOf("Alimentação", "Transporte", "Combustível", "Moradia", "Saúde", "Compras", "Lazer", "Outros") + customExpenseCategories)
            .distinct()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar lançamento") },
        text = {
            Column(
                modifier = Modifier
                    .height(420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Tipo")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "ENTRADA",
                        onClick = {
                            type = "ENTRADA"
                            category = "Receitas"
                        },
                        label = { Text("Entrada") }
                    )
                    FilterChip(
                        selected = type == "DESPESA",
                        onClick = {
                            type = "DESPESA"
                            category = "Outros"
                        },
                        label = { Text("Despesa") }
                    )
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Valor") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))
                Text("Categoria")
                categories.forEach { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = { category = item },
                        label = { Text(item) }
                    )
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = amount != null && amount > 0.0,
                onClick = {
                    val validAmount = amount ?: return@Button
                    val history = JSONArray(prefs.getString("transaction_history", "[]") ?: "[]")
                    history.put(
                        JSONObject()
                            .put("timestamp", System.currentTimeMillis())
                            .put("package", "MANUAL")
                            .put("title", description.ifBlank { "Lançamento manual" })
                            .put("text", description)
                            .put("type", type)
                            .put("category", category)
                            .put("confidence", 100)
                            .put("classification", "CONFIRMADA")
                            .put("amount", validAmount)
                    )
                    prefs.edit().putString("transaction_history", history.toString()).apply()
                    onSaved()
                }
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
