package com.contai.financeiro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ProfessorScreen(
    students: List<ProfessorStudent> = emptyList(),
    payments: List<ProfessorPayment> = emptyList(),
    onAddStudent: (ProfessorStudent) -> Unit = {},
    onMarkPaid: (ProfessorPayment) -> Unit = {}
) {
    var studentName by remember { mutableStateOf("") }
    var monthlyFee by remember { mutableStateOf("") }
    val activeStudents = students.count { it.active }
    val pending = payments.count { it.status != ProfessorPaymentStatus.PAGO }
    val receivedCents = payments.filter { it.status == ProfessorPaymentStatus.PAGO }.sumOf { it.amountCents }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Professor", style = MaterialTheme.typography.headlineMedium)
        Text("Alunos, mensalidades e pagamentos em um só lugar.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProfessorMetricCard("Alunos", activeStudents.toString(), Modifier.weight(1f))
            ProfessorMetricCard("Pendentes", pending.toString(), Modifier.weight(1f))
        }
        ProfessorMetricCard("Recebido", formatProfessorCurrency(receivedCents), Modifier.fillMaxWidth())

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Novo aluno", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(studentName, { studentName = it }, Modifier.fillMaxWidth(), label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(monthlyFee, { monthlyFee = it }, Modifier.fillMaxWidth(), label = { Text("Mensalidade") }, singleLine = true)
                Button(onClick = {
                    val cents = monthlyFee.replace(".", "").replace(",", ".").toDoubleOrNull()?.let { (it * 100).toLong() }
                    if (studentName.isNotBlank() && cents != null && cents > 0) {
                        onAddStudent(ProfessorStudent(System.currentTimeMillis(), studentName.trim(), monthlyFeeCents = cents))
                        studentName = ""
                        monthlyFee = ""
                    }
                }, Modifier.fillMaxWidth()) { Text("Adicionar aluno") }
            }
        }

        Text("Alunos", style = MaterialTheme.typography.titleLarge)
        if (students.isEmpty()) {
            Text("Nenhum aluno cadastrado ainda.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            students.filter { it.active }.forEach { student ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(student.name, style = MaterialTheme.typography.titleMedium)
                        Text("Mensalidade: @@{formatProfessorCurrency(student.monthlyFeeCents)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        payments.filter { it.studentId == student.id && it.status != ProfessorPaymentStatus.PAGO }.forEach { payment ->
                            Text("Pendente • @@{payment.referenceMonth} • @@{formatProfessorCurrency(payment.amountCents)}", color = MaterialTheme.colorScheme.error)
                            Button(onClick = { onMarkPaid(payment) }, Modifier.fillMaxWidth()) { Text("Marcar como pago") }
                        }
                    }
                }
            }
        }
        Text("Próxima etapa: histórico por aluno e geração automática das mensalidades.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfessorMetricCard(title: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

private fun formatProfessorCurrency(cents: Long): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(cents / 100.0)
