package com.creator.automation

enum class AgentState {
    IDLE,
    OBSERVING,
    RESOLVING,
    PLANNING,
    EXECUTING,
    VERIFYING,
    LEARNING,
    PAUSED,
    RECOVERING,
    COMPLETED,
    FAILED,
    CANCELLED,
    NEEDS_USER_INPUT
}

data class AgentStepResult(
    val stateBefore: AgentState,
    val observation: CurrentObservation?,
    val decisionReason: String?,
    val actionExecuted: AutomationAction?,
    val actionResult: ActionResult?,
    val verificationStatus: VerificationStatus,
    val goalResult: GoalResult = GoalResult.NOT_CONFIRMED,
    val nextState: AgentState
)

enum class GoalResult {
    CONFIRMED,
    NOT_CONFIRMED,
    VERIFICATION_UNAVAILABLE,
    FAILED,
    NEEDS_USER_INPUT,
    AMBIGUOUS
}

enum class RecoveryOutcome {
    RETRY,
    RESUME,
    PAUSE,
    FAIL
}
