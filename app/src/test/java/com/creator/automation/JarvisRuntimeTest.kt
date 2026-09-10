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

    @Test
    fun testResourceGuard_CheckMetricsMode() {
        val guard = ResourceGuard(mockContext)
        val metrics = guard.checkResourceState()

        assertNotNull(metrics.mode)
        assertTrue(metrics.usedHeapMb >= 0)
        assertTrue(metrics.maxHeapMb > 0)
    }

    @Test
    fun testAIContext_SanitizesSensitiveParameters() {
        val worldState = WorldState(
            packageName = "com.mock.login",
            visibleTexts = listOf("Username", "secretpassword123")
        )

        val sanitizedContext = AIContext.createSanitized("User login intent", worldState)

        assertEquals("com.mock.login", sanitizedContext.packageName)
        assertTrue(sanitizedContext.visibleTexts.contains("[REDACTED]"))
    }

    @Test
    fun testResearchEngine_DomainCategorization() {
        val engine = ResearchEngine(mockContext)

        assertEquals(ResearchDomain.YOUTUBE_INTELLIGENCE, engine.categorizeDomain("Check YouTube Short analytics"))
        assertEquals(ResearchDomain.NEWS_INTELLIGENCE, engine.categorizeDomain("Read today's AP news headlines"))
        assertEquals(ResearchDomain.FARM_RESEARCH, engine.categorizeDomain("Rainfall and crop yield analysis"))
        assertEquals(ResearchDomain.BUSINESS_RESEARCH, engine.categorizeDomain("Market competitor price comparison"))
    }

    @Test
    fun testAIWorkerRouter_FallbackToTaskDecisionEngineWhenUnconfigured() {
        val router = AIWorkerRouter(
            context = mockContext,
            primaryProvider = ChatGPTReasoningProvider(isNetworkAvailable = false),
            secondaryProvider = GeminiReasoningProvider(apiKey = null)
        )

        val goal = GoalModel.parse("Open Settings")
        val worldState = WorldState(packageName = "com.android.chrome", freshness = WorldStateFreshness.OBSERVED)

        kotlinx.coroutines.runBlocking {
            val decision = router.routeTaskReasoning(goal, worldState, emptyList())
            assertEquals(ActionType.LAUNCH_APP, decision.actionType)
            assertEquals("Settings", decision.target)
        }
    }

    @Test
    fun testAIWorkerRouter_WithConfiguredSecondaryProvider_RoutesSecondaryPlan() {
        val configuredGemini = GeminiReasoningProvider(apiKey = "mock-gemini-key")
        val router = AIWorkerRouter(
            context = mockContext,
            primaryProvider = ChatGPTReasoningProvider(isNetworkAvailable = false),
            secondaryProvider = configuredGemini
        )

        val goal = GoalModel.parse("Open Settings")
        val worldState = WorldState(packageName = "com.android.chrome", freshness = WorldStateFreshness.OBSERVED)
        val snapshot = AutomationCapabilitySnapshot(
            packageName = "com.android.chrome",
            availableCapabilities = listOf(ActionType.LAUNCH_APP.name, ActionType.READ_VISIBLE_UI.name)
        )

        kotlinx.coroutines.runBlocking {
            val decision = router.routeTaskReasoning(goal, worldState, listOf(snapshot))
            assertEquals(ActionType.LAUNCH_APP, decision.actionType)
            assertEquals("Settings", decision.target)
            assertTrue(decision.reason.contains("Gemini"))
        }
    }
}
