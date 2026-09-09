package com.creator.automation

enum class GateRejectionReason {
    NONE,
    AUTONOMOUS_DISABLED,
    STATE_UNKNOWN,
    NO_LEARNED_ACTION,
    LOW_CONFIDENCE,
    AMBIGUOUS_ACTION,
    TARGET_NOT_FOUND,
    VERIFICATION_UNAVAILABLE
}

data class GateEvaluation(
    val allowed: Boolean,
    val rejectionReason: GateRejectionReason = GateRejectionReason.NONE,
    val explanation: String
)

class AutonomousExecutionGate {

    fun evaluate(
        learningMode: LearningMode,
        globalAutonomousEnabled: Boolean,
        proposedDecision: ProposedDecision?
    ): GateEvaluation {

        if (!globalAutonomousEnabled) {
            return GateEvaluation(
                allowed = false,
                rejectionReason = GateRejectionReason.AUTONOMOUS_DISABLED,
                explanation = "Global autonomous replay is disabled by user switch."
            )
        }

        if (proposedDecision == null) {
            return GateEvaluation(
                allowed = false,
                rejectionReason = GateRejectionReason.NO_LEARNED_ACTION,
                explanation = "No learned decision available for current UI state."
            )
        }

        if (proposedDecision.isAmbiguous) {
            return GateEvaluation(
                allowed = false,
                rejectionReason = GateRejectionReason.AMBIGUOUS_ACTION,
                explanation = "Conflicting user demonstrations exist for current UI state."
            )
        }

        if (proposedDecision.action == null) {
            return GateEvaluation(
                allowed = false,
                rejectionReason = GateRejectionReason.NO_LEARNED_ACTION,
                explanation = "No valid action defined for proposed decision."
            )
        }

        if (proposedDecision.confidence != ConfidenceLevel.HIGH && proposedDecision.confidence != ConfidenceLevel.MEDIUM) {
            return GateEvaluation(
                allowed = false,
                rejectionReason = GateRejectionReason.LOW_CONFIDENCE,
                explanation = "Decision confidence level '${proposedDecision.confidence}' is insufficient for autonomous execution."
            )
        }

        return GateEvaluation(
            allowed = true,
            rejectionReason = GateRejectionReason.NONE,
            explanation = "Autonomous execution approved for target '${proposedDecision.record.targetText}' with confidence ${proposedDecision.confidence}."
        )
    }
}
