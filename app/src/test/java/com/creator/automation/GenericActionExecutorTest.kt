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
        mockResolver = Mockito.mock(ActionResolver::class.java)
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
}
