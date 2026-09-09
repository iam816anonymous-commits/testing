package com.creator.automation

import android.util.Log

enum class RecoveryLevel {
    LOCAL_REPAIR,
    STRATEGY_REPAIR,
    USER_INTERVENTION,
    FAILURE
}

class RecoveryManager(
    private val maxRetriesPerStep: Int = 1
) {

    companion object {
        private const val TAG = "RecoveryManager"
    }

    fun evaluateRecoveryLevel(
        failedStepCount: Int,
        lastActionResult: ActionResult?,
        actionSemantics: ActionSemantics = ActionSemantics.REPEATABLE
    ): RecoveryLevel {
        if (lastActionResult == null) return RecoveryLevel.USER_INTERVENTION

        // HIGH_RISK actions always require user intervention on failure
        if (actionSemantics == ActionSemantics.HIGH_RISK) {
            return RecoveryLevel.USER_INTERVENTION
        }

        // NON_IDEMPOTENT actions with uncertain or blocked status cannot be blindly retried
        if (actionSemantics == ActionSemantics.NON_IDEMPOTENT && lastActionResult.status != ActionResultStatus.SUCCESS) {
            return RecoveryLevel.USER_INTERVENTION
        }

        return when (lastActionResult.reason) {
            ExecutionReason.LOGIN_REQUIRED,
            ExecutionReason.ACCESSIBILITY_DISABLED,
            ExecutionReason.USER_REQUIRED,
            ExecutionReason.AMBIGUOUS_TARGET -> RecoveryLevel.USER_INTERVENTION

            ExecutionReason.PRECONDITION_FAILED,
            ExecutionReason.STUCK -> RecoveryLevel.STRATEGY_REPAIR

            else -> {
                if (failedStepCount < maxRetriesPerStep) RecoveryLevel.LOCAL_REPAIR else RecoveryLevel.FAILURE
            }
        }
    }

    fun evaluateRecovery(
        failedStepCount: Int,
        lastActionResult: ActionResult?,
        currentTask: TaskRecord? = null,
        actionSemantics: ActionSemantics = ActionSemantics.REPEATABLE
    ): RecoveryOutcome {
        val level = evaluateRecoveryLevel(failedStepCount, lastActionResult, actionSemantics)
        Log.i(TAG, "RECOVERY_EVALUATION: Level=$level for reason=${lastActionResult?.reason}, Semantics=$actionSemantics")

        return when (level) {
            RecoveryLevel.LOCAL_REPAIR -> RecoveryOutcome.RETRY
            RecoveryLevel.STRATEGY_REPAIR, RecoveryLevel.USER_INTERVENTION -> RecoveryOutcome.PAUSE
            RecoveryLevel.FAILURE -> RecoveryOutcome.FAIL
        }
    }
}
