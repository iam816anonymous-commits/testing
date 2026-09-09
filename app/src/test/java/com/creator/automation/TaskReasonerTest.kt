package com.creator.automation

import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class TaskReasonerTest {

    @Test
    fun testValidatePlan_ValidPlanApproved() {
        val context = mock(android.content.Context::class.java)
        val executor = mock(DeviceActionExecutor::class.java)
        val learnedDao = mock(LearnedWorkflowDao::class.java)
        val reasoner = TaskReasoner(context, executor, learnedDao)

        val validPlan = ReasoningPlan(
            taskId = "task123",
            taskDescription = "Find Analytics",
            steps = listOf(
                ReasoningStep(1, "CLICK_TEXT", "Analytics"),
                ReasoningStep(2, "CAPTURE_SCREEN", null)
            )
        )

        val validation = reasoner.validatePlan(validPlan)
        assertTrue(validation.isValid)
        assertNull(validation.rejectionReason)
    }

    @Test
    fun testValidatePlan_ProhibitedKeywordRejectsPlan() {
        val context = mock(android.content.Context::class.java)
        val executor = mock(DeviceActionExecutor::class.java)
        val learnedDao = mock(LearnedWorkflowDao::class.java)
        val reasoner = TaskReasoner(context, executor, learnedDao)

        val invalidPlan = ReasoningPlan(
            taskId = "task123",
            taskDescription = "Enter Password",
            steps = listOf(
                ReasoningStep(1, "CLICK_TEXT", "Type your password here")
            )
        )

        val validation = reasoner.validatePlan(invalidPlan)
        assertFalse(validation.isValid)
        assertTrue(validation.rejectionReason?.contains("prohibited sensitive keyword") == true)
    }

    @Test
    fun testValidatePlan_UnknownActionTypeRejectsPlan() {
        val context = mock(android.content.Context::class.java)
        val executor = mock(DeviceActionExecutor::class.java)
        val learnedDao = mock(LearnedWorkflowDao::class.java)
        val reasoner = TaskReasoner(context, executor, learnedDao)

        val invalidPlan = ReasoningPlan(
            taskId = "task123",
            taskDescription = "Magic Action",
            steps = listOf(
                ReasoningStep(1, "MAGIC_CLICK", "Target")
            )
        )

        val validation = reasoner.validatePlan(invalidPlan)
        assertFalse(validation.isValid)
        assertTrue(validation.rejectionReason?.contains("unknown action type") == true)
    }
}
