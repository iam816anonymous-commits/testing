package com.creator.automation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WaitEngineTest {

    private lateinit var waitEngine: WaitEngine

    @Before
    fun setUp() {
        waitEngine = WaitEngine()
    }

    @Test
    fun testWaitForTextConditionMet() = runBlocking {
        val condition = WaitCondition(
            type = WaitConditionType.WAIT_FOR_TEXT,
            expectedValue = "Dashboard",
            timeoutMs = 1000L,
            pollIntervalMs = 100L
        )

        // Null service scenario yields timeout cleanly
        val result = waitEngine.waitUntil(
            condition = condition,
            service = null
        )

        assertFalse(result.success)
        assertTrue(result.durationMs <= 1500L)
        assertNotNull(result.failureReason)
    }

    @Test
    fun testSameScreenProgressConditionType() {
        val condition = WaitCondition(
            type = WaitConditionType.EXPECTED_SAME_SCREEN_PROGRESS,
            expectedValue = "com.example.app",
            timeoutMs = 500L
        )

        assertEquals(WaitConditionType.EXPECTED_SAME_SCREEN_PROGRESS, condition.type)
        assertEquals(500L, condition.timeoutMs)
    }

    @Test
    fun testNodeDisappearsConditionType() {
        val condition = WaitCondition(
            type = WaitConditionType.NODE_DISAPPEARS,
            expectedValue = "Loading...",
            timeoutMs = 2000L
        )

        assertEquals(WaitConditionType.NODE_DISAPPEARS, condition.type)
        assertEquals("Loading...", condition.expectedValue)
    }

    @Test
    fun testTargetBecomesEnabledConditionType() {
        val condition = WaitCondition(
            type = WaitConditionType.TARGET_BECOMES_ENABLED,
            expectedValue = "SubmitButton",
            timeoutMs = 1500L
        )

        assertEquals(WaitConditionType.TARGET_BECOMES_ENABLED, condition.type)
    }

    @Test
    fun testStateChangeConditionFailureWithNullSignatures() = runBlocking {
        val condition = WaitCondition(
            type = WaitConditionType.WAIT_FOR_STATE_CHANGE,
            timeoutMs = 300L,
            pollIntervalMs = 50L
        )

        val res = waitEngine.waitUntil(
            condition = condition,
            service = null,
            initialSignature = null
        )

        assertFalse(res.success)
    }
}
