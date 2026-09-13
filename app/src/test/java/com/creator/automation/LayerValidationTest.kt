package com.creator.automation

import android.os.Build
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
@Config(sdk = [Build.VERSION_CODES.O_MR1])
class LayerValidationTest {

    private lateinit var actionResolver: ActionResolver
    private lateinit var controller: LayerValidationController

    @Before
    fun setUp() {
        actionResolver = ActionResolver()
        LayerValidationController.resetForTesting()
        controller = LayerValidationController.getOrCreateInstance(actionResolver)
    }

    private fun createSampleSnapshot(
        packageName: String = "com.example.app",
        text: String = "Search",
        isScrollable: Boolean = false
    ): UiSnapshot {
        val node = UiNodeInfo(
            text = text,
            contentDescription = null,
            viewIdResourceName = "com.example.app:id/search_button",
            className = "android.widget.Button",
            isClickable = true,
            isScrollable = isScrollable,
            isEditable = false,
            isVisibleToUser = true,
            isEnabled = true,
            boundsInScreen = "[100,200][300,400]"
        )
        return UiSnapshot(
            packageName = packageName,
            isRootAvailable = true,
            visibleTexts = listOf(text),
            clickableNodes = listOf(node),
            scrollableNodes = if (isScrollable) listOf(node) else emptyList(),
            allNodes = listOf(node)
        )
    }

    @Test
    fun testTargetRetentionAndStaleDetection() {
        val snap1 = createSampleSnapshot(packageName = "com.example.app", text = "Search")
        val candidate = TargetCandidate(
            node = snap1.allNodes.first(),
            matchType = "EXACT_TEXT",
            score = 0.95
        )

        controller.selectAndRetainTarget(candidate, "Search", snap1)

        val retained = LayerValidationController.retainedTarget.value
        assertNotNull(retained)
        assertEquals("Search", retained?.candidate?.text)
        assertEquals(ValidationTargetStatus.READY, retained?.status)

        // Evaluate on identical screen -> remains READY
        val freshness1 = controller.evaluateTargetFreshness(snap1)
        assertEquals(ValidationTargetStatus.READY, freshness1)

        // Evaluate on completely different screen where target is missing -> STALE
        val snap2 = UiSnapshot(
            packageName = "com.different.package",
            isRootAvailable = true,
            visibleTexts = listOf("Settings"),
            allNodes = listOf(UiNodeInfo(text = "Settings", isVisibleToUser = true))
        )
        val freshness2 = controller.evaluateTargetFreshness(snap2)
        assertEquals(ValidationTargetStatus.STALE, freshness2)
    }

    @Test
    fun testLayer4TouchBlockedWhenNoTargetSelected() {
        val service = org.robolectric.Robolectric.buildService(AutomationAccessibilityService::class.java).create().get()
        kotlinx.coroutines.runBlocking {
            val trace = controller.executeLayer4TouchTest(service)
            assertEquals(4, trace.layer)
            assertEquals("CLICK", trace.actionType)
            assertFalse(trace.dispatchAttempted)
            assertEquals("BLOCKED", trace.dispatchResult)
            assertEquals("NO_TARGET_SELECTED", trace.resolutionStatus)
            assertFalse(trace.isConfirmed)
        }
    }

    @Test
    fun testLayer4TouchBlockedWhenTargetStale() {
        val snap1 = createSampleSnapshot(packageName = "com.example.app", text = "Search")
        val candidate = TargetCandidate(node = snap1.allNodes.first(), matchType = "EXACT_TEXT", score = 0.95)
        controller.selectAndRetainTarget(candidate, "Search", snap1)

        // Force target status to STALE
        val snap2 = createSampleSnapshot(packageName = "com.example.app", text = "Settings")
        controller.evaluateTargetFreshness(snap2)

        val service = org.robolectric.Robolectric.buildService(AutomationAccessibilityService::class.java).create().get()
        kotlinx.coroutines.runBlocking {
            val trace = controller.executeLayer4TouchTest(service)
            assertEquals(4, trace.layer)
            assertFalse(trace.dispatchAttempted)
            assertEquals("BLOCKED", trace.dispatchResult)
            assertEquals("STALE_TARGET", trace.resolutionStatus)
            assertFalse(trace.isConfirmed)
        }
    }

    @Test
    fun testLayer5ScrollBlockedWhenNoScrollableRegion() {
        val service = org.robolectric.Robolectric.buildService(AutomationAccessibilityService::class.java).create().get()
        kotlinx.coroutines.runBlocking {
            val trace = controller.executeLayer5ScrollTest(service)
            assertEquals(5, trace.layer)
            assertTrue(trace.actionType.startsWith("SCROLL"))
            assertFalse(trace.dispatchAttempted)
            assertEquals("BLOCKED", trace.dispatchResult)
            assertEquals("NO_SCROLLABLE_REGION", trace.resolutionStatus)
            assertFalse(trace.isConfirmed)
        }
    }

