package com.contai.financeiro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfessorStorageCodecTest {

    @Test
    fun alunos_sao_serializados_e_recuperados_sem_perder_dados() {
        val students = listOf(
            ProfessorStudent(1L, "João\tda Silva", "11999999999", 25000L, true),
            ProfessorStudent(2L, "Maria\nSilva", "", 18000L, false)
        )

        val restored = ProfessorStorageCodec.decodeStudents(
            ProfessorStorageCodec.encodeStudents(students)
        )

        assertEquals(students, restored)
    }

    @Test
    fun pagamentos_sao_serializados_e_recuperados_sem_perder_status() {
        val payments = listOf(
            ProfessorPayment(
                id = 10L,
                studentId = 1L,
                referenceMonth = "2026-10",
                amountCents = 25000L,
                status = ProfessorPaymentStatus.PAGO,
                paidAt = 1790800000000L,
                note = "Pix\tconfirmado"
            ),
            ProfessorPayment(
                id = 11L,
                studentId = 1L,
                referenceMonth = "2026-11",
                amountCents = 25000L
            )
        )

        val restored = ProfessorStorageCodec.decodePayments(
            ProfessorStorageCodec.encodePayments(payments)
        )

        assertEquals(payments, restored)
    }

    @Test
    fun registros_invalidos_sao_ignorados() {
        val restored = ProfessorStorageCodec.decodeStudents("invalido\n1\tAna\t\t20000\ttrue")
        assertEquals(1, restored.size)
        assertEquals("Ana", restored.first().name)
        assertTrue(restored.first().active)
    }
}
