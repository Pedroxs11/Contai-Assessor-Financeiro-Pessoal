package com.contai.financeiro

import java.text.NumberFormat
import java.util.Locale

private val brLocale = Locale("pt", "BR")

fun formatCurrency(value: Double): String {
    return NumberFormat.getCurrencyInstance(brLocale).format(value)
}
