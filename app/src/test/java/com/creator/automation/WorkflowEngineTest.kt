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

        val result = engine.executeWorkflow(workflow, service = null)

        assertEquals(ActionResultStatus.BLOCKED, result.status)
        assertEquals(ExecutionReason.ACCESSIBILITY_DISABLED, result.reason)
        assertTrue(result.message?.contains("AccessibilityService is disabled") == true)
    }
}