    @Test
    fun testOverlayNodeExclusionInPerception() {
        val normalNode = UiNodeInfo(
            text = "Normal App Button",
            viewIdResourceName = "com.target.app:id/button",
            className = "android.widget.Button",
            isClickable = true
        )

        val snap = UiSnapshot(
            packageName = "com.target.app",
            isRootAvailable = true,
            allNodes = listOf(normalNode),
            clickableNodes = listOf(normalNode)
        )

        // Discover target "CROSS-APP CONSOLE" -> MUST return NOT_FOUND as overlay node is excluded
        val targetRes = actionResolver.discoverTarget(snap, TargetRequest(requestedText = "CROSS-APP CONSOLE"))
        assertEquals(TargetResolutionStatus.NOT_FOUND, targetRes.status)
    }

    @Test
    fun testLayerValidationStatusDefaults() {
        val status = LayerValidationController.layerStatus.value
        assertEquals("PASS", status.l1Accessibility)
        assertEquals("PASS", status.l2Observation)
        assertEquals("PASS", status.l3TargetDiscovery)
        assertEquals("AVAILABLE", status.l4Touch)
        assertEquals("AVAILABLE", status.l5Scroll)
        assertEquals("LOCKED", status.l6Focus)
        assertEquals("LOCKED", status.l7TextInput)
        assertEquals("LOCKED", status.l8Submit)
        assertEquals("LOCKED", status.l9Navigation)
    }

    @Test
    fun testOverlayMenuNavigationAndCollapseState() {
        assertFalse(LayerValidationController.isOverlayExpanded.value)
        assertEquals(OverlayMenuView.MAIN_MENU, LayerValidationController.currentMenuView.value)

        LayerValidationController.setOverlayExpanded(true)
        assertTrue(LayerValidationController.isOverlayExpanded.value)

        LayerValidationController.navigateMenuView(OverlayMenuView.L3_TARGET)
        assertEquals(OverlayMenuView.L3_TARGET, LayerValidationController.currentMenuView.value)

        LayerValidationController.navigateMenuView(OverlayMenuView.L4_TOUCH)
        assertEquals(OverlayMenuView.L4_TOUCH, LayerValidationController.currentMenuView.value)

        // Collapsing resets menu view to MAIN_MENU
        LayerValidationController.setOverlayExpanded(false)
        assertFalse(LayerValidationController.isOverlayExpanded.value)
        assertEquals(OverlayMenuView.MAIN_MENU, LayerValidationController.currentMenuView.value)
    }

    @Test
    fun testL3ToL4TargetRetentionFlow() {
        val snap = createSampleSnapshot(packageName = "com.google.android.youtube", text = "Search")
        val candidate = TargetCandidate(node = snap.allNodes.first(), matchType = "EXACT_TEXT", score = 0.95)

        LayerValidationController.navigateMenuView(OverlayMenuView.L3_TARGET)
        controller.selectAndRetainTarget(candidate, "Search", snap)

        val retained = LayerValidationController.retainedTarget.value
        assertNotNull(retained)
        assertEquals("Search", retained?.candidate?.text)

        // Seamless transition to L4 Touch
        LayerValidationController.navigateMenuView(OverlayMenuView.L4_TOUCH)
        assertEquals(OverlayMenuView.L4_TOUCH, LayerValidationController.currentMenuView.value)
        assertEquals(ValidationTargetStatus.READY, retained?.status)
    }

    @Test
    fun testValidationStateAndFailureReasonTaxonomy() {
        assertEquals("NO_OBSERVATION", ValidationState.NO_OBSERVATION.name)
        assertEquals("CONFIRMED", ValidationState.CONFIRMED.name)
        assertEquals("NOT_CONFIRMED", ValidationState.NOT_CONFIRMED.name)
        assertEquals("TARGET_STALE", ValidationFailureReason.TARGET_STALE.name)
        assertEquals("SCROLL_REGION_NOT_FOUND", ValidationFailureReason.SCROLL_REGION_NOT_FOUND.name)
    }

    @Test
    fun testDiscoverInteractionSurfacesExtraction() {
        val snap = createSampleSnapshot(packageName = "com.example.app", text = "Search Bar", isScrollable = true)
        val surfaces = actionResolver.discoverInteractionSurfaces(snap)

        assertFalse(surfaces.isEmpty())
        val firstSurface = surfaces.first()
        assertEquals("Search Bar", firstSurface.text)
        assertTrue(firstSurface.isClickable)
        assertTrue(firstSurface.isScrollable)
        assertEquals("ScrollView", firstSurface.role)
    }
}
