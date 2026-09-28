package com.contai.financeiro

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchdogRecoveryPolicyTest {
    @Test
    fun `primeiras tres recuperacoes usam intervalo rapido`() {
        var attempts = 0
        repeat(3) {
            val decision = WatchdogRecoveryPolicy.forStale(attempts)
            attempts = decision.attempts
            assertEquals("RECOVERY_FAST", decision.state)
            assertEquals(WATCHDOG_FAST_INTERVAL_MS, decision.nextDelayMs)
        }
        assertEquals(3, attempts)
    }

    @Test
    fun `quarta falha entra em backoff`() {
        val decision = WatchdogRecoveryPolicy.forStale(3)
        assertEquals(4, decision.attempts)
        assertEquals("RECOVERY_BACKOFF", decision.state)
        assertEquals(WATCHDOG_NORMAL_INTERVAL_MS, decision.nextDelayMs)
    }

    @Test
    fun `estado saudavel zera tentativas e volta ao intervalo normal`() {
        val decision = WatchdogRecoveryPolicy.healthy()
        assertEquals(0, decision.attempts)
        assertEquals("HEALTHY", decision.state)
        assertEquals(WATCHDOG_NORMAL_INTERVAL_MS, decision.nextDelayMs)
    }
}
