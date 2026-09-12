package com.creator.automation

import org.junit.Assert.*
import org.junit.Test

class GoalModelTest {

    @Test
    fun testParseGoalModelExtractsConstraintsAndCapabilities() {
        val rawGoal = "Open Chrome and search for new Telugu movies without sending messages"
        val goal = GoalModel.parse(rawGoal)

        assertNotNull(goal)
        assertEquals("Chrome", goal.targetAppQuery)
        assertEquals("new Telugu movies without sending messages", goal.expectedTextInResult)
        assertTrue("NO_SEND_MESSAGE constraint extracted", goal.constraints.contains("NO_SEND_MESSAGE"))
    }

    @Test
    fun testParseGoalModelExtractsCameraCapabilityRequirement() {
        val rawGoal = "Take a picture using camera"
        val goal = GoalModel.parse(rawGoal)

        assertNotNull(goal)
        assertTrue("CAMERA_VISION capability extracted", goal.requiredCapabilities.contains("CAMERA_VISION"))
        assertTrue("Camera permission required", goal.requiredPermissions.contains(android.Manifest.permission.CAMERA))
        assertEquals(ActionSemantics.HIGH_RISK, goal.sideEffectRisk)
    }

    @Test
    fun testWorldStateDiffCalculatesUIProgress() {
        val beforeSnapshot = UiSnapshot(
            packageName = "com.android.chrome",
            visibleTexts = listOf("Google", "Search")
        )
        val afterSnapshot = UiSnapshot(
            packageName = "com.android.chrome",
            visibleTexts = listOf("Google", "Search", "Telugu Movies 2026")
        )

        val beforeState = WorldState.fromSnapshot(beforeSnapshot)
        val afterState = WorldState.fromSnapshot(afterSnapshot)

        val diff = WorldStateDiff.calculateDiff(beforeState, afterState)

        assertNotNull(diff)
        assertFalse(diff.packageChanged)
        assertTrue("New texts detected", diff.newTextsAppeared.contains("Telugu Movies 2026"))
        assertTrue("Progress observed", diff.isProgressObserved)
    }
}
