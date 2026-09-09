package com.creator.automation

import org.junit.Assert.*
import org.junit.Test

class AutonomousExecutionGateTest {

    private val gate = AutonomousExecutionGate()

    @Test
    fun testEvaluate_AutonomousDisabled_Rejects() {
        val eval = gate.evaluate(
            learningMode = LearningMode.AUTONOMOUS,
            globalAutonomousEnabled = false,
            proposedDecision = null
        )

        assertFalse(eval.allowed)
        assertEquals(GateRejectionReason.AUTONOMOUS_DISABLED, eval.rejectionReason)
    }

    @Test
    fun testEvaluate_NoLearnedAction_Rejects() {
        val eval = gate.evaluate(
            learningMode = LearningMode.AUTONOMOUS,
            globalAutonomousEnabled = true,
            proposedDecision = null
        )

        assertFalse(eval.allowed)
        assertEquals(GateRejectionReason.NO_LEARNED_ACTION, eval.rejectionReason)
    }

    @Test
    fun testEvaluate_AmbiguousDecision_Rejects() {
        val record = DemonstrationRecord(
            packageName = "com.creator.automation",
            stateSignature = "sig123",
            actionType = ActionType.CLICK_TEXT.name,
            targetText = "Option A"
        )
        val decision = ProposedDecision(
            action = null,
            record = record,
            confidence = ConfidenceLevel.LOW,
            isAmbiguous = true,
            reason = "Conflicting choices"
        )

        val eval = gate.evaluate(
            learningMode = LearningMode.AUTONOMOUS,
            globalAutonomousEnabled = true,
            proposedDecision = decision
        )

        assertFalse(eval.allowed)
        assertEquals(GateRejectionReason.AMBIGUOUS_ACTION, eval.rejectionReason)
    }

    @Test
    fun testEvaluate_LowConfidence_Rejects() {
        val record = DemonstrationRecord(
            packageName = "com.creator.automation",
            stateSignature = "sig123",
            actionType = ActionType.CLICK_TEXT.name,
            targetText = "Option A"
        )
        val decision = ProposedDecision(
            action = AutomationAction(ActionType.CLICK_TEXT, "Option A"),
            record = record,
            confidence = ConfidenceLevel.CANDIDATE,
            isAmbiguous = false,
            reason = "Single demo"
        )

        val eval = gate.evaluate(
            learningMode = LearningMode.AUTONOMOUS,
            globalAutonomousEnabled = true,
            proposedDecision = decision
        )

        assertFalse(eval.allowed)
        assertEquals(GateRejectionReason.LOW_CONFIDENCE, eval.rejectionReason)
    }

    @Test
    fun testEvaluate_HighConfidence_Approved() {
        val record = DemonstrationRecord(
            packageName = "com.creator.automation",
            stateSignature = "sig123",
            actionType = ActionType.CLICK_TEXT.name,
            targetText = "Continue to Studio",
            confidenceLevel = ConfidenceLevel.HIGH.name,
            demonstrationCount = 5
        )
        val decision = ProposedDecision(
            action = AutomationAction(ActionType.CLICK_TEXT, "Continue to Studio"),
            record = record,
            confidence = ConfidenceLevel.HIGH,
            isAmbiguous = false,
            canAutoExecute = true,
            reason = "High confidence match"
        )

        val eval = gate.evaluate(
            learningMode = LearningMode.AUTONOMOUS,
            globalAutonomousEnabled = true,
            proposedDecision = decision
        )

        assertTrue(eval.allowed)
        assertEquals(GateRejectionReason.NONE, eval.rejectionReason)
    }
}
