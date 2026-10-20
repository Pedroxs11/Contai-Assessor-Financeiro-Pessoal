package com.contai.financeiro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinancialParserTest {

    @Test
    fun `pix recebido de app financeiro confiavel vira entrada confirmada`() {
        val result = FinancialParser.parse(
            packageName = "com.nu.production",
            title = "Pix recebido",
            text = "Você recebeu um Pix de R$ 1.234,56"
        )

        assertEquals(1234.56, result.amount!!, 0.001)
        assertEquals("ENTRADA", result.type)
        assertEquals("CONFIRMADA", result.classification)
        assertEquals(95, result.confidence)
    }

    @Test
    fun `pix enviado de app financeiro confiavel vira despesa confirmada`() {
        val result = FinancialParser.parse(
            packageName = "com.itau.app",
            title = "Pix enviado",
            text = "Pix enviado no valor de R$ 89,90"
        )

        assertEquals(89.90, result.amount!!, 0.001)
        assertEquals("DESPESA", result.type)
        assertEquals("CONFIRMADA", result.classification)
    }

    @Test
    fun `texto agregado no estilo Samsung preserva valor e direcao`() {
        val result = FinancialParser.parse(
            packageName = "com.nu.production",
            title = "Nubank",
            text = "Pix recebido | R$ 250,00 | Transferência recebida com sucesso"
        )

        assertEquals(250.0, result.amount!!, 0.001)
        assertEquals("ENTRADA", result.type)
        assertEquals("CONFIRMADA", result.classification)
    }

    @Test
    fun `promocao com valor nao deve virar transacao`() {
        val result = FinancialParser.parse(
            packageName = "com.loja.app",
            title = "Oferta imperdível",
            text = "Aproveite por apenas R$ 99,90"
        )

        assertEquals("NAO_FINANCEIRA", result.classification)
        assertEquals(10, result.confidence)
    }

    @Test
    fun `valor sem contexto financeiro fica pendente e nao confirmado`() {
        val result = FinancialParser.parse(
            packageName = "com.app.generico",
            title = "Aviso",
            text = "Valor R$ 35,00"
        )

        assertEquals(35.0, result.amount!!, 0.001)
        assertEquals("NAO_IDENTIFICADO", result.type)
        assertEquals("POSSIVEL", result.classification)
        assertNotEquals("CONFIRMADA", result.classification)
    }

    @Test
    fun `notificacao sem valor nao deve gerar transacao financeira`() {
        val result = FinancialParser.parse(
            packageName = "com.nu.production",
            title = "Olá",
            text = "Confira as novidades do app"
        )

        assertNull(result.amount)
        assertEquals("NAO_FINANCEIRA", result.classification)
    }

    @Test
    fun `valor sem R$ com milhar separado por espaco e reconhecido apos rotulo`() {
        val result = FinancialParser.parse(
            packageName = "com.nu.production",
            title = "Pix recebido",
            text = "Você recebeu valor 1 234,56"
        )

        assertEquals(1234.56, result.amount!!, 0.001)
        assertEquals("ENTRADA", result.type)
        assertEquals("CONFIRMADA", result.classification)
    }

    @Test
    fun `valor sem R$ com ponto de milhar e reconhecido apos rotulo`() {
        val result = FinancialParser.parse(
            packageName = "com.nu.production",
            title = "Pix recebido",
            text = "Pix recebido no valor de 12.345,67"
        )

        assertEquals(12345.67, result.amount!!, 0.001)
        assertEquals("ENTRADA", result.type)
        assertEquals("CONFIRMADA", result.classification)
    }

    @Test
    fun `numero decimal sem simbolo nem rotulo financeiro nao e extraido`() {
        val result = FinancialParser.parse(
            packageName = "com.app.generico",
            title = "Aviso",
            text = "Saldo atualizado em 1234,56"
        )

        assertNull(result.amount)
        assertEquals("NAO_FINANCEIRA", result.classification)
    }

    @Test
    fun `valor com tres casas decimais apos R$ e rejeitado sem truncamento`() {
        val result = FinancialParser.parse(
            packageName = "com.nu.production",
            title = "Pix recebido",
            text = "Você recebeu um Pix de R$ 1.234,567"
        )

        assertNull(result.amount)
        assertEquals("NAO_FINANCEIRA", result.classification)
    }

    @Test
    fun `valor rotulado sem R$ com tres casas decimais e rejeitado`() {
        val result = FinancialParser.parse(
            packageName = "com.nu.production",
            title = "Pix recebido",
            text = "Pix recebido no valor de 1234,567"
        )

        assertNull(result.amount)
        assertEquals("NAO_FINANCEIRA", result.classification)
    }

    @Test
    fun `valor sem agrupamento com duas casas decimais continua reconhecido`() {
        val result = FinancialParser.parse(
            packageName = "com.nu.production",
            title = "Pix recebido",
            text = "Você recebeu um Pix de R$ 1234,56"
        )

        assertEquals(1234.56, result.amount!!, 0.001)
        assertEquals("ENTRADA", result.type)
        assertEquals("CONFIRMADA", result.classification)
    }

    @Test
    fun `different currency values are marked ambiguous`() {
        val result = FinancialParser.parse(
            packageName = "com.nu.production",
            title = "Saldo R$ 2.000,00",
            text = "Pix recebido R$ 150,00"
        )
        assertNull(result.amount)
        assertEquals("AMBIGUA", result.classification)
    }
}
