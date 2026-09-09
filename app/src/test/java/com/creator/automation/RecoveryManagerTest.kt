package com.creator.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryManagerTest {

    @Test
    fun testEvaluateRecovery_NullActionResult_ReturnsPause() {
        val manager = RecoveryManager(maxRetriesPerStep = 1)
        val outcome = manager.evaluateRecovery(failedStepCount = 0, lastActionResult = null, currentTask = null)
        assertEquals(RecoveryOutcome.PAUSE, outcome)
    }

    @Test
    fun testEvaluateRecovery_AccessibilityDisabled_ReturnsPause() {
        val manager = RecoveryManager(maxRetriesPerStep = 1)
        val result = ActionResult(
            status = ActionResultStatus.BLOCKED,
            reason = ExecutionReason.ACCESSIBILITY_DISABLED,
            message = "Service off"
        )
        val outcome = manager.evaluateRecovery(failedStepCount = 0, lastActionResult = result, currentTask = null)
        assertEquals(RecoveryOutcome.PAUSE, outcome)
    }

    @Test
    fun testEvaluateRecovery_LoginRequired_ReturnsPause() {
        val manager = RecoveryManager(maxRetriesPerStep = 1)
        val result = ActionResult(
            status = ActionResultStatus.BLOCKED,
            reason = ExecutionReason.LOGIN_REQUIRED,
            message = "Login screen detected"
        )
        val outcome = manager.evaluateRecovery(failedStepCount = 0, lastActionResult = result, currentTask = null)
        assertEquals(RecoveryOutcome.PAUSE, outcome)
    }

    @Test
    fun testEvaluateRecovery_AmbiguousTarget_ReturnsPause() {
        val manager = RecoveryManager(maxRetriesPerStep = 1)
        val result = ActionResult(
            status = ActionResultStatus.BLOCKED,
            reason = ExecutionReason.AMBIGUOUS_TARGET,
            message = "Target ambiguous"
        )
        val outcome = manager.evaluateRecovery(failedStepCount = 0, lastActionResult = result, currentTask = null)
        assertEquals(RecoveryOutcome.PAUSE, outcome)
    }

    @Test
    fun testEvaluateRecovery_NonIdempotentFailure_ReturnsPause() {
        val manager = RecoveryManager(maxRetriesPerStep = 1)
        val result = ActionResult(
            status = ActionResultStatus.FAILED,
            reason = ExecutionReason.VERIFICATION_FAILED,
            message = "Submit button verification failed"
        )
        val outcome = manager.evaluateRecovery(
            failedStepCount = 0,
            lastActionResult = result,
            actionSemantics = ActionSemantics.NON_IDEMPOTENT
        )
        assertEquals(RecoveryOutcome.PAUSE, outcome)
    }

    @Test
    fun testEvaluateRecovery_HighRiskFailure_ReturnsPause() {
        val manager = RecoveryManager(maxRetriesPerStep = 1)
        val result = ActionResult(
            status = ActionResultStatus.FAILED,
            reason = ExecutionReason.NONE,
            message = "High risk action failed"
        )
        val outcome = manager.evaluateRecovery(
            failedStepCount = 0,
            lastActionResult = result,
            actionSemantics = ActionSemantics.HIGH_RISK
        )
        assertEquals(RecoveryOutcome.PAUSE, outcome)
    }

    @Test
    fun testEvaluateRecovery_FailureUnderMaxRetries_ReturnsRetry() {
        val manager = RecoveryManager(maxRetriesPerStep = 2)
        val result = ActionResult(
            status = ActionResultStatus.FAILED,
            reason = ExecutionReason.UI_NOT_FOUND,
            message = "Element missing"
        )
        val outcome = manager.evaluateRecovery(failedStepCount = 1, lastActionResult = result, currentTask = null)
        assertEquals(RecoveryOutcome.RETRY, outcome)
    }

    @Test
    fun testEvaluateRecovery_FailureReachingMaxRetries_ReturnsFail() {
        val manager = RecoveryManager(maxRetriesPerStep = 1)
        val result = ActionResult(
            status = ActionResultStatus.FAILED,
            reason = ExecutionReason.UI_NOT_FOUND,
            message = "Element missing"
        )
        val outcome = manager.evaluateRecovery(failedStepCount = 1, lastActionResult = result, currentTask = null)
        assertEquals(RecoveryOutcome.FAIL, outcome)
    }
}
