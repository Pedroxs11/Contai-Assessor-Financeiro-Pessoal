package com.contai.financeiro

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.content.ContextCompat
import com.contai.financeiro.ui.theme.AppThemeMode
import com.contai.financeiro.ui.theme.ContaiTheme

private const val SETTINGS_PREFS = "contai_settings"
private const val THEME_MODE_KEY = "theme_mode"
private const val HIDE_VALUES_KEY = "hide_values"
private const val PERMISSION_SETUP_SHOWN_KEY = "permission_setup_shown"

class ContaiShellActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        openNotificationAccessSettings()
    }

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
        val shouldShowPermissionSetup = !settings.getBoolean(PERMISSION_SETUP_SHOWN_KEY, false) &&
            !hasNotificationListenerAccess()

        setContent {
            var themeMode by remember { mutableStateOf(savedThemeMode) }
            var hideValues by remember { mutableStateOf(savedHideValues) }
            var showPermissionSetup by remember { mutableStateOf(shouldShowPermissionSetup) }

            ContaiTheme(mode = themeMode) {
                if (showPermissionSetup) {
                    PermissionSetupDialog(
                        onConfigure = {
                            settings.edit().putBoolean(PERMISSION_SETUP_SHOWN_KEY, true).apply()
                            showPermissionSetup = false
                            beginNotificationSetup()
                        },
                        onLater = {
                            settings.edit().putBoolean(PERMISSION_SETUP_SHOWN_KEY, true).apply()
                            showPermissionSetup = false
                        }
                    )
                }

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

    private fun beginNotificationSetup() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationAccessSettings()
        }
    }

    private fun openNotificationAccessSettings() {
        if (hasNotificationListenerAccess()) return

        val listenerComponent = ComponentName(this, FinanceNotificationListener::class.java)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    listenerComponent.flattenToString()
                )
            }
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

        runCatching {
            startActivity(intent)
        }.onFailure {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun hasNotificationListenerAccess(): Boolean {
        val listenerComponent = ComponentName(this, FinanceNotificationListener::class.java)
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        return enabledListeners.contains(listenerComponent.flattenToString())
    }

    override fun onResume() {
        super.onResume()

        val listenerComponent = ComponentName(
            this,
            FinanceNotificationListener::class.java
        )

        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()

        if (enabledListeners.contains(listenerComponent.flattenToString())) {
            getSharedPreferences("contai_notifications", Context.MODE_PRIVATE)
                .edit()
                .putString("listener_lifecycle_event", "rebindRequestedOnAppResume")
                .putLong("listener_lifecycle_at", System.currentTimeMillis())
                .apply()

            NotificationListenerService.requestRebind(listenerComponent)
        }
    }
}

@Composable
private fun PermissionSetupDialog(
    onConfigure: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Ativar notificações") },
        text = {
            Text(
                "Para enviar lembretes e identificar movimentações automaticamente, " +
                    "o Contai precisa de duas permissões do Android. Primeiro permita as " +
                    "notificações e depois ative o acesso às notificações na tela do sistema."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfigure) {
                Text("Configurar agora")
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text("Depois")
            }
        }
    )
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
                ContaiDestination.AGENDA -> AgendaScreen()
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
