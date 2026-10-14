package com.contai.financeiro

import org.junit.Assert.assertEquals
import org.junit.Test

class FinancialParserSpaceGroupingTest {

    @Test
    fun `valor com separador de milhar por espaco ASCII e extraido corretamente`() {
        val result = FinancialParser.parse(
            packageName = "com.nu.production",
            title = "Pix recebido",
            text = "Você recebeu R$ 1 234,56"
        )

        assertEquals(1234.56, result.amount!!, 0.001)
        assertEquals("ENTRADA", result.type)
        assertEquals("CONFIRMADA", result.classification)
    }

    @Test
    fun `valor com cinco digitos e separador de milhar por espaco ASCII e extraido corretamente`() {
        val result = FinancialParser.parse(
            packageName = "com.nu.production",
            title = "Pix recebido",
            text = "Você recebeu R$ 12 345,67"
        )

        assertEquals(12345.67, result.amount!!, 0.001)
        assertEquals("ENTRADA", result.type)
        assertEquals("CONFIRMADA", result.classification)
    }
}
