package com.creator.automation

import android.util.Log

class RecoveryManager(
    private val maxRetriesPerStep: Int = 1
) {

    companion object {
        private const val TAG = "RecoveryManager"
    }

    fun evaluateRecovery(
        failedStepCount: Int,
        lastActionResult: ActionResult?,
        currentTask: TaskRecord?
    ): RecoveryOutcome {
        if (lastActionResult == null) {
            Log.w(TAG, "RECOVERY_PAUSE: No last action result available")
            return RecoveryOutcome.PAUSE
        }

        if (lastActionResult.reason == ExecutionReason.ACCESSIBILITY_DISABLED ||
            lastActionResult.reason == ExecutionReason.LOGIN_REQUIRED
        ) {
            Log.w(TAG, "RECOVERY_PAUSE: Critical blocked state '${lastActionResult.reason}'. Pausing task.")
            return RecoveryOutcome.PAUSE
        }

        if (failedStepCount < maxRetriesPerStep) {
            Log.i(TAG, "RECOVERY_RETRY: Failed step count ($failedStepCount) < maxRetries ($maxRetriesPerStep). Retrying.")
            return RecoveryOutcome.RETRY
        }

        Log.w(TAG, "RECOVERY_FAIL: Bounded retry limit reached ($failedStepCount). Transitioning to FAIL.")
        return RecoveryOutcome.FAIL
    }
}
