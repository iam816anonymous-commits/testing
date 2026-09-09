package com.creator.automation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class LearnedWorkflowEngineTest {

    @Test
    fun testExecuteLearnedWorkflow_EmptyStepsReturnsFailure() = runTest {
        val dao = mock(LearnedWorkflowDao::class.java)
        val context = mock(android.content.Context::class.java)
        `when`(context.applicationContext).thenReturn(context)

        val executor = mock(DeviceActionExecutor::class.java)
        val gate = mock(AutonomousExecutionGate::class.java)
        val service = mock(AutomationAccessibilityService::class.java)

        val engine = LearnedWorkflowEngine(context, dao, executor, gate)
        val workflow = LearnedWorkflow(name = "Empty Workflow", startingStateSignature = "sig123")

        `when`(dao.getStepsForWorkflow(workflow.id)).thenReturn(emptyList())

        val result = engine.executeLearnedWorkflow(workflow, service)

        assertEquals(ActionResultStatus.FAILED, result.status)
        assertEquals(ExecutionReason.UI_NOT_FOUND, result.reason)
        assertTrue(result.message?.contains("no steps") == true)
    }

    @Test
    fun testExecuteLearnedWorkflow_StateMismatchPausesWorkflow() = runTest {
        val dao = mock(LearnedWorkflowDao::class.java)
        val context = mock(android.content.Context::class.java)
        `when`(context.applicationContext).thenReturn(context)

        val executor = mock(DeviceActionExecutor::class.java)
        val gate = mock(AutonomousExecutionGate::class.java)
        val service = mock(AutomationAccessibilityService::class.java)

        val engine = LearnedWorkflowEngine(context, dao, executor, gate)
        val workflow = LearnedWorkflow(name = "Mismatch Workflow", startingStateSignature = "sig123")

        val step = LearnedWorkflowStep(
            workflowId = workflow.id,
            sequenceNumber = 1,
            beforeStateSignature = "expectedSig999",
            actionType = "CLICK_TEXT",
            targetIdentifier = "Analytics"
        )

        `when`(dao.getStepsForWorkflow(workflow.id)).thenReturn(listOf(step))

        val result = engine.executeLearnedWorkflow(workflow, service)

        assertEquals(ActionResultStatus.BLOCKED, result.status)
        assertEquals(ExecutionReason.VERIFICATION_FAILED, result.reason)
        assertTrue(result.message?.contains("state signature mismatch") == true)
    }
}
