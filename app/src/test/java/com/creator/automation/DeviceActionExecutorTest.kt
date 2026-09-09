package com.creator.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
