package com.creator.automation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class WorkflowEngineTest {

    @Test
    fun testWorkflowExecution_NullServiceReturnsBlocked() = runTest {
        val workflow = DefaultWorkflows.youtubeStudioReadOnlyWorkflow
        val engine = WorkflowEngine(mock(android.content.Context::class.java))

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
