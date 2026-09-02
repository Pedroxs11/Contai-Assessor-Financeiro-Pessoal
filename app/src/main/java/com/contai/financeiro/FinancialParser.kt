package com.contai.financeiro

data class ParsedTransaction(
    val amount: Double?,
    val type: String,
    val description: String,
    val confidence: Int,
    val classification: String
)

object FinancialParser {

    private val amountRegex =
        Regex("""R\$\s*([0-9]{1,3}(?:\.[0-9]{3})*,[0-9]{2})""")

    fun parse(packageName: String, title: String, text: String): ParsedTransaction {
        val content = "$title $text".trim()

        val amount = amountRegex
            .find(content)
            ?.groupValues
            ?.get(1)
            ?.replace(".", "")
            ?.replace(",", ".")
            ?.toDoubleOrNull()

        val lower = content.lowercase()
        val packageLower = packageName.lowercase()

        val trustedFinancialPackages = listOf(
            "santander",
            "nubank",
            "com.nu.production",
            "br.com.digio.uber",
            "itau",
            "bradesco",
            "inter",
            "mercadopago",
            "picpay",
            "caixa",
            "bancodobrasil"
        )

        val isTrustedFinancialApp =
            trustedFinancialPackages.any { packageLower.contains(it) }



        val expenseWords = listOf(
            "compra",
            "pagamento",
            "débito",
            "debito",
            "gasto",
            "cobrança",
            "cobranca",
            "pix enviado",
            "transferência enviada",
            "transferencia enviada"
        )

        val incomeWords = listOf(
            "pix recebido",
            "recebido",
            "depósito",
            "deposito",
            "crédito recebido",
            "credito recebido",
            "transferência recebida",
            "transferencia recebida",
            "recebemos sua transferência",
            "recebemos sua transferencia"
        )


        val promoWords = listOf(
            "promoção",
            "promocao",
            "oferta",
            "desconto",
            "cupom",
            "compre aqui",
            "até 3x",
            "ate 3x",
            "frete grátis",
            "frete gratis",
            "aproveite",
            "imperdível",
            "imperdivel",
            "cashback",
            "ganhe",
            "economize",
            "por apenas",
            "clique aqui",
            "compre agora"
        )

        val hasPromoWords =
            promoWords.any { lower.contains(it) }

        val type = when {
            incomeWords.any { lower.contains(it) } -> "ENTRADA"
            expenseWords.any { lower.contains(it) } -> "DESPESA"
            else -> "NAO_IDENTIFICADO"
        }

        val confidence = when {
            hasPromoWords -> 10
            isTrustedFinancialApp && amount != null && type != "NAO_IDENTIFICADO" -> 95
            amount != null && type != "NAO_IDENTIFICADO" -> 80
            amount != null -> 60
            else -> 20
        }

        val classification = when {
            hasPromoWords -> "NAO_FINANCEIRA"
            isTrustedFinancialApp && amount != null && type != "NAO_IDENTIFICADO" -> "CONFIRMADA"
            amount != null && type != "NAO_IDENTIFICADO" -> "POSSIVEL"
            amount != null -> "POSSIVEL"
            else -> "NAO_FINANCEIRA"
        }

        return ParsedTransaction(
            amount = amount,
            type = type,
            description = content,
            confidence = confidence,
            classification = classification
        )
    }
}
