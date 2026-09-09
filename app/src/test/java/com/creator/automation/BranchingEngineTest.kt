package com.creator.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BranchingEngineTest {

    private lateinit var branchingEngine: BranchingEngine

    @Before
    fun setUp() {
        branchingEngine = BranchingEngine()
    }

    @Test
    fun testEvaluateBranch_TextConditionMet_TakesThenBranch() {
        val snapshot = UiSnapshot(
            packageName = "com.creator.automation",
            visibleTexts = listOf("Continue to Studio", "Dashboard")
        )

        val condition = BranchCondition(
            ifConditionType = WaitConditionType.WAIT_FOR_TEXT,
            ifExpectedValue = "Continue to Studio",
            thenStepId = "step_studio_dashboard",
            elseStepId = "step_get_app"
        )

        val decision = branchingEngine.evaluateBranch(condition, snapshot)

        assertTrue(decision.shouldBranch)
        assertEquals("step_studio_dashboard", decision.targetStepId)
        assertEquals("THEN", decision.branchTaken)
    }

    @Test
    fun testEvaluateBranch_TextConditionNotMet_TakesElseBranch() {
        val snapshot = UiSnapshot(
            packageName = "com.creator.automation",
            visibleTexts = listOf("Get the app", "Dashboard")
        )

        val condition = BranchCondition(
            ifConditionType = WaitConditionType.WAIT_FOR_TEXT,
            ifExpectedValue = "Continue to Studio",
            thenStepId = "step_studio_dashboard",
            elseStepId = "step_get_app"
        )

        val decision = branchingEngine.evaluateBranch(condition, snapshot)

        assertTrue(decision.shouldBranch)
        assertEquals("step_get_app", decision.targetStepId)
        assertEquals("ELSE", decision.branchTaken)
    }

    @Test
    fun testEvaluateBranch_ConditionNotMet_NoElseBranch_ReturnsNoBranch() {
        val snapshot = UiSnapshot(
            packageName = "com.creator.automation",
            visibleTexts = listOf("Welcome")
        )

        val condition = BranchCondition(
            ifConditionType = WaitConditionType.WAIT_FOR_TEXT,
            ifExpectedValue = "Continue to Studio",
            thenStepId = "step_studio_dashboard",
            elseStepId = null
        )

        val decision = branchingEngine.evaluateBranch(condition, snapshot)

        assertFalse(decision.shouldBranch)
        assertEquals("NONE", decision.branchTaken)
    }
}
