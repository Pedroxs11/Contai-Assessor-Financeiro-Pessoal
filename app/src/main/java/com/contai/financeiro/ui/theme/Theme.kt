package com.contai.financeiro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

private val ContaiLightColors = lightColorScheme(
    primary = ContaiGreen,
    onPrimary = ContaiSurface,
    primaryContainer = ContaiGreenLight,
    onPrimaryContainer = ContaiGreenDark,
    background = ContaiBackground,
    onBackground = ContaiTextPrimary,
    surface = ContaiSurface,
    onSurface = ContaiTextPrimary,
    secondary = ContaiIncome,
    tertiary = ContaiExpense,
    error = ContaiExpense
)

private val ContaiDarkColors = darkColorScheme(
    primary = ContaiYellow,
    onPrimary = ContaiOnYellow,
    primaryContainer = ContaiDarkYellowContainer,
    onPrimaryContainer = ContaiDarkTextPrimary,
    background = ContaiDarkBackground,
    onBackground = ContaiDarkTextPrimary,
    surface = ContaiDarkSurface,
    onSurface = ContaiDarkTextPrimary,
    surfaceVariant = ContaiDarkSurfaceVariant,
    onSurfaceVariant = ContaiDarkTextSecondary,
    secondary = ContaiIncome,
    onSecondary = ContaiOnYellow,
    tertiary = ContaiExpense,
    error = ContaiExpense,
    outline = ContaiDarkOutline
)

@Composable
fun ContaiTheme(
    mode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (mode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) ContaiDarkColors else ContaiLightColors,
        content = content
    )
}
