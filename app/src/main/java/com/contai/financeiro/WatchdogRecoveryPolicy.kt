package com.contai.financeiro

internal const val WATCHDOG_NORMAL_INTERVAL_MS = 5 * 60 * 1000L
internal const val WATCHDOG_FAST_INTERVAL_MS = 60 * 1000L
internal const val WATCHDOG_FAST_RECOVERY_LIMIT = 3

internal data class WatchdogRecoveryDecision(
    val attempts: Int,
    val state: String,
    val nextDelayMs: Long
)

internal object WatchdogRecoveryPolicy {
    fun isStale(now: Long, lastAliveAt: Long, staleAfterMs: Long): Boolean =
        lastAliveAt <= 0L || lastAliveAt > now || now - lastAliveAt > staleAfterMs

    fun forStale(previousAttempts: Int): WatchdogRecoveryDecision {
        val attempts = previousAttempts.coerceAtLeast(0) + 1
        val fast = attempts <= WATCHDOG_FAST_RECOVERY_LIMIT
        return WatchdogRecoveryDecision(
            attempts = attempts,
            state = if (fast) "RECOVERY_FAST" else "RECOVERY_BACKOFF",
            nextDelayMs = if (fast) WATCHDOG_FAST_INTERVAL_MS else WATCHDOG_NORMAL_INTERVAL_MS
        )
    }

    fun healthy(): WatchdogRecoveryDecision = WatchdogRecoveryDecision(
        attempts = 0,
        state = "HEALTHY",
        nextDelayMs = WATCHDOG_NORMAL_INTERVAL_MS
    )
}
