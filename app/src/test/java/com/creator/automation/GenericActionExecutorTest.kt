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
}
