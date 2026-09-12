package com.creator.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class AndroidAutomationCompatTest {

    private lateinit var mockService: AutomationAccessibilityService
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockService = Mockito.mock(AutomationAccessibilityService::class.java)
        mockContext = Mockito.mock(Context::class.java)
        Mockito.`when`(mockContext.applicationContext).thenReturn(mockContext)
    }

    @Test
    fun testGlobalHomeSupportedOnApi27() {
        Mockito.`when`(mockService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)).thenReturn(true)

        val result = AndroidAutomationCompat.performGlobalHome(mockService)

        assertTrue("Global HOME must be supported and return true on API 27", result)
        Mockito.verify(mockService).performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    @Test
    fun testGlobalBackSupportedOnApi27() {
        Mockito.`when`(mockService.performGoBack()).thenReturn(true)

        val result = AndroidAutomationCompat.performGlobalBack(mockService)

        assertTrue("Global BACK must be supported and return true on API 27", result)
        Mockito.verify(mockService).performGoBack()
    }

    @Test
    fun testRecentsSupportedOnApi27() {
        Mockito.`when`(mockService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)).thenReturn(true)

        val result = AndroidAutomationCompat.performGlobalRecents(mockService)

        assertTrue("Global RECENTS must be supported and return true on API 27", result)
        Mockito.verify(mockService).performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    }

    @Test
    fun testCaptureScreenshotCompat_OnApi27_FallsBackToPerceptionSnapshot() = runBlocking {
        val result = AndroidAutomationCompat.captureScreenshotCompat(mockService, mockContext, null)

        assertEquals("Screenshot on API 27 should fall back to SUCCESS status with perception snapshot", ActionResultStatus.SUCCESS, result.status)
        assertNotNull(result.message)
        assertTrue(result.message!!.contains("fallback"))
    }

    @Test
    fun testTaskResolver_SystemCommandsResolvedLocallyOnApi27() = runBlocking {
        val mockDao = Mockito.mock(LearnedWorkflowDao::class.java)
        val resolver = TaskResolver(learnedWorkflowDao = mockDao)

        val homeRes = resolver.resolveTask("Go home")
        assertEquals(TaskSource.LOCAL_RULE, homeRes.source)
        assertEquals(ActionType.PRESS_HOME, homeRes.localWorkflow?.steps?.first()?.action?.type)

        val backRes = resolver.resolveTask("Go back")
        assertEquals(TaskSource.LOCAL_RULE, backRes.source)
        assertEquals(ActionType.GO_BACK, backRes.localWorkflow?.steps?.first()?.action?.type)

        val recentsRes = resolver.resolveTask("Recents")
        assertEquals(TaskSource.LOCAL_RULE, recentsRes.source)
        assertEquals(ActionType.PRESS_RECENTS, recentsRes.localWorkflow?.steps?.first()?.action?.type)

        val readRes = resolver.resolveTask("Read visible screen")
        assertEquals(TaskSource.LOCAL_RULE, readRes.source)
        assertEquals(ActionType.READ_VISIBLE_UI, readRes.localWorkflow?.steps?.first()?.action?.type)
    }
}
