package com.creator.automation

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])
class AutonomousValidationEngineTest {

    private lateinit var context: android.content.Context
    private lateinit var engine: AutonomousValidationEngine

    @Before
    fun setUp() {
        context = org.robolectric.RuntimeEnvironment.getApplication()
        AutonomousValidationEngine.resetForTesting()
        engine = AutonomousValidationEngine.getOrCreateInstance(context)
    }

    @Test
    fun testTargetSafetyClassification() {
        assertEquals(TargetSafetyLevel.SAFE, engine.classifyTargetSafety("Search"))
        assertEquals(TargetSafetyLevel.SAFE, engine.classifyTargetSafety("Open Chrome"))
        assertEquals(TargetSafetyLevel.DESTRUCTIVE, engine.classifyTargetSafety("Delete account"))
        assertEquals(TargetSafetyLevel.DESTRUCTIVE, engine.classifyTargetSafety("Uninstall App"))
        assertEquals(TargetSafetyLevel.CAUTION, engine.classifyTargetSafety("Device Settings"))
        assertEquals(TargetSafetyLevel.CAUTION, engine.classifyTargetSafety("Security Permissions"))
    }

    @Test
    fun testThreeDimensionTruthEvaluation() {
        val fullPass = ThreeDimensionTruth(
            dispatchTruth = DispatchTruth(acceptedBySystem = true, mechanismUsed = "ACTION_CLICK"),
            observationTruth = ObservationTruth(uiStateChanged = true),
            goalTruth = GoalTruth(goalAchieved = true)
        )
        assertTrue(fullPass.isFullPass)

        val failedObservation = ThreeDimensionTruth(
            dispatchTruth = DispatchTruth(acceptedBySystem = true, mechanismUsed = "ACTION_CLICK"),
            observationTruth = ObservationTruth(uiStateChanged = false),
            goalTruth = GoalTruth(goalAchieved = true)
        )
        assertFalse(failedObservation.isFullPass)

        val failedGoal = ThreeDimensionTruth(
            dispatchTruth = DispatchTruth(acceptedBySystem = true, mechanismUsed = "ACTION_CLICK"),
            observationTruth = ObservationTruth(uiStateChanged = true),
            goalTruth = GoalTruth(goalAchieved = false)
        )
        assertFalse(failedGoal.isFullPass)
    }

    @Test
    fun testEngineProgressAndCancellationState() {
        val initialProgress = AutonomousValidationEngine.progress.value
        assertFalse(initialProgress.isRunning)
        assertFalse(initialProgress.isCancelled)

        engine.stopValidation()
        val stoppedProgress = AutonomousValidationEngine.progress.value
        assertFalse(stoppedProgress.isRunning)
        assertTrue(stoppedProgress.isCancelled)
    }
}
