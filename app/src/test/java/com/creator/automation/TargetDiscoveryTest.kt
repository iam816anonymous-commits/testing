package com.creator.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class TargetDiscoveryTest {

    private lateinit var resolver: ActionResolver

    @Before
    fun setUp() {
        resolver = ActionResolver()
        AutomationAccessibilityService.resetDiagnosticsForTesting()
    }

    @Test
    fun testOverlayEventIgnoredInService() {
        val service = AutomationAccessibilityService()
        val initialEventCount = AutomationAccessibilityService.diagnosticState.value.eventCount

        val event = android.view.accessibility.AccessibilityEvent.obtain(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            packageName = "com.creator.automation"
        }

        service.onAccessibilityEvent(event)

        val updatedEventCount = AutomationAccessibilityService.diagnosticState.value.eventCount
        assertEquals("Agent overlay accessibility events must be ignored to prevent layout feedback loops", initialEventCount, updatedEventCount)
    }

    @Test
    fun testDiscoverTargetExactTextFound() {
        val node1 = UiNodeInfo(text = "Search", viewIdResourceName = "com.youtube:id/search_button", isClickable = true, boundsInScreen = "[10,10][100,50]")
        val snapshot = UiSnapshot(
            packageName = "com.google.android.youtube",
            visibleTexts = listOf("Search"),
            allNodes = listOf(node1)
        )

        val req = TargetRequest(requestedText = "Search")
        val res = resolver.discoverTarget(snapshot, req)

        assertEquals(TargetResolutionStatus.FOUND_UNIQUE, res.status)
        assertEquals(1, res.candidateCount)
        assertFalse(res.isAmbiguous)
        assertNotNull(res.match)
        assertEquals("Search", res.match?.node?.text)
        assertEquals("[10,10][100,50]", res.match?.node?.boundsInScreen)
    }

    @Test
    fun testDiscoverTargetAmbiguous() {
        val node1 = UiNodeInfo(text = "Search", boundsInScreen = "[10,10][100,50]")
        val node2 = UiNodeInfo(text = "Search", boundsInScreen = "[10,60][100,100]")
        val snapshot = UiSnapshot(
            packageName = "com.google.android.youtube",
            visibleTexts = listOf("Search"),
            allNodes = listOf(node1, node2)
        )

        val req = TargetRequest(requestedText = "Search")
        val res = resolver.discoverTarget(snapshot, req)

        assertEquals(TargetResolutionStatus.AMBIGUOUS, res.status)
        assertEquals(2, res.candidateCount)
        assertTrue(res.isAmbiguous)
    }

    @Test
    fun testDiscoverTargetGenericRoleIsAmbiguous() {
        val node1 = UiNodeInfo(className = "android.widget.TextView", boundsInScreen = "[10,10][100,50]")
        val snapshot = UiSnapshot(
            packageName = "com.example.app",
            allNodes = listOf(node1)
        )

        val req = TargetRequest(requestedRole = "TextView")
        val res = resolver.discoverTarget(snapshot, req)

        assertEquals(TargetResolutionStatus.AMBIGUOUS, res.status)
        assertEquals(0.40, res.match?.confidence ?: 0.0, 0.01)
    }

    @Test
    fun testDiscoverTargetNotFound() {
        val node1 = UiNodeInfo(text = "Home", boundsInScreen = "[0,0][100,50]")
        val snapshot = UiSnapshot(
            packageName = "com.example.app",
            visibleTexts = listOf("Home"),
            allNodes = listOf(node1)
        )

        val req = TargetRequest(requestedText = "NonExistentButton")
        val res = resolver.discoverTarget(snapshot, req)

        assertEquals(TargetResolutionStatus.NOT_FOUND, res.status)
        assertEquals(0, res.candidateCount)
        assertNull(res.match)
    }

    @Test
    fun testGenerateInteractionMap() {
        val node1 = UiNodeInfo(text = "Search", isClickable = true, boundsInScreen = "[10,10][100,50]")
        val node2 = UiNodeInfo(text = "Input", isEditable = true, boundsInScreen = "[10,60][100,100]")
        val snapshot = UiSnapshot(packageName = "com.test.app", allNodes = listOf(node1, node2))

        val map = resolver.generateInteractionMap(snapshot)
        assertEquals("com.test.app", map.packageName)
        assertEquals(2, map.totalElements)
        assertEquals(2, map.interactiveElementsCount)
    }

    @Test
    fun testReportMechanismAvailabilityReadOnlyInvariant() {
        val node = UiNodeInfo(text = "Submit", isClickable = true, boundsInScreen = "[0,0][10,10]")
        val mech = resolver.reportMechanismAvailability(node)

        assertTrue(mech.accessibilityClick)
        assertTrue(mech.gestureFallback)
        assertFalse("Layer 3 mechanism report must strictly enforce actionDispatched = false", mech.actionDispatched)
    }

    @Test
    fun testAutoDetectScreenReadOnlyInvariant() {
        val snapshot = UiSnapshot(packageName = "com.test.app", isRootAvailable = true)
        val autoDet = resolver.autoDetectScreen(snapshot)

        assertEquals("com.test.app", autoDet.packageName)
        assertTrue(autoDet.isRootAvailable)
        assertFalse("Auto detect screen analysis must strictly enforce actionDispatched = false", autoDet.actionDispatched)
    }
}
