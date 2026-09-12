package com.creator.automation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class TaskResolverTest {

    @Test
    fun testResolveTask_LearnedWorkflowMatch() = runTest {
        val learnedDao = mock(LearnedWorkflowDao::class.java)
        val provider = mock(ReasoningProvider::class.java)
        val resolver = TaskResolver(learnedDao, provider)

        val snapshot = UiSnapshot(
            packageName = "com.google.android.apps.youtube.creator",
            visibleTexts = listOf("Channel Dashboard", "Analytics")
        )
        val stateSig = StateSignatureGenerator.generateSignature(snapshot)

        val learnedWf = LearnedWorkflow(
            name = "Learned Studio Workflow",
            startingStateSignature = stateSig
        )

        `when`(learnedDao.getWorkflowsForStartingState(stateSig)).thenReturn(listOf(learnedWf))

        val resolution = resolver.resolveTask("Open analytics", snapshot)

        assertEquals(TaskSource.LEARNED_WORKFLOW, resolution.source)
        assertEquals(ResolutionReason.LEARNED_WORKFLOW_MATCH, resolution.resolutionReason)
        assertEquals("Learned Studio Workflow", resolution.learnedWorkflow?.name)
    }

    @Test
    fun testResolveTask_LocalWorkflowMatch() = runTest {
        val learnedDao = mock(LearnedWorkflowDao::class.java)
        val provider = mock(ReasoningProvider::class.java)
        val resolver = TaskResolver(learnedDao, provider)

        val resolution = resolver.resolveTask("YouTube Studio")

        assertEquals(TaskSource.LOCAL_RULE, resolution.source)
        assertEquals(ResolutionReason.LOCAL_WORKFLOW_MATCH, resolution.resolutionReason)
        assertNotNull(resolution.localWorkflow)
    }

    @Test
    fun testResolveTask_UnseenAppTask_GeneratesGenericWorkflow() = runTest {
        val learnedDao = mock(LearnedWorkflowDao::class.java)
        val provider = mock(ReasoningProvider::class.java)
        val resolver = TaskResolver(learnedDao, provider)

        val resolution = resolver.resolveTask("Open Chrome and search for new Telugu movies")

        assertEquals(TaskSource.LOCAL_RULE, resolution.source)
        assertEquals(ResolutionReason.LOCAL_WORKFLOW_MATCH, resolution.resolutionReason)
        assertNotNull(resolution.localWorkflow)

        val wf = resolution.localWorkflow!!
        assertTrue(wf.steps.any { it.action.type == ActionType.LAUNCH_APP && it.action.targetValue == "Chrome" })
        assertTrue(wf.steps.any { it.action.type == ActionType.TYPE_TEXT && it.action.inputData == "new Telugu movies" })
        assertTrue(wf.steps.any { it.action.type == ActionType.SUBMIT_INPUT })
    }

    @Test
    fun testResolveTask_ChatGPTFallback() = runTest {
        val learnedDao = mock(LearnedWorkflowDao::class.java)
        val provider = mock(ReasoningProvider::class.java)
        val resolver = TaskResolver(learnedDao, provider)

        `when`(provider.getProviderName()).thenReturn("ChatGPT")

        val plan = ReasoningPlan(
            taskId = "plan123",
            taskDescription = "Unknown Custom Task",
            steps = listOf(ReasoningStep(1, "READ_VISIBLE_UI", null)),
            isValid = true
        )

        `when`(provider.requestReasoning(org.mockito.kotlin.any())).thenReturn(plan)

        val resolution = resolver.resolveTask("Unknown Custom Task")

        assertEquals(TaskSource.CHATGPT, resolution.source)
        assertEquals(ResolutionReason.CHATGPT_FALLBACK, resolution.resolutionReason)
        assertNotNull(resolution.reasoningPlan)
    }
}
