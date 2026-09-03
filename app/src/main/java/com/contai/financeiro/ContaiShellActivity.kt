package com.contai.financeiro

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.contai.financeiro.ui.theme.AppThemeMode
import com.contai.financeiro.ui.theme.ContaiTheme

private const val SETTINGS_PREFS = "contai_settings"
private const val THEME_MODE_KEY = "theme_mode"
private const val HIDE_VALUES_KEY = "hide_values"

class ContaiShellActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val savedThemeMode = runCatching {
            AppThemeMode.valueOf(
                settings.getString(THEME_MODE_KEY, AppThemeMode.SYSTEM.name)
                    ?: AppThemeMode.SYSTEM.name
            )
        }.getOrDefault(AppThemeMode.SYSTEM)
        val savedHideValues = settings.getBoolean(HIDE_VALUES_KEY, false)

        setContent {
            var themeMode by remember { mutableStateOf(savedThemeMode) }
            var hideValues by remember { mutableStateOf(savedHideValues) }

            ContaiTheme(mode = themeMode) {
                ContaiShell(
                    themeMode = themeMode,
                    onThemeModeChange = { newMode ->
                        themeMode = newMode
                        settings.edit().putString(THEME_MODE_KEY, newMode.name).apply()
                    },
                    hideValues = hideValues,
                    onHideValuesChange = { shouldHide ->
                        hideValues = shouldHide
                        settings.edit().putBoolean(HIDE_VALUES_KEY, shouldHide).apply()
                    }
                )
            }
        }
    }
}

@Composable
private fun ContaiShell(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    hideValues: Boolean,
    onHideValuesChange: (Boolean) -> Unit
) {
    val destinations = listOf(
        ContaiDestination.HOME,
        ContaiDestination.AGENDA,
        ContaiDestination.REPORTS,
        ContaiDestination.PROFILE
    )

    var selectedDestination by remember { mutableStateOf(ContaiDestination.HOME) }
    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    var showQuickAdd by remember { mutableStateOf(false) }

    fun moveDestination(direction: Int) {
        val currentIndex = destinations.indexOf(selectedDestination)
        val nextIndex = (currentIndex + direction).coerceIn(destinations.indices)
        selectedDestination = destinations[nextIndex]
    }

    if (showQuickAdd) {
        QuickManualEntryDialog(
            onDismiss = { showQuickAdd = false },
            onSaved = {
                showQuickAdd = false
                selectedDestination = ContaiDestination.HOME
            }
        )
    }

    Scaffold(
        bottomBar = {
            ContaiBottomNavigation(
                selected = selectedDestination,
                onDestinationSelected = { selectedDestination = it },
                onAddClick = { showQuickAdd = true }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(selectedDestination) {
                    detectHorizontalDragGestures(
                        onDragStart = { horizontalDrag = 0f },
                        onHorizontalDrag = { _, dragAmount -> horizontalDrag += dragAmount },
                        onDragEnd = {
                            when {
                                horizontalDrag <= -120f -> moveDestination(1)
                                horizontalDrag >= 120f -> moveDestination(-1)
                            }
                            horizontalDrag = 0f
                        },
                        onDragCancel = { horizontalDrag = 0f }
                    )
                }
        ) {
            when (selectedDestination) {
                ContaiDestination.HOME -> ContaiApp(hideValuesByDefault = hideValues)
                ContaiDestination.AGENDA -> DestinationPlaceholder(
                    title = "Agenda",
                    description = "Seus compromissos e lembretes ficarão aqui."
                )
                ContaiDestination.REPORTS -> ReportsScreen(hideValues = hideValues)
                ContaiDestination.PROFILE -> ProfileScreen(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    hideValues = hideValues,
                    onHideValuesChange = onHideValuesChange
                )
            }
        }
    }
}

@Composable
private fun DestinationPlaceholder(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = description, style = MaterialTheme.typography.bodyLarge)
    }
}
