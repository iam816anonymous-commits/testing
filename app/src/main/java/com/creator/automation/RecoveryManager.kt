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
        lastActionResult: ActionResult?
    ): RecoveryLevel {
        if (lastActionResult == null) return RecoveryLevel.USER_INTERVENTION

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
        currentTask: TaskRecord?
    ): RecoveryOutcome {
        val level = evaluateRecoveryLevel(failedStepCount, lastActionResult)
        Log.i(TAG, "RECOVERY_EVALUATION: Level=$level for reason=${lastActionResult?.reason}")

        return when (level) {
            RecoveryLevel.LOCAL_REPAIR -> RecoveryOutcome.RETRY
            RecoveryLevel.STRATEGY_REPAIR, RecoveryLevel.USER_INTERVENTION -> RecoveryOutcome.PAUSE
            RecoveryLevel.FAILURE -> RecoveryOutcome.FAIL
        }
    }
}
