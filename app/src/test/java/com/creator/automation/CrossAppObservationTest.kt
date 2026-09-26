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
class CrossAppObservationTest {

    @Before
    fun setUp() {
        AutomationAccessibilityService.resetDiagnosticsForTesting()
    }

    @Test
    fun testInitialCrossAppObservationState() {
        val state = AutomationAccessibilityService.crossAppObservationState.value
        assertFalse("Cross-app observation should initially be inactive", state.observationActive)
        assertEquals("unknown", state.foregroundPackage)
        assertEquals(0, state.nodeCount)
        assertEquals(0L, state.eventCount)
    }

    @Test
    fun testToggleCrossAppObservationActive() {
        val service = AutomationAccessibilityService()
        service.setCrossAppObservationActive(true)

        val stateActive = AutomationAccessibilityService.crossAppObservationState.value
        assertTrue("Observation should be active after toggle on", stateActive.observationActive)

        service.setCrossAppObservationActive(false)
        val stateInactive = AutomationAccessibilityService.crossAppObservationState.value
        assertFalse("Observation should be inactive after toggle off", stateInactive.observationActive)
    }

    @Test
    fun testZeroPollutionOverlayExclusion() {
        val snapshot = ActionResolver.captureSnapshot(null, "com.android.chrome")
        assertEquals("com.android.chrome", snapshot.packageName)
        assertFalse("Root is null so rootAvailable is false", snapshot.isRootAvailable)

        val agentNodesInExternalApp = snapshot.allNodes.filter { it.viewIdResourceName?.contains("com.creator.automation") == true }
        assertTrue("External app snapshot should exclude agent overlay nodes", agentNodesInExternalApp.isEmpty())
    }
}
