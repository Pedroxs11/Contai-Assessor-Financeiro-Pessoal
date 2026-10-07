package com.contai.financeiro

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfessorModelsTest {

    @Test
    fun pagamento_marcado_como_pago_permanece_pago() {
        val payment = ProfessorPayment(
            id = 1L,
            studentId = 10L,
            referenceMonth = "2026-09",
            amountCents = 15000L,
            status = ProfessorPaymentStatus.PAGO
        )

        assertEquals(
            ProfessorPaymentStatus.PAGO,
            professorPaymentStatus(payment, "2026-10")
        )
    }

    @Test
    fun pagamento_do_mes_atual_fica_pendente() {
        val payment = ProfessorPayment(
            id = 2L,
            studentId = 10L,
            referenceMonth = "2026-10",
            amountCents = 15000L
        )

        assertEquals(
            ProfessorPaymentStatus.PENDENTE,
            professorPaymentStatus(payment, "2026-10")
        )
    }

    @Test
    fun pagamento_de_mes_anterior_fica_atrasado() {
        val payment = ProfessorPayment(
            id = 3L,
            studentId = 10L,
            referenceMonth = "2026-09",
            amountCents = 15000L
        )

        assertEquals(
            ProfessorPaymentStatus.ATRASADO,
            professorPaymentStatus(payment, "2026-10")
        )
    }
}
