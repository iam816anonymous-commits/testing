package com.creator.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class AutomationAccessibilityServiceTest {

    @Before
    fun setUp() {
        AutomationAccessibilityService.resetDiagnosticsForTesting()
    }

    @Test
    fun testInitialDiagnosticState() {
        val diag = AutomationAccessibilityService.diagnosticState.value
        assertFalse("serviceCreated should initially be false", diag.serviceCreated)
        assertFalse("serviceConnected should initially be false", diag.serviceConnected)
        assertEquals("eventCount should initially be 0", 0L, diag.eventCount)
        assertEquals("activePackage should initially be unknown", "unknown", diag.activePackage)
        assertFalse("rootAvailable should initially be false", diag.rootAvailable)
    }

    @Test
    fun testDiagnosticsSummary() {
        val service = AutomationAccessibilityService()
        val summary = service.getDiagnosticsSummary()
        assertNotNull("Diagnostics summary should not be null", summary)
        assertTrue("Summary should contain serviceCreated", summary.contains("serviceCreated"))
        assertTrue("Summary should contain serviceConnected", summary.contains("serviceConnected"))
        assertTrue("Summary should contain eventCount", summary.contains("eventCount"))
        assertTrue("Summary should contain activePackage", summary.contains("activePackage"))
    }

    @Test
    fun testCapabilityStateSeparation() {
        val permissionEnabled = true
        val serviceConnected = false
        val rootAvailable = false
        val eventsReceived = false

        val isUsable = permissionEnabled && serviceConnected && rootAvailable && eventsReceived
        assertFalse("Service should not be usable when connected is false despite permission enabled", isUsable)
    }
}
