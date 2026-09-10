package com.contai.financeiro

data class ParsedTransaction(
    val amount: Double?,
    val type: String,
    val description: String,
    val confidence: Int,
    val classification: String,
    val investmentType: String = ""
)

object FinancialParser {

    private val amountPatterns = listOf(
        Regex("""R\$\s*([0-9]{1,3}(?:\.[0-9]{3})*,[0-9]{2})""", RegexOption.IGNORE_CASE),
        Regex("""R\$\s*([0-9]+,[0-9]{2})""", RegexOption.IGNORE_CASE),
        Regex("""(?:valor|recebeu|recebido|enviado|pagamento|pix)\D{0,20}([0-9]+,[0-9]{2})""", RegexOption.IGNORE_CASE)
    )

    private fun extractAmount(content: String): Double? {
        val raw = amountPatterns.firstNotNullOfOrNull { regex ->
            regex.find(content)?.groupValues?.getOrNull(1)
        } ?: return null
        return raw.replace(".", "").replace(",", ".").toDoubleOrNull()
    }

    fun parse(packageName: String, title: String, text: String): ParsedTransaction {
        val content = "$title $text".replace(Regex("""\s+"""), " ").trim()
        val amount = extractAmount(content)
        val lower = content.lowercase()
        val packageLower = packageName.lowercase()

        val trustedFinancialPackages = listOf(
            "santander", "nubank", "com.nu.production", "br.com.digio.uber",
            "itau", "itaú", "bradesco", "inter", "mercadopago", "mercado pago",
            "picpay", "caixa", "bancodobrasil", "banco do brasil", "c6", "neon"
        )
        val isTrustedFinancialApp = trustedFinancialPackages.any { packageLower.contains(it) }

        val expenseWords = listOf(
            "compra", "pagamento", "débito", "debito", "gasto", "cobrança", "cobranca",
            "pix enviado", "pix realizado", "pix feito", "você fez um pix", "voce fez um pix",
            "transferência enviada", "transferencia enviada", "transferência realizada",
            "transferencia realizada", "pagou", "valor debitado", "saiu da sua conta"
        )

        val incomeWords = listOf(
            "pix recebido", "você recebeu um pix", "voce recebeu um pix", "recebeu um pix",
            "pix caiu", "pix recebido com sucesso", "recebido", "depósito", "deposito",
            "crédito recebido", "credito recebido", "transferência recebida", "transferencia recebida",
            "recebemos sua transferência", "recebemos sua transferencia", "valor creditado",
            "entrou na sua conta", "creditado em sua conta"
        )

        val investmentType = when {
            lower.contains("dividendo") || lower.contains("dividendos") -> "DIVIDENDO"
            lower.contains("jcp") || lower.contains("juros sobre capital próprio") || lower.contains("juros sobre capital proprio") -> "JCP"
            lower.contains("rendimento") || lower.contains("rendimentos") -> "RENDIMENTO"
            else -> ""
        }

        val promoWords = listOf(
            "promoção", "promocao", "oferta", "desconto", "cupom", "compre aqui",
            "até 3x", "ate 3x", "frete grátis", "frete gratis", "aproveite", "imperdível",
            "imperdivel", "cashback", "ganhe", "economize", "por apenas", "clique aqui", "compre agora"
        )
        val hasPromoWords = promoWords.any { lower.contains(it) }

        val type = when {
            investmentType.isNotBlank() -> "ENTRADA"
            incomeWords.any { lower.contains(it) } -> "ENTRADA"
            expenseWords.any { lower.contains(it) } -> "DESPESA"
            else -> "NAO_IDENTIFICADO"
        }

        val confidence = when {
            hasPromoWords -> 10
            isTrustedFinancialApp && amount != null && type != "NAO_IDENTIFICADO" -> 95
            amount != null && type != "NAO_IDENTIFICADO" -> 80
            isTrustedFinancialApp && amount != null -> 70
            amount != null -> 60
            else -> 20
        }

        val classification = when {
            hasPromoWords -> "NAO_FINANCEIRA"
            isTrustedFinancialApp && amount != null && type != "NAO_IDENTIFICADO" -> "CONFIRMADA"
            amount != null -> "POSSIVEL"
            else -> "NAO_FINANCEIRA"
        }

        return ParsedTransaction(
            amount = amount,
            type = type,
            description = content,
            confidence = confidence,
            classification = classification,
            investmentType = investmentType
        )
    }
}
