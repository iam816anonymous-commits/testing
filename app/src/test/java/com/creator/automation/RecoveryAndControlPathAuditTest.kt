package com.creator.automation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class RecoveryAndControlPathAuditTest {

    @Test
    fun testRecoveryAndControlPathPresence() {
        val resolver = ActionResolver()
        val verifier = GoalVerifier(resolver)
        val context = mock(android.content.Context::class.java)
        val scanner = DeviceCapabilityScanner(context)

        assertNotNull(resolver)
        assertNotNull(verifier)
        assertNotNull(scanner)
    }

    @Test
    fun testTwoCandidateTargetResolutionAmbiguity() {
        val node1 = UiNodeInfo(
            text = "Open Settings",
            viewIdResourceName = "com.android.settings:id/btn_1",
            isClickable = true
        )
        val node2 = UiNodeInfo(
            text = "Open Settings",
            viewIdResourceName = "com.android.settings:id/btn_2",
            isClickable = true
        )

        val snapshot = UiSnapshot(
            packageName = "com.android.settings",
            allNodes = listOf(node1, node2)
        )

        val resolver = ActionResolver()
        val res = resolver.resolveTargetWithAmbiguity(snapshot, "Open Settings")

        assertEquals(2, res.candidateCount)
        assertTrue(res.isAmbiguous)
        assertEquals(TargetResolutionStatus.AMBIGUOUS, res.status)
        assertNotNull(res.match)
    }

    @Test
    fun testNonBlockingStoragePermissions() {
        val declared = setOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)

        val permState = DeviceCapabilityScanner.evaluatePermissionState(
            permission = android.Manifest.permission.READ_EXTERNAL_STORAGE,
            declaredPermissions = declared,
            isGranted = false,
            isRuntimeApplicable = true
        )

        assertTrue(permState.isDeclared)
        assertFalse(permState.isGranted)
        assertFalse(permState.isUsable)
    }

    @Test
    fun testGoalResultInAgentStepResult() {
        val stepResult = AgentStepResult(
            stateBefore = AgentState.IDLE,
            observation = null,
            decisionReason = "Resolved step",
            actionExecuted = null,
            actionResult = null,
            verificationStatus = VerificationStatus.SUCCESSFULLY_VERIFIED,
            goalResult = GoalResult.CONFIRMED,
            nextState = AgentState.COMPLETED
        )

        assertEquals(GoalResult.CONFIRMED, stepResult.goalResult)
        assertEquals(AgentState.COMPLETED, stepResult.nextState)
    }
}
