package com.contai.financeiro

import android.content.Context

private const val PREFS_NAME = "contai_professor"
private const val STUDENTS_KEY = "students"
private const val PAYMENTS_KEY = "payments"
private const val FIELD_SEPARATOR = "\t"
private const val RECORD_SEPARATOR = "\n"

data class ProfessorData(
    val students: List<ProfessorStudent> = emptyList(),
    val payments: List<ProfessorPayment> = emptyList()
)

object ProfessorStorageCodec {
    fun encodeStudents(students: List<ProfessorStudent>): String =
        students.joinToString(RECORD_SEPARATOR) {
            listOf(
                it.id.toString(),
                escape(it.name),
                escape(it.phone),
                it.monthlyFeeCents.toString(),
                it.active.toString()
            ).joinToString(FIELD_SEPARATOR)
        }

    fun decodeStudents(value: String): List<ProfessorStudent> =
        value.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val fields = line.split(FIELD_SEPARATOR)
                if (fields.size != 5) return@mapNotNull null
                runCatching {
                    ProfessorStudent(
                        id = fields[0].toLong(),
                        name = unescape(fields[1]),
                        phone = unescape(fields[2]),
                        monthlyFeeCents = fields[3].toLong(),
                        active = fields[4].toBoolean()
                    )
                }.getOrNull()
            }
            .toList()

    fun encodePayments(payments: List<ProfessorPayment>): String =
        payments.joinToString(RECORD_SEPARATOR) {
            listOf(
                it.id.toString(),
                it.studentId.toString(),
                it.referenceMonth,
                it.amountCents.toString(),
                it.status.name,
                it.paidAt?.toString() ?: "",
                escape(it.note)
            ).joinToString(FIELD_SEPARATOR)
        }

    fun decodePayments(value: String): List<ProfessorPayment> =
        value.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val fields = line.split(FIELD_SEPARATOR)
                if (fields.size != 7) return@mapNotNull null
                runCatching {
                    ProfessorPayment(
                        id = fields[0].toLong(),
                        studentId = fields[1].toLong(),
                        referenceMonth = fields[2],
                        amountCents = fields[3].toLong(),
                        status = ProfessorPaymentStatus.valueOf(fields[4]),
                        paidAt = fields[5].takeIf { it.isNotBlank() }?.toLong(),
                        note = unescape(fields[6])
                    )
                }.getOrNull()
            }
            .toList()

    private fun escape(value: String): String =
        value.replace("%", "%25")
            .replace("\t", "%09")
            .replace("\n", "%0A")

    private fun unescape(value: String): String =
        value.replace("%0A", "\n")
            .replace("%09", "\t")
            .replace("%25", "%")
}

class ProfessorRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): ProfessorData = ProfessorData(
        students = ProfessorStorageCodec.decodeStudents(prefs.getString(STUDENTS_KEY, "") ?: ""),
        payments = ProfessorStorageCodec.decodePayments(prefs.getString(PAYMENTS_KEY, "") ?: "")
    )

    fun save(data: ProfessorData) {
        prefs.edit()
            .putString(STUDENTS_KEY, ProfessorStorageCodec.encodeStudents(data.students))
            .putString(PAYMENTS_KEY, ProfessorStorageCodec.encodePayments(data.payments))
            .apply()
    }
}
