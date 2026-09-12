package com.creator.automation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RuntimeMechanismTruthTest {

    @Test
    fun testExecutionStageAndTraceCollection() {
        val trace1 = ExecutionTrace(
            stage = ExecutionStage.OBSERVATION_STARTED,
            packageName = "com.google.android.youtube",
            details = "Capturing UI snapshot"
        )
        val trace2 = ExecutionTrace(
            stage = ExecutionStage.GOAL_VERIFIED,
            packageName = "com.google.android.youtube",
            details = "Verified search results"
        )

        val result = AgentStepResult(
            stateBefore = AgentState.IDLE,
            observation = null,
            decisionReason = "Task complete",
            actionExecuted = null,
            actionResult = null,
            verificationStatus = VerificationStatus.SUCCESSFULLY_VERIFIED,
            nextState = AgentState.COMPLETED,
            traces = listOf(trace1, trace2)
        )

        assertEquals(2, result.traces.size)
        assertEquals(ExecutionStage.OBSERVATION_STARTED, result.traces[0].stage)
        assertEquals(ExecutionStage.GOAL_VERIFIED, result.traces[1].stage)
    }

    @Test
    fun testHardwareGoalMatchingExcludesObservationCommands() {
        assertNull("Read screen should NOT match DisplayActuator", HardwareActuatorRegistry.findActuatorForGoal("Read screen"))
        assertNull("Capture screen should NOT match DisplayActuator", HardwareActuatorRegistry.findActuatorForGoal("Capture screen"))
        assertNull("Read visible UI should NOT match DisplayActuator", HardwareActuatorRegistry.findActuatorForGoal("Read visible UI"))

        val wakeActuator = HardwareActuatorRegistry.findActuatorForGoal("wake display")
        assertNotNull("Wake display SHOULD match DisplayActuator", wakeActuator)
        assertEquals(HardwareCapabilityType.DISPLAY, wakeActuator?.type)

        val torchActuator = HardwareActuatorRegistry.findActuatorForGoal("turn on flashlight")
        assertNotNull("Turn on flashlight SHOULD match FlashlightActuator", torchActuator)
        assertEquals(HardwareCapabilityType.FLASHLIGHT, torchActuator?.type)
    }

    @Test
    fun testFalseSuccessPreventionInvariant() {
        val verifier = GoalVerifier()

        val snapshot = UiSnapshot(
            packageName = "com.google.android.youtube",
            visibleTexts = listOf("Telugu Movies 2026")
        )

        val successRes = verifier.verifyGoal("Telugu Movies", "com.google.android.youtube", snapshot)
        assertTrue("Goal should be verified when expected text is present on fresh snapshot", successRes.isVerified)

        val missingTextRes = verifier.verifyGoal("NonExistentMovieQueryXYZ", "com.google.android.youtube", snapshot)
        assertFalse("Goal must NOT be confirmed when text is missing from snapshot", missingTextRes.isVerified)

        val wrongPkgRes = verifier.verifyGoal("Telugu Movies", "com.android.chrome", snapshot)
        assertFalse("Goal must NOT be confirmed when active package differs from expected", wrongPkgRes.isVerified)
    }

    @Test
    fun testTimeoutSourceClassificationInWaitEngine() = runBlocking {
        val waitEngine = WaitEngine()

        val pkgCondition = WaitCondition(
            type = WaitConditionType.WAIT_FOR_PACKAGE,
            expectedValue = "com.nonexistent.package",
            timeoutMs = 200L,
            pollIntervalMs = 50L
        )

        val res = waitEngine.waitUntil(pkgCondition, service = null)
        assertFalse(res.success)
        assertNotNull(res.failureReason)
        assertTrue(res.failureReason!!.contains("[Source=OBSERVATION]"))
    }
}
