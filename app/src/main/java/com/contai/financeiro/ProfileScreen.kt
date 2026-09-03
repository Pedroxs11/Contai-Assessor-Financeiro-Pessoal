package com.contai.financeiro

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.contai.financeiro.ui.theme.AppThemeMode
import org.json.JSONArray

@Composable
fun ProfileScreen(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    hideValues: Boolean,
    onHideValuesChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val transactionPrefs = context.getSharedPreferences("contai_notifications", Context.MODE_PRIVATE)
    var showCategories by remember { mutableStateOf(false) }
    var categoryType by remember { mutableStateOf("DESPESA") }
    var newCategoryName by remember { mutableStateOf("") }
    var categoryToRename by remember { mutableStateOf("") }
    var renamedCategoryName by remember { mutableStateOf("") }
    var categoryToDelete by remember { mutableStateOf("") }
    var replacementCategory by remember { mutableStateOf("") }
    var customIncomeCategories by remember {
        mutableStateOf(transactionPrefs.getStringSet("custom_income_categories", emptySet())?.toList()?.sorted().orEmpty())
    }
    var customExpenseCategories by remember {
        mutableStateOf(transactionPrefs.getStringSet("custom_expense_categories", emptySet())?.toList()?.sorted().orEmpty())
    }

    fun addCategory() {
        val cleanName = newCategoryName.trim()
        if (cleanName.isBlank()) return
        if (categoryType == "ENTRADA") {
            val updated = (customIncomeCategories + cleanName).distinct().sorted()
            transactionPrefs.edit().putStringSet("custom_income_categories", updated.toSet()).apply()
            customIncomeCategories = updated
        } else {
            val updated = (customExpenseCategories + cleanName).distinct().sorted()
            transactionPrefs.edit().putStringSet("custom_expense_categories", updated.toSet()).apply()
            customExpenseCategories = updated
        }
        newCategoryName = ""
    }

    fun renameCategory() {
        val oldName = categoryToRename.trim()
        val newName = renamedCategoryName.trim()
        if (oldName.isBlank() || newName.isBlank() || oldName == newName) return

        if (oldName in customIncomeCategories) {
            val updated = customIncomeCategories.map { if (it == oldName) newName else it }.distinct().sorted()
            transactionPrefs.edit().putStringSet("custom_income_categories", updated.toSet()).apply()
            customIncomeCategories = updated
        }
        if (oldName in customExpenseCategories) {
            val updated = customExpenseCategories.map { if (it == oldName) newName else it }.distinct().sorted()
            transactionPrefs.edit().putStringSet("custom_expense_categories", updated.toSet()).apply()
            customExpenseCategories = updated
        }

        val historyJson = JSONArray(transactionPrefs.getString("transaction_history", "[]") ?: "[]")
        for (i in 0 until historyJson.length()) {
            val item = historyJson.getJSONObject(i)
            if (item.optString("category") == oldName) item.put("category", newName)
        }
        transactionPrefs.edit().putString("transaction_history", historyJson.toString()).apply()

        categoryToRename = ""
        renamedCategoryName = ""
    }

    fun deleteCategory() {
        val oldName = categoryToDelete
        val newName = replacementCategory
        if (oldName.isBlank() || newName.isBlank() || oldName == newName) return

        val historyJson = JSONArray(transactionPrefs.getString("transaction_history", "[]") ?: "[]")
        for (i in 0 until historyJson.length()) {
            val item = historyJson.getJSONObject(i)
            if (item.optString("category") == oldName) item.put("category", newName)
        }
        transactionPrefs.edit().putString("transaction_history", historyJson.toString()).apply()

        if (oldName in customIncomeCategories) {
            val updated = customIncomeCategories.filterNot { it == oldName }
            transactionPrefs.edit().putStringSet("custom_income_categories", updated.toSet()).apply()
            customIncomeCategories = updated
        }
        if (oldName in customExpenseCategories) {
            val updated = customExpenseCategories.filterNot { it == oldName }
            transactionPrefs.edit().putStringSet("custom_expense_categories", updated.toSet()).apply()
            customExpenseCategories = updated
        }
        if (categoryToRename == oldName) {
            categoryToRename = ""
            renamedCategoryName = ""
        }
        categoryToDelete = ""
        replacementCategory = ""
    }

    if (categoryToDelete.isNotBlank()) {
        val isIncome = categoryToDelete in customIncomeCategories
        val replacementOptions = if (isIncome) {
            (listOf("Receitas", "Salário", "Pix recebido", "Outros") + customIncomeCategories)
        } else {
            (listOf("Alimentação", "Transporte", "Combustível", "Moradia", "Saúde", "Compras", "Lazer", "Outros") + customExpenseCategories)
        }.distinct().filterNot { it == categoryToDelete }.sorted()

        AlertDialog(
            onDismissRequest = {
                categoryToDelete = ""
                replacementCategory = ""
            },
            title = { Text("Excluir categoria?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Escolha para qual categoria mover os lançamentos de “$categoryToDelete”. Nenhum lançamento será apagado.")
                    replacementOptions.forEach { category ->
                        FilterChip(
                            selected = replacementCategory == category,
                            onClick = { replacementCategory = category },
                            label = { Text(category) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { deleteCategory() },
                    enabled = replacementCategory.isNotBlank()
                ) { Text("Mover e excluir") }
            },
            dismissButton = {
                TextButton(onClick = {
                    categoryToDelete = ""
                    replacementCategory = ""
                }) { Text("Cancelar") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Perfil e configurações", style = MaterialTheme.typography.headlineMedium)
        Text("Personalize o Contai do seu jeito.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Aparência", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("Escolha como o Contai deve aparecer.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = themeMode == AppThemeMode.SYSTEM, onClick = { onThemeModeChange(AppThemeMode.SYSTEM) }, label = { Text("Sistema") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = themeMode == AppThemeMode.LIGHT, onClick = { onThemeModeChange(AppThemeMode.LIGHT) }, label = { Text("Claro") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = themeMode == AppThemeMode.DARK, onClick = { onThemeModeChange(AppThemeMode.DARK) }, label = { Text("Escuro") }, modifier = Modifier.weight(1f))
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), onClick = { showCategories = !showCategories }) {
            Column(Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Categorias", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("Gerencie suas categorias de entradas e despesas", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (showCategories) "⌃" else "›", style = MaterialTheme.typography.headlineSmall)
                }

                if (showCategories) {
                    Spacer(Modifier.height(16.dp))
                    Text("Entradas", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text((listOf("Receitas", "Salário", "Pix recebido", "Outros") + customIncomeCategories).distinct().joinToString(" • "), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(14.dp))
                    Text("Despesas", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text((listOf("Alimentação", "Transporte", "Combustível", "Moradia", "Saúde", "Compras", "Lazer", "Outros") + customExpenseCategories).distinct().joinToString(" • "), style = MaterialTheme.typography.bodyMedium)

                    Spacer(Modifier.height(16.dp))
                    Text("Nova categoria", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = categoryType == "ENTRADA", onClick = { categoryType = "ENTRADA" }, label = { Text("Entrada") }, modifier = Modifier.weight(1f))
                        FilterChip(selected = categoryType == "DESPESA", onClick = { categoryType = "DESPESA" }, label = { Text("Despesa") }, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = newCategoryName, onValueChange = { newCategoryName = it }, label = { Text("Nome da categoria") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { addCategory() }, enabled = newCategoryName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Adicionar categoria") }

                    val editableCategories = (customIncomeCategories + customExpenseCategories).distinct().sorted()
                    if (editableCategories.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        Text("Gerenciar categorias personalizadas", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        editableCategories.forEach { category ->
                            FilterChip(
                                selected = categoryToRename == category,
                                onClick = {
                                    categoryToRename = category
                                    renamedCategoryName = category
                                },
                                label = { Text(category) }
                            )
                        }
                        if (categoryToRename.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = renamedCategoryName, onValueChange = { renamedCategoryName = it }, label = { Text("Novo nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { renameCategory() }, enabled = renamedCategoryName.isNotBlank() && renamedCategoryName.trim() != categoryToRename, modifier = Modifier.fillMaxWidth()) { Text("Renomear") }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    categoryToDelete = categoryToRename
                                    replacementCategory = ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Excluir categoria") }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("As categorias padrão são protegidas. Ao excluir uma personalizada, os lançamentos são movidos para outra categoria escolhida por você.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        SettingsCard("Captura automática", "Permissão, status e diagnóstico")
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ocultar valores", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Esconde valores financeiros ao abrir o aplicativo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = hideValues, onCheckedChange = onHideValuesChange)
            }
        }
        SettingsCard("Dados", "Gerencie os dados financeiros salvos no aparelho")
        SettingsCard("Sobre", "Versão e informações do Contai")
    }
}

@Composable
private fun SettingsCard(title: String, description: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}
