package com.creator.automation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PhysicalExecutionStabilizationTest {

    @Test
    fun testExecutionStageAndTraceModels() {
        val trace = ExecutionTrace(
            stage = ExecutionStage.TARGET_RESOLVED,
            packageName = "com.google.android.youtube",
            details = "Resolved Search Button"
        )

        assertEquals(ExecutionStage.TARGET_RESOLVED, trace.stage)
        assertEquals("com.google.android.youtube", trace.packageName)
        assertEquals("Resolved Search Button", trace.details)

        val result = AgentStepResult(
            stateBefore = AgentState.IDLE,
            observation = null,
            decisionReason = "Task complete",
            actionExecuted = null,
            actionResult = null,
            verificationStatus = VerificationStatus.SUCCESSFULLY_VERIFIED,
            nextState = AgentState.COMPLETED,
            traces = listOf(trace)
        )

        assertEquals(1, result.traces.size)
        assertEquals(ExecutionStage.TARGET_RESOLVED, result.traces.first().stage)
    }

    @Test
    fun testTimeoutSourceClassification() = runBlocking {
        val waitEngine = WaitEngine()
        val condition = WaitCondition(
            type = WaitConditionType.WAIT_FOR_PACKAGE,
            expectedValue = "com.nonexistent.package",
            timeoutMs = 300L,
            pollIntervalMs = 100L
        )

        val res = waitEngine.waitUntil(condition, service = null)
        assertFalse(res.success)
        assertNotNull(res.failureReason)
        assertTrue(res.failureReason!!.contains("Timeout waiting for WAIT_FOR_PACKAGE"))
        assertTrue(res.failureReason!!.contains("[Source=OBSERVATION]"))
    }

    @Test
    fun testSearchQueryExtractionCleanFilters() {
        val resolver = TaskResolver(
            learnedWorkflowDao = org.mockito.Mockito.mock(LearnedWorkflowDao::class.java),
            reasoningProvider = org.mockito.Mockito.mock(ReasoningProvider::class.java)
        )

        val workflow = resolver.generateGenericWorkflowForTask("Open Chrome and search for Telugu movies")
        assertNotNull(workflow)

        val typeStep = workflow!!.steps.find { it.action.type == ActionType.TYPE_TEXT }
        assertNotNull(typeStep)
        assertEquals("Telugu movies", typeStep!!.action.inputData)

        val submitStep = workflow.steps.find { it.action.type == ActionType.SUBMIT_INPUT }
        assertNotNull(submitStep)
        assertEquals("Telugu movies", submitStep!!.action.targetValue)
    }

    @Test
    fun testGoalVerificationResultEnforcesTruthfulEvidence() {
        val verifier = GoalVerifier()

        val snapshot = UiSnapshot(
            packageName = "com.google.android.youtube",
            visibleTexts = listOf("Telugu Movies 2026 Full HD")
        )

        val successRes = verifier.verifyGoal("Telugu Movies", "com.google.android.youtube", snapshot)
        assertTrue(successRes.isVerified)
        assertEquals(GoalVerificationStatus.GOAL_VERIFIED, successRes.status)

        val missingTextRes = verifier.verifyGoal("Hindi Comedy Movies", "com.google.android.youtube", snapshot)
        assertFalse(missingTextRes.isVerified)
        assertEquals(GoalVerificationStatus.GOAL_TEXT_NOT_FOUND, missingTextRes.status)

        val wrongPkgRes = verifier.verifyGoal("Telugu Movies", "com.android.chrome", snapshot)
        assertFalse(wrongPkgRes.isVerified)
        assertEquals(GoalVerificationStatus.WRONG_PACKAGE, wrongPkgRes.status)
    }
}
