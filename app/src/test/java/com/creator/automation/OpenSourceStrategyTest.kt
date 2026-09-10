package com.creator.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenSourceStrategyTest {

    private lateinit var resolver: ActionResolver

    @Before
    fun setUp() {
        resolver = ActionResolver()
    }

    @Test
    fun testCandidateConfidenceScoring_ViewIdHasHighestConfidence() {
        val node1 = UiNodeInfo(
            text = "Settings",
            viewIdResourceName = "com.creator.automation:id/settings_btn"
        )
        val snapshot = UiSnapshot(
            packageName = "com.creator.automation",
            allNodes = listOf(node1)
        )

        val res = resolver.resolveTargetWithAmbiguity(snapshot, "settings_btn")

        assertNotNull(res.match)
        assertEquals("VIEW_ID", res.match?.matchMethod)
        assertEquals(1.0, res.match?.confidence ?: 0.0, 0.001)
        assertEquals(TargetResolutionStatus.FOUND_UNIQUE, res.status)
    }

    @Test
    fun testCandidateConfidenceScoring_ExactTextMatch() {
        val node1 = UiNodeInfo(
            text = "Settings",
            viewIdResourceName = null
        )
        val snapshot = UiSnapshot(
            packageName = "com.creator.automation",
            allNodes = listOf(node1)
        )

        val res = resolver.resolveTargetWithAmbiguity(snapshot, "Settings")

        assertNotNull(res.match)
        assertEquals("EXACT_TEXT", res.match?.matchMethod)
        assertEquals(0.95, res.match?.confidence ?: 0.0, 0.001)
        assertEquals(TargetResolutionStatus.FOUND_UNIQUE, res.status)
    }

    @Test
    fun testCandidateConfidenceScoring_ContentDescriptionMatch() {
        val node1 = UiNodeInfo(
            text = null,
            contentDescription = "Open App Settings"
        )
        val snapshot = UiSnapshot(
            packageName = "com.creator.automation",
            allNodes = listOf(node1)
        )

        val res = resolver.resolveTargetWithAmbiguity(snapshot, "Open App Settings")

        assertNotNull(res.match)
        assertEquals("CONTENT_DESCRIPTION", res.match?.matchMethod)
        assertEquals(0.90, res.match?.confidence ?: 0.0, 0.001)
        assertEquals(TargetResolutionStatus.FOUND_UNIQUE, res.status)
    }

    @Test
    fun testActionabilityFiltering_DisabledNode_ReturnsNotActionableStatus() {
        val disabledNode = UiNodeInfo(
            text = "Disabled Option",
            isClickable = true,
            isEnabled = false
        )
        val snapshot = UiSnapshot(
            packageName = "com.creator.automation",
            allNodes = listOf(disabledNode)
        )

        val res = resolver.resolveTargetWithAmbiguity(snapshot, "Disabled Option")

        assertNotNull(res.match)
        assertEquals(TargetResolutionStatus.NOT_ACTIONABLE, res.status)
        assertFalse(res.match!!.node.isEnabled)
    }

    @Test
    fun testAmbiguityDetection_MultipleCandidates_ReturnsAmbiguousStatus() {
        val node1 = UiNodeInfo(text = "Download", isClickable = true)
        val node2 = UiNodeInfo(text = "Download", isClickable = true)
        val snapshot = UiSnapshot(
            packageName = "com.creator.automation",
            allNodes = listOf(node1, node2)
        )

        val res = resolver.resolveTargetWithAmbiguity(snapshot, "Download")

        assertTrue(res.isAmbiguous)
        assertEquals(2, res.candidateCount)
        assertEquals(TargetResolutionStatus.AMBIGUOUS, res.status)
    }
}
