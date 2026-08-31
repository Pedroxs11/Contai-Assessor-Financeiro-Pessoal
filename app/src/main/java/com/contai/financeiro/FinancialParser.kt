package com.contai.financeiro

data class ParsedTransaction(
    val amount: Double?,
    val type: String,
    val description: String,
    val confidence: Int
)

object FinancialParser {

    private val amountRegex =
        Regex("""R\$\s*([0-9]{1,3}(?:\.[0-9]{3})*,[0-9]{2})""")

    fun parse(title: String, text: String): ParsedTransaction {
        val content = "$title $text".trim()

        val amount = amountRegex
            .find(content)
            ?.groupValues
            ?.get(1)
            ?.replace(".", "")
            ?.replace(",", ".")
            ?.toDoubleOrNull()

        val lower = content.lowercase()

        val expenseWords = listOf(
            "compra",
            "pagamento",
            "débito",
            "debito",
            "gasto",
            "cobrança",
            "cobranca"
        )

        val incomeWords = listOf(
            "pix recebido",
            "recebido",
            "depósito",
            "deposito",
            "crédito recebido",
            "credito recebido"
        )

        val type = when {
            incomeWords.any { lower.contains(it) } -> "ENTRADA"
            expenseWords.any { lower.contains(it) } -> "DESPESA"
            else -> "NAO_IDENTIFICADO"
        }

        val confidence = when {
            amount != null && type != "NAO_IDENTIFICADO" -> 90
            amount != null -> 60
            else -> 20
        }

        return ParsedTransaction(
            amount = amount,
            type = type,
            description = content,
            confidence = confidence
        )
    }
}
