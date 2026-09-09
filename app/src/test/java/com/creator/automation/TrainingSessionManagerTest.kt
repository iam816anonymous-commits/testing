package com.creator.automation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class TrainingSessionManagerTest {

    @Test
    fun testTrainingSession_RecordAndDetectConflicts() = runTest {
        val dao = mock(LearnedWorkflowDao::class.java)
        val manager = TrainingSessionManager(dao)

        val sessionId = manager.startSession()
        assertNotNull(sessionId)
        assertTrue(TrainingSessionManager.isTrainingActive.value)

        val snapshotA = UiSnapshot(
            packageName = "com.google.android.apps.youtube.creator",
            visibleTexts = listOf("Get the app", "Continue to Studio")
        )

        manager.recordObservedAction(snapshotA, ActionType.CLICK_TEXT.name, "Continue to Studio")

        val snapshotB = UiSnapshot(
            packageName = "com.google.android.apps.youtube.creator",
            visibleTexts = listOf("Channel Analytics", "Dashboard")
        )
        manager.recordResultingState(snapshotB)

        val assembled = manager.stopSessionAndAssembleWorkflow("Test Studio Workflow")

        assertNotNull(assembled)
        assertFalse(TrainingSessionManager.isTrainingActive.value)
        assertEquals("Test Studio Workflow", assembled?.name)
    }

    @Test
    fun testDetectConflicts_DetectsMultipleTargetsForSameState() {
        val manager = TrainingSessionManager(mock(LearnedWorkflowDao::class.java))

        val transition1 = ObservedTransition(
            sequenceNumber = 1,
            beforeStateSignature = "sig123",
            packageName = "com.creator.automation",
            actionType = "CLICK_TEXT",
            targetText = "Option A"
        )
        val transition2 = ObservedTransition(
            sequenceNumber = 2,
            beforeStateSignature = "sig123",
            packageName = "com.creator.automation",
            actionType = "CLICK_TEXT",
            targetText = "Option B"
        )

        val hasConflict = manager.detectConflicts(listOf(transition1, transition2))
        assertTrue(hasConflict)
    }
}
