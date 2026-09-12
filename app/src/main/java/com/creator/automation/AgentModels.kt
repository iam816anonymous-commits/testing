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

enum class ExecutionStage {
    ACTION_CREATED,
    TARGET_RESOLVED,
    ACTION_DISPATCH_STARTED,
    ACTION_DISPATCH_SUCCEEDED,
    ACTION_DISPATCH_FAILED,
    WAIT_STARTED,
    WAIT_CONDITION_SATISFIED,
    WAIT_TIMEOUT,
    OBSERVATION_STARTED,
    OBSERVATION_COMPLETED,
    TARGET_VERIFIED,
    GOAL_VERIFIED,
    GOAL_NOT_VERIFIED,
    RECOVERY_STARTED,
    RECOVERY_COMPLETED,
    RECOVERY_FAILED,
    TASK_COMPLETED,
    TASK_FAILED,
    TASK_CANCELLED
}

data class ExecutionTrace(
    val stage: ExecutionStage,
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String? = null,
    val details: String = ""
)

data class AgentStepResult(
    val stateBefore: AgentState,
    val observation: CurrentObservation?,
    val decisionReason: String?,
    val actionExecuted: AutomationAction?,
    val actionResult: ActionResult?,
    val verificationStatus: VerificationStatus,
    val nextState: AgentState,
    val traces: List<ExecutionTrace> = emptyList()
)

enum class RecoveryOutcome {
    RETRY,
    RESUME,
    PAUSE,
    FAIL
}
