package com.contai.financeiro

enum class ProfessorPaymentStatus {
    PAGO,
    PENDENTE,
    ATRASADO
}

data class ProfessorStudent(
    val id: Long,
    val name: String,
    val phone: String = "",
    val monthlyFeeCents: Long = 0L,
    val active: Boolean = true
)

data class ProfessorPayment(
    val id: Long,
    val studentId: Long,
    val referenceMonth: String,
    val amountCents: Long,
    val status: ProfessorPaymentStatus = ProfessorPaymentStatus.PENDENTE,
    val paidAt: Long? = null,
    val note: String = ""
)

fun professorPaymentStatus(
    payment: ProfessorPayment,
    todayReferenceMonth: String
): ProfessorPaymentStatus {
    if (payment.status == ProfessorPaymentStatus.PAGO) return ProfessorPaymentStatus.PAGO
    return if (payment.referenceMonth < todayReferenceMonth) {
        ProfessorPaymentStatus.ATRASADO
    } else {
        ProfessorPaymentStatus.PENDENTE
    }
}
