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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
    val customIncomeCategories = remember(showCategories) {
        transactionPrefs.getStringSet("custom_income_categories", emptySet())?.toList()?.sorted().orEmpty()
    }
    val customExpenseCategories = remember(showCategories) {
        transactionPrefs.getStringSet("custom_expense_categories", emptySet())?.toList()?.sorted().orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Perfil e configurações", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Personalize o Contai do seu jeito.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Aparência", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Escolha como o Contai deve aparecer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = themeMode == AppThemeMode.SYSTEM,
                        onClick = { onThemeModeChange(AppThemeMode.SYSTEM) },
                        label = { Text("Sistema") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = themeMode == AppThemeMode.LIGHT,
                        onClick = { onThemeModeChange(AppThemeMode.LIGHT) },
                        label = { Text("Claro") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = themeMode == AppThemeMode.DARK,
                        onClick = { onThemeModeChange(AppThemeMode.DARK) },
                        label = { Text("Escuro") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showCategories = !showCategories }
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Categorias", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Gerencie suas categorias de entradas e despesas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(if (showCategories) "⌃" else "›", style = MaterialTheme.typography.headlineSmall)
                }

                if (showCategories) {
                    Spacer(Modifier.height(16.dp))
                    Text("Entradas", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        (listOf("Receitas", "Salário", "Pix recebido", "Outros") + customIncomeCategories)
                            .distinct()
                            .joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("Despesas", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        (listOf("Alimentação", "Transporte", "Combustível", "Moradia", "Saúde", "Compras", "Lazer", "Outros") + customExpenseCategories)
                            .distinct()
                            .joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "As categorias padrão são protegidas. As personalizadas poderão ser editadas ou excluídas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SettingsCard("Captura automática", "Permissão, status e diagnóstico")

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ocultar valores", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Esconde valores financeiros ao abrir o aplicativo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = hideValues,
                    onCheckedChange = onHideValuesChange
                )
            }
        }

        SettingsCard("Dados", "Gerencie os dados financeiros salvos no aparelho")
        SettingsCard("Sobre", "Versão e informações do Contai")
    }
}

@Composable
private fun SettingsCard(title: String, description: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}
