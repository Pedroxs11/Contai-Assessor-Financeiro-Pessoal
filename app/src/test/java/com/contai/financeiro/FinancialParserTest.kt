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
    fun `pix recebido com texto multiline do Android preserva valor e direcao`() {
        val result = FinancialParser.parse(
            packageName = "com.nu.production",
            title = "Pix recebido",
            text = "Transferência recebida • Você recebeu\nR$ 47,35\nde Maria"
        )

        assertEquals(47.35, result.amount!!, 0.001)
        assertEquals("ENTRADA", result.type)
        assertEquals("CONFIRMADA", result.classification)
    }

    @Test
    fun `pix enviado duplicado no texto agregado continua com um unico valor valido`() {
        val result = FinancialParser.parse(
            packageName = "com.itau.app",
            title = "Pix enviado",
            text = "Pix enviado R$ 18,90 • Pix enviado R$ 18,90"
        )

        assertEquals(18.90, result.amount!!, 0.001)
        assertEquals("DESPESA", result.type)
        assertEquals("CONFIRMADA", result.classification)
    }

    @Test
    fun `dividendo recebido identifica entrada de investimento`() {
        val result = FinancialParser.parse(
            packageName = "com.nu.production",
            title = "Rendimento de dividendos",
            text = "Você recebeu R$ 32,45 em dividendos"
        )

        assertEquals(32.45, result.amount!!, 0.001)
        assertEquals("ENTRADA", result.type)
        assertEquals("DIVIDENDO", result.investmentType)
        assertEquals("CONFIRMADA", result.classification)
    }
    @Test
    fun `valor com espaco unicode antes do numero continua sendo extraido`() {
        val result = FinancialParser.parse(
            packageName = "com.nu.production",
            title = "Pix recebido",
            text = "Você recebeu R$\u00A01\u202F234,56"
        )

        assertEquals(1234.56, result.amount!!, 0.001)
        assertEquals("ENTRADA", result.type)
        assertEquals("CONFIRMADA", result.classification)
    }

}
