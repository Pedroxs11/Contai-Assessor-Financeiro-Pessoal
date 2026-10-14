package com.contai.financeiro

import java.text.Normalizer

/**
 * Interprets a user's free-text financial message as a proposal, never as a
 * confirmed transaction. The UI must ask for confirmation before persisting it.
 */
object MessageTransactionParser {

    private val expenseTerms = listOf(
        "gastei", "gasto", "paguei", "pagamento", "comprei", "compra",
        "abasteci", "despesa", "pix enviado", "transferi", "debitei",
        "debito", "saiu da conta"
    )

    private val incomeTerms = listOf(
        "recebi", "recebo", "ganhei", "salario", "faturei", "vendi",
        "rendimento", "dividendo", "pix recebido", "transferencia recebida",
        "entrada", "me pagaram", "caiu na conta"
    )

    private val currencyAmountRegex = Regex(
        """R\$[\s\u00A0\u202F]*([0-9]+(?:[. \u00A0\u202F][0-9]{3})*(?:,[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // Without R$, only accept an amount immediately after a financial-intent
    // phrase (optionally preceded by common connectors). This avoids treating
    // dates or unrelated numbers elsewhere in a sentence as money.
    private val anchoredAmountRegex = Regex(
        """\b(?:gastei|gasto|paguei|pagamento|comprei|compra|abasteci|despesa|recebi|recebo|ganhei|salario|faturei|vendi|rendimento|dividendo|pix enviado|pix recebido|transferi|entrada)\b\s*(?:(?:no valor|valor|foi|de)\s*){0,3}([0-9]+(?:[. \u00A0\u202F][0-9]{3})*(?:,[0-9]{1,2})?)"""
    )

    fun parse(message: String): ParsedTransaction {
        val original = message.trim()
        val normalized = normalize(original)
        val isExpense = containsAnyTerm(normalized, expenseTerms)
        val isIncome = containsAnyTerm(normalized, incomeTerms)

        // A single record cannot safely represent a message containing both
        // an income and an expense. Ask the UI to request a clearer message.
        if (isExpense && isIncome) {
            return ParsedTransaction(
                amount = null,
                type = "NAO_IDENTIFICADO",
                description = original,
                confidence = 10,
                classification = "POSSIVEL"
            )
        }

        val currencyMatches = currencyAmountRegex.findAll(original).toList()
        // Multiple explicit monetary values may describe multiple movements;
        // don't silently record only the first one.
        if (currencyMatches.size > 1) {
            return ParsedTransaction(
                amount = null,
                type = "NAO_IDENTIFICADO",
                description = original,
                confidence = 10,
                classification = "POSSIVEL"
            )
        }

        val rawAmount = currencyMatches.singleOrNull()?.groupValues?.getOrNull(1)
            ?: if (currencyMatches.isEmpty()) {
                anchoredAmountRegex.find(normalized)?.groupValues?.getOrNull(1)
            } else {
                null
            }
        val amount = rawAmount?.let(::parseAmount)

        val type = when {
            isExpense -> "DESPESA"
            isIncome -> "ENTRADA"
            else -> "NAO_IDENTIFICADO"
        }

        val investmentType = when {
            isIncome && normalized.contains("dividendo") -> "DIVIDENDO"
            isIncome && Regex("""\bjcp\b|juros sobre capital proprio""").containsMatchIn(normalized) -> "JCP"
            isIncome && normalized.contains("rendimento") -> "RENDIMENTO"
            else -> ""
        }

        val classification = when {
            type == "NAO_IDENTIFICADO" && amount == null -> "NAO_FINANCEIRA"
            else -> "POSSIVEL"
        }
        val confidence = when {
            type != "NAO_IDENTIFICADO" && amount != null -> 80
            type != "NAO_IDENTIFICADO" -> 30
            amount != null -> 20
            else -> 10
        }

        return ParsedTransaction(
            amount = amount,
            type = type,
            description = original,
            confidence = confidence,
            classification = classification,
            investmentType = investmentType
        )
    }

    private fun containsAnyTerm(text: String, terms: List<String>): Boolean =
        terms.any { term ->
            Regex("""\b${Regex.escape(term)}\b""").containsMatchIn(text)
        }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .lowercase()

    private fun parseAmount(raw: String): Double? =
        raw.replace(Regex("""[.\s\u00A0\u202F]"""), "")
            .replace(",", ".")
            .toDoubleOrNull()
}
