package com.creator.automation

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WorldStateDecisionTest {

    private lateinit var decisionEngine: TaskDecisionEngine

    @Before
    fun setUp() {
        // TaskDecisionEngine works deterministically without active Android Context in Robolectric/unit environment
        decisionEngine = TaskDecisionEngine(org.mockito.Mockito.mock(android.content.Context::class.java))
    }

    @Test
    fun testGoalAlreadySatisfiedDecidesEnd() {
        val goal = GoalModel(
            rawUserIntent = "Open Chrome",
            targetAppQuery = "chrome",
            expectedTextInResult = "Google"
        )
        val worldState = WorldState(
            packageName = "com.android.chrome",
            visibleTexts = listOf("Google", "Search")
        )

        val decision = decisionEngine.decideNextAction(goal, worldState)
        assertEquals(ActionType.END, decision.actionType)
        assertEquals(1.0, decision.confidence, 0.01)
    }

    @Test
    fun testActionBudgetExceededDecidesEnd() {
        val goal = GoalModel(
            rawUserIntent = "Search query",
            currentActionCount = 10,
            maxActionBudget = 10
        )
        val worldState = WorldState(packageName = "com.example.app")

        val decision = decisionEngine.decideNextAction(goal, worldState)
        assertEquals(ActionType.END, decision.actionType)
        assertEquals(0.0, decision.confidence, 0.01)
    }

    @Test
    fun testAppMismatchDecidesLaunchApp() {
        val goal = GoalModel(
            rawUserIntent = "Open YouTube",
            targetAppQuery = "youtube"
        )
        val worldState = WorldState(packageName = "com.android.chrome")

        val decision = decisionEngine.decideNextAction(goal, worldState)
        assertEquals(ActionType.LAUNCH_APP, decision.actionType)
        assertEquals("youtube", decision.target)
    }

    @Test
    fun testEditableFieldAvailableDecidesTypeText() {
        val goal = GoalModel(
            rawUserIntent = "Search cats",
            targetAppQuery = "chrome",
            expectedTextInResult = "cats"
        )
        val worldState = WorldState(
            packageName = "com.android.chrome",
            editableTargets = listOf(UiNodeInfo(text = "Search or type web address", isEditable = true))
        )

        val decision = decisionEngine.decideNextAction(goal, worldState)
        assertEquals(ActionType.TYPE_TEXT, decision.actionType)
        assertEquals("cats", decision.inputData)
    }

    @Test
    fun testClickableSearchButtonDecidesClickText() {
        val goal = GoalModel(
            rawUserIntent = "Search query",
            targetAppQuery = "chrome",
            expectedTextInResult = "movies"
        )
        val worldState = WorldState(
            packageName = "com.android.chrome",
            clickableTargets = listOf(UiNodeInfo(text = "Search", isClickable = true))
        )

        val decision = decisionEngine.decideNextAction(goal, worldState)
        assertEquals(ActionType.CLICK_TEXT, decision.actionType)
        assertEquals("Search", decision.target)
    }

    @Test
    fun testFallbackDecidesSubmitInput() {
        val goal = GoalModel(
            rawUserIntent = "Search query",
            targetAppQuery = "chrome",
            expectedTextInResult = "movies"
        )
        val worldState = WorldState(packageName = "com.android.chrome")

        val decision = decisionEngine.decideNextAction(goal, worldState)
        assertEquals(ActionType.SUBMIT_INPUT, decision.actionType)
    }

    @Test
    fun testDecisionLayerDoesNotExecuteActions() {
        val goal = GoalModel(rawUserIntent = "Test intent")
        val worldState = WorldState(packageName = "com.example.test")

        val decision = decisionEngine.decideNextAction(goal, worldState)
        assertNotNull(decision)
        assertTrue(decision is ActionDecision)
    }
}
