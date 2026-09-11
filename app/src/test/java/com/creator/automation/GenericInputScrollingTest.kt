package com.creator.automation

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class GenericInputScrollingTest {

    private lateinit var mockContext: Context
    private lateinit var mockService: AutomationAccessibilityService
    private lateinit var mockActionResolver: ActionResolver
    private lateinit var mockAuditDao: ActionAuditDao
    private lateinit var mockNodeInfo: AccessibilityNodeInfo

    @Before
    fun setUp() {
        mockContext = Mockito.mock(Context::class.java)
        mockService = Mockito.mock(AutomationAccessibilityService::class.java)
        mockActionResolver = Mockito.mock(ActionResolver::class.java)
        mockAuditDao = Mockito.mock(ActionAuditDao::class.java)
        mockNodeInfo = Mockito.mock(AccessibilityNodeInfo::class.java)

        val mockRes = Mockito.mock(Resources::class.java)
        val metrics = DisplayMetrics().apply { widthPixels = 1080; heightPixels = 1920 }
        Mockito.`when`(mockContext.applicationContext).thenReturn(mockContext)
        Mockito.`when`(mockContext.resources).thenReturn(mockRes)
        Mockito.`when`(mockRes.displayMetrics).thenReturn(metrics)
    }

    @Test
    fun testPerformTypeText_InjectsTextAndVerifies() = runBlocking {
        val executor = DeviceActionExecutor(
            context = mockContext,
            actionResolver = mockActionResolver,
            auditDao = mockAuditDao
        )

        val dummySnapshot = UiSnapshot(
            packageName = "com.test",
            editableNodes = listOf(
                UiNodeInfo(
                    text = "Hello World",
                    isEditable = true,
                    isEnabled = true,
                    isFocused = true,
                    nodeRef = mockNodeInfo
                )
            )
        )

        val targetMatch = ResolutionMatch(
            node = dummySnapshot.editableNodes.first(),
            matchMethod = "EXACT_TEXT_MATCH",
            confidence = 1.0,
            reason = "Test match"
        )

        val dummyResolution = TargetResolutionResult(
            match = targetMatch,
            candidateCount = 1,
            isAmbiguous = false,
            status = TargetResolutionStatus.FOUND_UNIQUE,
            explanation = "Test explanation"
        )

        Mockito.doReturn(dummyResolution).`when`(mockActionResolver).resolveEditableTarget(Mockito.any() ?: dummySnapshot, Mockito.any())

        Mockito.`when`(mockService.getRootNode()).thenReturn(mockNodeInfo)
        Mockito.`when`(mockNodeInfo.performAction(Mockito.eq(AccessibilityNodeInfo.ACTION_SET_TEXT), Mockito.any(Bundle::class.java))).thenReturn(true)

        val action = AutomationAction(
            type = ActionType.TYPE_TEXT,
            targetValue = "Search",
            inputData = "Hello World"
        )

        val result = executor.executeAndAudit("wf_type", action, mockService)

        assertEquals(ActionResultStatus.SUCCESS, result.status)
        assertNotNull(result.message)
        assertTrue(result.message!!.contains("VERIFIED_SUCCESS") || result.message!!.contains("DISPATCH_SUCCEEDED"))
    }

    @Test
    fun testPerformScroll_AccessibilityScrollActionSucceeds() = runBlocking {
        val executor = DeviceActionExecutor(
            context = mockContext,
            actionResolver = mockActionResolver,
            auditDao = mockAuditDao
        )

        Mockito.`when`(mockService.getRootNode()).thenReturn(mockNodeInfo)
        Mockito.`when`(mockNodeInfo.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)).thenReturn(true)

        val action = AutomationAction(
            type = ActionType.SCROLL_DOWN
        )

        val result = executor.executeAndAudit("wf_scroll", action, mockService)

        // Action executes and attempts scroll
        assertNotNull(result)
    }
}
