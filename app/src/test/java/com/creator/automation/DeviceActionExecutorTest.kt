package com.creator.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceActionExecutorTest {

    @Test
    fun testRedactSensitiveText_RedactsPasswordsAndTokens() {
        assertEquals("[REDACTED]", DeviceActionExecutor.redactSensitiveText("mySecretPassword123"))
        assertEquals("[REDACTED]", DeviceActionExecutor.redactSensitiveText("auth_token_xyz"))
        assertEquals("[REDACTED]", DeviceActionExecutor.redactSensitiveText("user_pin_4321"))
        assertEquals("Analytics", DeviceActionExecutor.redactSensitiveText("Analytics"))
        assertNull(DeviceActionExecutor.redactSensitiveText(null))
    }

    @Test
    fun testCompareStates() {
        val context = org.mockito.Mockito.mock(android.content.Context::class.java)
        val resolver = org.mockito.Mockito.mock(ActionResolver::class.java)
        val auditDao = org.mockito.Mockito.mock(ActionAuditDao::class.java)
        val executor = DeviceActionExecutor(context, resolver, auditDao)

        assertEquals(StateChangeResult.NO_CHANGE, executor.compareStates("sigA", "sigA", false))
        assertEquals(StateChangeResult.STATE_CHANGED, executor.compareStates("sigA", "sigB", false))
        assertEquals(StateChangeResult.EXPECTED_STATE_REACHED, executor.compareStates("sigA", "sigB", true))
    }

    @Test
    fun testCheckPreconditions_PackageMatch_Success() {
        val context = org.mockito.Mockito.mock(android.content.Context::class.java)
        val resolver = ActionResolver()
        val auditDao = org.mockito.Mockito.mock(ActionAuditDao::class.java)
        val executor = DeviceActionExecutor(context, resolver, auditDao)

        val snapshot = UiSnapshot(
            packageName = "com.google.android.apps.youtube.creator",
            visibleTexts = listOf("Dashboard", "Analytics")
        )

        val preconditions = listOf(
            ActionPrecondition(PreconditionType.PACKAGE_MATCH, "com.google.android.apps.youtube.creator"),
            ActionPrecondition(PreconditionType.TEXT_PRESENT, "Dashboard")
        )

        val result = executor.checkPreconditions(preconditions, snapshot)
        assertTrue(result.success)
        assertNull(result.failureReason)
    }

    @Test
    fun testCheckPreconditions_PackageMismatch_Failure() {
        val context = org.mockito.Mockito.mock(android.content.Context::class.java)
        val resolver = ActionResolver()
        val auditDao = org.mockito.Mockito.mock(ActionAuditDao::class.java)
        val executor = DeviceActionExecutor(context, resolver, auditDao)

        val snapshot = UiSnapshot(
            packageName = "com.android.settings",
            visibleTexts = listOf("Settings")
        )

        val preconditions = listOf(
            ActionPrecondition(PreconditionType.PACKAGE_MATCH, "com.google.android.apps.youtube.creator")
        )

        val result = executor.checkPreconditions(preconditions, snapshot)
        assertFalse(result.success)
        assertTrue(result.failureReason?.contains("PACKAGE_MATCH") == true)
    }

    @Test
    fun testPreconditionEditablePresent_Success() {
        val context = org.mockito.Mockito.mock(android.content.Context::class.java)
        val resolver = ActionResolver()
        val auditDao = org.mockito.Mockito.mock(ActionAuditDao::class.java)
        val executor = DeviceActionExecutor(context, resolver, auditDao)

        val snapshot = UiSnapshot(
            packageName = "com.android.chrome",
            editableNodes = listOf(UiNodeInfo(text = "Search or type web address", isEditable = true))
        )

        val preconditions = listOf(
            ActionPrecondition(PreconditionType.EDITABLE_PRESENT)
        )

        val result = executor.checkPreconditions(preconditions, snapshot)
        assertTrue(result.success)
    }
}
