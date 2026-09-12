package com.creator.automation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class InteractionDiscoveryTest {

    @Test
    fun testAppInteractionProfileDefaults() {
        val profile = AppInteractionProfile(
            packageName = "com.google.android.youtube"
        )

        assertEquals("com.google.android.youtube", profile.packageName)
        assertTrue(profile.observedStates.isEmpty())
        assertTrue(profile.interactionCapabilities.isEmpty())
        assertEquals(0, profile.successfulActionCount)
        assertEquals(0.5, profile.overallConfidence, 0.01)
    }

    @Test
    fun testDiscoverSurfacesFromSnapshot() {
        val nodeEditable = UiNodeInfo(
            text = "Search YouTube",
            viewIdResourceName = "com.google.android.youtube:id/search_edit_text",
            isEditable = true,
            isClickable = true
        )

        val nodeSubmit = UiNodeInfo(
            text = "Search",
            viewIdResourceName = "com.google.android.youtube:id/search_button",
            isClickable = true
        )

        val nodeScroll = UiNodeInfo(
            className = "androidx.recyclerview.widget.RecyclerView",
            isScrollable = true
        )

        val nodeNav = UiNodeInfo(
            contentDescription = "Navigate up",
            isClickable = true
        )

        val nodeDestructive = UiNodeInfo(
            text = "Delete account history",
            isClickable = true
        )

        val snapshot = UiSnapshot(
            packageName = "com.google.android.youtube",
            allNodes = listOf(nodeEditable, nodeSubmit, nodeScroll, nodeNav, nodeDestructive)
        )

        val surfaces = InteractionDiscoveryEngine.discoverSurfaces(snapshot)
        assertFalse(surfaces.isEmpty())

        assertTrue(surfaces.any { it.surfaceType == InteractionSurfaceType.EDITABLE_FIELD })
        assertTrue(surfaces.any { it.surfaceType == InteractionSurfaceType.SUBMIT_CANDIDATE })
        assertTrue(surfaces.any { it.surfaceType == InteractionSurfaceType.SCROLLABLE_CONTAINER })
        assertTrue(surfaces.any { it.surfaceType == InteractionSurfaceType.NAVIGATION_CONTROL })
    }

    @Test
    fun testGenerateCandidatesSafetyLevels() {
        val nodeEditable = UiNodeInfo(
            text = "Search YouTube",
            isEditable = true,
            isClickable = true
        )

        val nodeDestructive = UiNodeInfo(
            text = "Delete account history",
            isClickable = true
        )

        val snapshot = UiSnapshot(
            packageName = "com.google.android.youtube",
            allNodes = listOf(nodeEditable, nodeDestructive)
        )

        val surfaces = InteractionDiscoveryEngine.discoverSurfaces(snapshot)
        val candidates = InteractionDiscoveryEngine.generateCandidates(surfaces)

        assertFalse(candidates.isEmpty())

        val safeCandidates = candidates.filter { it.safetyLevel == InteractionSafetyLevel.SAFE_TO_EXPLORE }
        val blockedCandidates = candidates.filter { it.safetyLevel == InteractionSafetyLevel.DESTRUCTIVE_BLOCKED }

        assertTrue(safeCandidates.isNotEmpty())
        assertTrue(blockedCandidates.isNotEmpty())
        assertTrue(blockedCandidates.any { it.surface.label?.contains("Delete", ignoreCase = true) == true })
    }

    @Test
    fun testTransitionObservationEngine() = runBlocking {
        val engine = TransitionObservationEngine(dao = null)

        val preSnapshot = UiSnapshot(
            packageName = "com.example.app",
            visibleTexts = listOf("Home", "Search")
        )

        val postSnapshot = UiSnapshot(
            packageName = "com.example.app",
            visibleTexts = listOf("SearchResults", "Item 1", "Item 2")
        )

        val verification = GoalVerificationResult(
            status = GoalVerificationStatus.GOAL_VERIFIED,
            isVerified = true,
            explanation = "Search results verified"
        )

        val transition = engine.observeTransition(
            packageName = "com.example.app",
            preSnapshot = preSnapshot,
            postSnapshot = postSnapshot,
            actionType = ActionType.SUBMIT_INPUT,
            targetSurfaceId = "Search",
            verificationResult = verification
        )

        assertEquals("com.example.app", transition.packageName)
        assertTrue(transition.isStateChanged)
        assertEquals(GoalVerificationStatus.GOAL_VERIFIED, transition.verificationStatus)
        assertEquals(0.95, transition.confidence, 0.01)

        val graph = engine.assembleFlowGraph("com.example.app", listOf(transition))
        assertEquals("com.example.app", graph.packageName)
        assertEquals(1, graph.totalExplorations)
        assertEquals(2, graph.knownStates.size)
    }
}
