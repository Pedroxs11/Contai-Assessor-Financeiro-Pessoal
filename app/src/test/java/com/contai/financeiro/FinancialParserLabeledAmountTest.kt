package com.contai.financeiro

import org.junit.Assert.assertEquals
import org.junit.Test

class FinancialParserLabeledAmountTest {
    @Test
    fun groupedAmountWithoutCurrencySymbolIsParsed() {
        val result = FinancialParser.parse("com.example.wallet", "Pix recebido", "Valor: 1 234,56 recebido")
        assertEquals(1234.56, result.amount!!, 0.001)
        assertEquals("ENTRADA", result.type)
        assertEquals("POSSIVEL", result.classification)
    }
}
