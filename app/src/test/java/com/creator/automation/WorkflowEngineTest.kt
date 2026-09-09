package com.creator.automation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WorkflowEngineTest {

    @Test
    fun testWorkflowExecution_NullServiceReturnsBlocked() = runTest {
        val workflow = DefaultWorkflows.youtubeStudioReadOnlyWorkflow
        val engine = WorkflowEngine(mockContext())

        val result = engine.executeWorkflow(workflow, service = null)

        assertEquals(ActionResultStatus.BLOCKED, result.status)
        assertTrue(result.message?.contains("AccessibilityService is disabled") == true)
    }

    private fun mockContext(): android.content.Context {
        return org.mockito.Mockito.mock(android.content.Context::class.java)
    }
}
