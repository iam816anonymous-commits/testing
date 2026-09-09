package com.creator.automation

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ActionResolverTest {

    private lateinit var resolver: ActionResolver

    @Before
    fun setUp() {
        resolver = ActionResolver()
    }

    @Test
    fun testTargetResolutionPriority_ViewIdFirst() {
        val node1 = UiNodeInfo(
            text = "Analytics",
            contentDescription = "Analytics Tab",
            viewIdResourceName = "com.creator.automation:id/analytics_btn"
        )
        val snapshot = UiSnapshot(
            packageName = "com.creator.automation",
            allNodes = listOf(node1)
        )

        // Matching view ID end
        val match = resolver.resolveTarget(snapshot, "analytics_btn")
        assertNotNull(match)
        assertEquals("VIEW_ID", match?.matchMethod)
        assertEquals(1.0, match?.confidence ?: 0.0, 0.001)
    }

    @Test
    fun testTargetResolutionPriority_ExactText() {
        val node1 = UiNodeInfo(
            text = "Analytics",
            contentDescription = "Open Analytics Overview"
        )
        val snapshot = UiSnapshot(
            packageName = "com.creator.automation",
            allNodes = listOf(node1)
        )

        val match = resolver.resolveTarget(snapshot, "Analytics")
        assertNotNull(match)
        assertEquals("EXACT_TEXT", match?.matchMethod)
        assertEquals(0.95, match?.confidence ?: 0.0, 0.001)
    }

    @Test
    fun testTargetResolutionPriority_ContentDescription() {
        val node1 = UiNodeInfo(
            text = null,
            contentDescription = "Channel Dashboard"
        )
        val snapshot = UiSnapshot(
            packageName = "com.creator.automation",
            allNodes = listOf(node1)
        )

        val match = resolver.resolveTarget(snapshot, "Channel Dashboard")
        assertNotNull(match)
        assertEquals("CONTENT_DESCRIPTION", match?.matchMethod)
        assertEquals(0.90, match?.confidence ?: 0.0, 0.001)
    }

    @Test
    fun testTargetResolutionPriority_PartialAccessibilityProperties() {
        val node1 = UiNodeInfo(
            text = "View latest video analytics statistics"
        )
        val snapshot = UiSnapshot(
            packageName = "com.creator.automation",
            allNodes = listOf(node1)
        )

        val match = resolver.resolveTarget(snapshot, "analytics")
        assertNotNull(match)
        assertEquals("ACCESSIBILITY_PROPERTIES", match?.matchMethod)
        assertEquals(0.75, match?.confidence ?: 0.0, 0.001)
    }

    @Test
    fun testTargetResolution_NotFound() {
        val snapshot = UiSnapshot(
            packageName = "com.creator.automation",
            allNodes = listOf(
                UiNodeInfo(text = "Home"),
                UiNodeInfo(text = "Settings")
            )
        )

        val match = resolver.resolveTarget(snapshot, "NonExistentButton")
        assertNull(match)
    }
}
