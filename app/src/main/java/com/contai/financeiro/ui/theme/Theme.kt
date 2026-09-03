package com.contai.financeiro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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

@Composable
fun ContaiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ContaiLightColors,
        content = content
    )
}
