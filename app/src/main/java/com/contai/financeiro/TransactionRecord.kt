package com.contai.financeiro

data class TransactionRecord(
    val amount: Double?,
    val type: String,
    val timestamp: Long,
    val category: String,
    val status: String,
    val source: String,
    val title: String,
    val text: String,
    val confidence: Int,
    val investmentType: String = "",
    val movementTimestamp: Long = timestamp
) {
    val effectiveMovementTimestamp: Long
        get() = movementTimestamp.takeIf { it > 0L } ?: timestamp
}
