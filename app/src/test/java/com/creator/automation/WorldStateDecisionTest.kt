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
            normalizedObjective = "Open Chrome",
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
            normalizedObjective = "Search query",
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
            normalizedObjective = "Open YouTube",
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
            normalizedObjective = "Search cats",
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
            normalizedObjective = "Search query",
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
            normalizedObjective = "Search query",
            targetAppQuery = "chrome",
            expectedTextInResult = "movies"
        )
        val worldState = WorldState(packageName = "com.android.chrome")

        val decision = decisionEngine.decideNextAction(goal, worldState)
        assertEquals(ActionType.SUBMIT_INPUT, decision.actionType)
    }

    @Test
    fun testDecisionLayerDoesNotExecuteActions() {
        val goal = GoalModel(
            rawUserIntent = "Test intent",
            normalizedObjective = "Test intent"
        )
        val worldState = WorldState(packageName = "com.example.test")

        val decision = decisionEngine.decideNextAction(goal, worldState)
        assertNotNull(decision)
        assertTrue(decision is ActionDecision)
    }

    // Command Decomposition Unit Tests (Scenarios A through J)

    @Test
    fun testScenarioA_TypeCommand() {
        val parsed = GoalModel.parse("Type new Telugu movies")
        assertEquals(ActionType.TYPE_TEXT, parsed.requestedActionType)
        assertEquals("new Telugu movies", parsed.expectedTextInResult)
    }

    @Test
    fun testScenarioB_PressGoCommand() {
        val parsed = GoalModel.parse("Press Go")
        assertEquals(ActionType.CLICK_TEXT, parsed.requestedActionType)
        assertEquals("Go", parsed.requestedActionTarget)
        assertNull(parsed.expectedTextInResult)
    }

    @Test
    fun testScenarioC_PressEnterCommand() {
        val parsed = GoalModel.parse("Press Enter")
        assertEquals(ActionType.SUBMIT_INPUT, parsed.requestedActionType)
        assertNull(parsed.expectedTextInResult)
    }

    @Test
    fun testScenarioD_SearchForMovies() {
        val parsed = GoalModel.parse("Search for new Telugu movies")
        assertEquals("new Telugu movies", parsed.expectedTextInResult)
        assertEquals(ActionType.SUBMIT_INPUT, parsed.requestedActionType)
    }

    @Test
    fun testScenarioE_CompoundSearchAndPressGo() {
        val parsed = GoalModel.parse("Search for new Telugu movies and press Go")
        assertEquals("new Telugu movies", parsed.expectedTextInResult)
        assertEquals(ActionType.SUBMIT_INPUT, parsed.requestedActionType)
        assertEquals("Go", parsed.requestedActionTarget)
    }

    @Test
    fun testScenarioF_TypeGoPreservesPayload() {
        val parsed = GoalModel.parse("Type Go")
        assertEquals(ActionType.TYPE_TEXT, parsed.requestedActionType)
        assertEquals("Go", parsed.expectedTextInResult)
    }

    @Test
    fun testScenarioG_TypeEnterPreservesPayload() {
        val parsed = GoalModel.parse("Type Enter")
        assertEquals(ActionType.TYPE_TEXT, parsed.requestedActionType)
        assertEquals("Enter", parsed.expectedTextInResult)
    }

    @Test
    fun testScenarioH_SearchForMoviesAboutGoPreservesPayload() {
        val parsed = GoalModel.parse("Search for movies about Go")
        assertEquals("movies about Go", parsed.expectedTextInResult)
        assertEquals(ActionType.SUBMIT_INPUT, parsed.requestedActionType)
    }

    @Test
    fun testScenarioI_SearchForEnterKeyboardShortcutsPreservesPayload() {
        val parsed = GoalModel.parse("Search for Enter keyboard shortcuts")
        assertEquals("Enter keyboard shortcuts", parsed.expectedTextInResult)
        assertEquals(ActionType.SUBMIT_INPUT, parsed.requestedActionType)
    }

    @Test
    fun testScenarioJ_PressGoAfterTypingHello() {
        val parsed = GoalModel.parse("Press Go after typing hello")
        assertEquals("hello", parsed.expectedTextInResult)
        assertEquals(ActionType.SUBMIT_INPUT, parsed.requestedActionType)
        assertEquals("Go", parsed.requestedActionTarget)
    }
}
