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
    val nextState: AgentState
)

enum class RecoveryOutcome {
    RETRY,
    RESUME,
    PAUSE,
    FAIL
}
