package com.creator.automation

import android.util.Log

enum class StuckType {
    NONE,
    REPEATED_STATE_NO_PROGRESS,
    REPEATED_ACTION_LOOP,
    MAX_STEPS_EXCEEDED
}

data class StuckEvaluation(
    val isStuck: Boolean,
    val type: StuckType = StuckType.NONE,
    val explanation: String = "Normal progress"
)

data class ExecutionStepRecord(
    val stateSignature: String,
    val actionType: String,
    val targetIdentifier: String?,
    val timestamp: Long = System.currentTimeMillis()
)

class StuckDetector(
    private val maxStateRepetitions: Int = 3,
    private val maxActionRepetitions: Int = 3,
    private val maxTotalSteps: Int = 20
) {

    companion object {
        private const val TAG = "StuckDetector"
    }

    private val history = mutableListOf<ExecutionStepRecord>()

    fun recordStep(
        stateSignature: String,
        actionType: String,
        targetIdentifier: String?
    ): StuckEvaluation {
        val record = ExecutionStepRecord(stateSignature, actionType, targetIdentifier)
        history.add(record)

        if (history.size > maxTotalSteps) {
            Log.w(TAG, "STUCK_DETECTED: Maximum execution steps ($maxTotalSteps) exceeded")
            return StuckEvaluation(
                isStuck = true,
                type = StuckType.MAX_STEPS_EXCEEDED,
                explanation = "Execution exceeded maximum step limit ($maxTotalSteps steps)"
            )
        }

        // Check for repeated state signature without progress
        if (history.size >= maxStateRepetitions) {
            val recentStates = history.takeLast(maxStateRepetitions).map { it.stateSignature }
            if (recentStates.all { it == stateSignature }) {
                Log.w(TAG, "STUCK_DETECTED: State signature '$stateSignature' repeated $maxStateRepetitions times without UI progress")
                return StuckEvaluation(
                    isStuck = true,
                    type = StuckType.REPEATED_STATE_NO_PROGRESS,
                    explanation = "UI state signature repeated $maxStateRepetitions times without progress"
                )
            }
        }

        // Check for repeated action loop
        if (history.size >= maxActionRepetitions) {
            val recentActions = history.takeLast(maxActionRepetitions).map { "${it.actionType}_${it.targetIdentifier}" }
            val currentActionKey = "${actionType}_${targetIdentifier}"
            if (recentActions.all { it == currentActionKey }) {
                Log.w(TAG, "STUCK_DETECTED: Action '$currentActionKey' repeated $maxActionRepetitions times in a row")
                return StuckEvaluation(
                    isStuck = true,
                    type = StuckType.REPEATED_ACTION_LOOP,
                    explanation = "Action '$actionType ($targetIdentifier)' repeated $maxActionRepetitions times continuously"
                )
            }
        }

        return StuckEvaluation(isStuck = false)
    }

    fun reset() {
        history.clear()
        Log.i(TAG, "STUCK_DETECTOR_RESET: Cleared step history")
    }
}
