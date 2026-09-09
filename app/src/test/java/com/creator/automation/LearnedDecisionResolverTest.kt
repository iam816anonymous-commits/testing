package com.creator.automation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class LearnedDecisionResolverTest {

    @Test
    fun testResolveDecision_SingleDemonstrationCandidateConfidence() = runTest {
        val dao = mock(DemonstrationDao::class.java)
        val resolver = LearnedDecisionResolver(dao)

        val snapshot = UiSnapshot(
            packageName = "com.google.android.apps.youtube.creator",
            visibleTexts = listOf("Get the app", "Continue to Studio")
        )
        val sig = StateSignatureGenerator.generateSignature(snapshot)

        val record = DemonstrationRecord(
            packageName = "com.google.android.apps.youtube.creator",
            stateSignature = sig,
            actionType = ActionType.CLICK_TEXT.name,
            targetText = "Continue to Studio",
            confidenceLevel = ConfidenceLevel.CANDIDATE.name,
            demonstrationCount = 1
        )

        `when`(dao.getRecordsForState(snapshot.packageName, sig)).thenReturn(listOf(record))

        val decision = resolver.resolveDecision(snapshot, globalAutonomousEnabled = true)

        assertNotNull(decision)
        assertEquals(ConfidenceLevel.CANDIDATE, decision?.confidence)
        assertFalse(decision?.canAutoExecute == true) // Single candidate does not auto execute
        assertEquals("Continue to Studio", decision?.action?.targetValue)
    }

    @Test
    fun testResolveDecision_MultipleDemonstrationsHighConfidence() = runTest {
        val dao = mock(DemonstrationDao::class.java)
        val resolver = LearnedDecisionResolver(dao)

        val snapshot = UiSnapshot(
            packageName = "com.google.android.apps.youtube.creator",
            visibleTexts = listOf("Get the app", "Continue to Studio")
        )
        val sig = StateSignatureGenerator.generateSignature(snapshot)

        val record = DemonstrationRecord(
            packageName = "com.google.android.apps.youtube.creator",
            stateSignature = sig,
            actionType = ActionType.CLICK_TEXT.name,
            targetText = "Continue to Studio",
            demonstrationCount = 3,
            successCount = 2,
            failureCount = 0
        )

        `when`(dao.getRecordsForState(snapshot.packageName, sig)).thenReturn(listOf(record))

        val decision = resolver.resolveDecision(snapshot, globalAutonomousEnabled = true)

        assertNotNull(decision)
        assertEquals(ConfidenceLevel.HIGH, decision?.confidence)
        assertTrue(decision?.canAutoExecute == true)
        assertEquals("Continue to Studio", decision?.action?.targetValue)
    }

    @Test
    fun testResolveDecision_ConflictingActionsReturnsAmbiguous() = runTest {
        val dao = mock(DemonstrationDao::class.java)
        val resolver = LearnedDecisionResolver(dao)

        val snapshot = UiSnapshot(
            packageName = "com.google.android.apps.youtube.creator",
            visibleTexts = listOf("Get the app", "Continue to Studio")
        )
        val sig = StateSignatureGenerator.generateSignature(snapshot)

        val record1 = DemonstrationRecord(
            packageName = "com.google.android.apps.youtube.creator",
            stateSignature = sig,
            actionType = ActionType.CLICK_TEXT.name,
            targetText = "Continue to Studio"
        )
        val record2 = DemonstrationRecord(
            packageName = "com.google.android.apps.youtube.creator",
            stateSignature = sig,
            actionType = ActionType.CLICK_TEXT.name,
            targetText = "Get the app"
        )

        `when`(dao.getRecordsForState(snapshot.packageName, sig)).thenReturn(listOf(record1, record2))

        val decision = resolver.resolveDecision(snapshot, globalAutonomousEnabled = true)

        assertNotNull(decision)
        assertTrue(decision?.isAmbiguous == true)
        assertNull(decision?.action)
    }
}
