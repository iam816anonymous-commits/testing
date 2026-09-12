package com.creator.automation

import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class WaitEngineTest {

    private lateinit var mockService: AutomationAccessibilityService
    private lateinit var waitEngine: WaitEngine

    @Before
    fun setUp() {
        mockService = Mockito.mock(AutomationAccessibilityService::class.java)
        waitEngine = WaitEngine()
    }

    @Test
    fun testWaitEngine_RejectsEmptyPackageWaitImmediately() = runBlocking {
        val emptyPackageCondition = WaitCondition(
            type = WaitConditionType.WAIT_FOR_PACKAGE,
            expectedValue = "",
            timeoutMs = 10000L
        )

        val result = waitEngine.waitUntil(emptyPackageCondition, mockService)

        assertFalse("Blank WAIT_FOR_PACKAGE must be rejected immediately", result.success)
        assertEquals(0L, result.durationMs)
        assertTrue(result.failureReason!!.contains("INVALID_WAIT_CONDITION"))
    }

    @Test
    fun testWaitEngine_SatisfiesValidPackageWait() = runBlocking {
        val validCondition = WaitCondition(
            type = WaitConditionType.WAIT_FOR_PACKAGE,
            expectedValue = "com.google.android.youtube",
            timeoutMs = 2000L
        )

        val mockNode = Mockito.mock(AccessibilityNodeInfo::class.java)
        Mockito.`when`(mockService.getRootNode()).thenReturn(mockNode)
        Mockito.`when`(mockNode.packageName).thenReturn("com.google.android.youtube")
        Mockito.`when`(mockService.packageName).thenReturn("com.google.android.youtube")

        val result = waitEngine.waitUntil(validCondition, mockService)

        assertTrue("Valid package match should satisfy wait condition", result.success)
        assertEquals("com.google.android.youtube", result.matchedValue)
    }

    @Test
    fun testTaskResolver_PureTypingTaskGeneratesInputWorkflowWithoutPackageWait() = runBlocking {
        val mockDao = Mockito.mock(LearnedWorkflowDao::class.java)
        val resolver = TaskResolver(learnedWorkflowDao = mockDao)

        val res = resolver.resolveTask("Type hello world")

        assertEquals(TaskSource.LOCAL_RULE, res.source)
        val steps = res.localWorkflow?.steps ?: emptyList()
        assertTrue(steps.isNotEmpty())
        assertEquals(ActionType.TYPE_TEXT, steps.first().action.type)
        assertEquals("hello world", steps.first().action.inputData)
        // Verify no WAIT_FOR_PACKAGE condition exists in typing task
        assertTrue(steps.none { it.action.waitCondition?.type == WaitConditionType.WAIT_FOR_PACKAGE })
    }
}
