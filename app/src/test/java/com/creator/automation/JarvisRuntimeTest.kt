package com.creator.automation

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class JarvisRuntimeTest {

    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockContext = Mockito.mock(Context::class.java)
    }

    @Test
    fun testWorldState_FromSnapshot_CalculatesMetrics() {
        val snapshot = UiSnapshot(
            packageName = "com.mock.chrome",
            visibleTexts = listOf("Search or type web address", "Google"),
            allNodes = listOf(
                UiNodeInfo(text = "Search or type web address", isEditable = true, isEnabled = true),
                UiNodeInfo(text = "Google", isClickable = true, isEnabled = false)
            )
        )

        val worldState = WorldState.fromSnapshot(snapshot, isAccessibilityAvailable = true)

        assertEquals("com.mock.chrome", worldState.packageName)
        assertEquals(WorldStateFreshness.OBSERVED, worldState.freshness)
        assertEquals(1, worldState.enabledControlsCount)
        assertEquals(1, worldState.disabledControlsCount)
        assertEquals(1, worldState.editableTargets.size)
    }

    @Test
    fun testGoalModel_ParseAndSatisfaction() {
        val rawGoal = "Open Chrome and search for new Telugu movies"
        val goal = GoalModel.parse(rawGoal)

        assertEquals("Chrome", goal.targetAppQuery)
        assertEquals("new Telugu movies", goal.expectedTextInResult)

        val matchingSnapshot = UiSnapshot(
            packageName = "com.android.chrome",
            visibleTexts = listOf("Results for new Telugu movies")
        )
        val matchingState = WorldState.fromSnapshot(matchingSnapshot)

        assertTrue(goal.isGoalSatisfied(matchingState))
    }

    @Test
    fun testTaskDecisionEngine_GeneratesStructuredActionDecision() {
        val engine = TaskDecisionEngine(mockContext)
        val goal = GoalModel.parse("Open Chrome and search for new Telugu movies")

        // 1. Initial State: wrong package -> Decision: LAUNCH_APP
        val state1 = WorldState(packageName = "com.android.settings", freshness = WorldStateFreshness.OBSERVED)
        val decision1 = engine.decideNextAction(goal, state1)

        assertEquals(ActionType.LAUNCH_APP, decision1.actionType)
        assertEquals("Chrome", decision1.target)

        // 2. In Chrome with editable field -> Decision: TYPE_TEXT
        val state2 = WorldState(
            packageName = "com.android.chrome",
            editableTargets = listOf(UiNodeInfo(text = "Search", isEditable = true, isEnabled = true)),
            freshness = WorldStateFreshness.OBSERVED
        )
        val decision2 = engine.decideNextAction(goal, state2)

        assertEquals(ActionType.TYPE_TEXT, decision2.actionType)
        assertEquals("new Telugu movies", decision2.inputData)
    }

    @Test
    fun testSkillRegistry_FindsApplicableSkills() {
        val registry = SkillRegistry(mockContext)
        val worldState = WorldState(packageName = "com.android.chrome", freshness = WorldStateFreshness.OBSERVED)

        val openSkill = registry.findApplicableSkill(worldState, "Open Chrome")
        assertNotNull(openSkill)
        assertEquals("OpenApp", openSkill?.name)

        val searchSkill = registry.findApplicableSkill(worldState, "Search for movies")
        assertNotNull(searchSkill)
        assertEquals("Search", searchSkill?.name)
    }
}
