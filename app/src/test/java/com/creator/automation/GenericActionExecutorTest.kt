package com.creator.automation

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class GenericActionExecutorTest {

    private lateinit var mockContext: Context
    private lateinit var mockResolver: ActionResolver
    private lateinit var mockAuditDao: ActionAuditDao

    @Before
    fun setUp() {
        mockContext = Mockito.mock(Context::class.java)
        mockResolver = ActionResolver()
        mockAuditDao = Mockito.mock(ActionAuditDao::class.java)
    }

    @Test
    fun testActionTypeEnum_ContainsEngine21GenericActions() {
        assertNotNull(ActionType.valueOf("LAUNCH_APP"))
        assertNotNull(ActionType.valueOf("TYPE_TEXT"))
        assertNotNull(ActionType.valueOf("CLEAR_TEXT"))
        assertNotNull(ActionType.valueOf("PRESS_ENTER"))
        assertNotNull(ActionType.valueOf("SUBMIT_INPUT"))
        assertNotNull(ActionType.valueOf("PRESS_HOME"))
        assertNotNull(ActionType.valueOf("PRESS_RECENTS"))
        assertNotNull(ActionType.valueOf("SCROLL_UP"))
        assertNotNull(ActionType.valueOf("SCROLL_DOWN"))
        assertNotNull(ActionType.valueOf("LONG_CLICK"))
        assertNotNull(ActionType.valueOf("REFRESH_OBSERVATION"))
    }

    @Test
    fun testExecutionReason_ContainsAppNotInstalledAndAmbiguousApp() {
        assertNotNull(ExecutionReason.valueOf("APP_NOT_INSTALLED"))
        assertNotNull(ExecutionReason.valueOf("AMBIGUOUS_APPLICATION"))
        assertNotNull(ExecutionReason.valueOf("UNSUPPORTED_SUBMISSION_MECHANISM"))
    }

    @Test
    fun testMockSearchApp_ResolveSearchControl_SingleUniqueMatch() {
        val mockSearchAppSnapshot = UiSnapshot(
            packageName = "com.mock.searchapp",
            allNodes = listOf(
                UiNodeInfo(text = "Search Query Field", isEditable = true, isFocused = true),
                UiNodeInfo(text = "Search", isClickable = true, isEnabled = true)
            )
        )

        val res = mockResolver.resolveTargetWithAmbiguity(mockSearchAppSnapshot, "Search")

        assertNotNull(res.match)
        assertEquals(1, res.candidateCount)
        assertEquals(TargetResolutionStatus.FOUND_UNIQUE, res.status)
    }

    @Test
    fun testMockSearchApp_MultipleSearchControls_AmbiguousTargetBlocked() {
        val mockAmbiguousSnapshot = UiSnapshot(
            packageName = "com.mock.searchapp",
            allNodes = listOf(
                UiNodeInfo(text = "Search Query Field", isEditable = true),
                UiNodeInfo(text = "Search", isClickable = true),
                UiNodeInfo(text = "Search", isClickable = true)
            )
        )

        val res = mockResolver.resolveTargetWithAmbiguity(mockAmbiguousSnapshot, "Search")

        assertTrue(res.isAmbiguous)
        assertEquals(2, res.candidateCount)
        assertEquals(TargetResolutionStatus.AMBIGUOUS, res.status)
    }

    @Test
    fun testMockFormApp_MissingSubmitControl_NotFoundStatus() {
        val mockFormSnapshot = UiSnapshot(
            packageName = "com.mock.formapp",
            allNodes = listOf(
                UiNodeInfo(text = "Name Field", isEditable = true)
            )
        )

        val res = mockResolver.resolveTargetWithAmbiguity(mockFormSnapshot, "Submit")

        assertEquals(TargetResolutionStatus.NOT_FOUND, res.status)
        assertEquals(0, res.candidateCount)
    }

    @Test
    fun testAccessibilityObservationProvider_DynamicServiceProvider() {
        var mockService: AutomationAccessibilityService? = null
        val provider = AccessibilityObservationProvider(serviceProvider = { mockService })

        // When service is null, return acc_disabled summary
        kotlinx.coroutines.runBlocking {
            val obsDisabled = provider.captureObservation()
            assertEquals("acc_disabled", obsDisabled.stateSignature)
            assertTrue(obsDisabled.summary.contains("connected = false"))
        }
    }

    @Test
    fun testTargetResolution_DisabledTarget_NotActionable() {
        val disabledSnapshot = UiSnapshot(
            packageName = "com.mock.settings",
            allNodes = listOf(
                UiNodeInfo(text = "Network & internet", isEnabled = false, isClickable = true)
            )
        )

        val res = mockResolver.resolveTargetWithAmbiguity(disabledSnapshot, "Network & internet")

        assertNotNull(res.match)
        assertEquals(TargetResolutionStatus.NOT_ACTIONABLE, res.status)
    }

    @Test
    fun testTargetBounds_CenterXCenterYWidthHeight() {
        val bounds = TargetBounds(left = 100, top = 200, right = 500, bottom = 400)

        assertEquals(300, bounds.centerX)
        assertEquals(300, bounds.centerY)
        assertEquals(400, bounds.width)
        assertEquals(200, bounds.height)

        val visState = AutomationVisualizationState(
            actionState = VisualizationActionState.CLICKING,
            targetText = "Network & internet",
            targetBounds = bounds
        )

        assertEquals(300, visState.cursorX)
        assertEquals(300, visState.cursorY)
        assertEquals(VisualizationActionState.CLICKING, visState.actionState)
    }
}
