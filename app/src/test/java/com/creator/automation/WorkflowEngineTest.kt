package com.creator.automation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class WorkflowEngineTest {

    @Test
    fun testWorkflowExecution_NullServiceReturnsBlocked() = runTest {
        val workflow = DefaultWorkflows.youtubeStudioReadOnlyWorkflow
        val context = mock(android.content.Context::class.java)
        `when`(context.applicationContext).thenReturn(context)

        val actionResolver = mock(ActionResolver::class.java)
        val decisionResolver = mock(LearnedDecisionResolver::class.java)

        val engine = WorkflowEngine(context, actionResolver, decisionResolver)

        val result = engine.executeWorkflow(
            workflow = workflow,
            service = null,
            trigger = ExecutionTrigger.SCHEDULED
        )

        assertEquals(ActionResultStatus.BLOCKED, result.status)
        assertEquals(ExecutionReason.ACCESSIBILITY_DISABLED, result.reason)
        assertEquals(ExecutionTrigger.SCHEDULED, result.trigger)
        assertTrue(result.message?.contains("AccessibilityService is disabled") == true)
    }
}
