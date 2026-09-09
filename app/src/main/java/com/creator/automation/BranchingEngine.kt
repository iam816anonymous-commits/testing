package com.creator.automation

import android.util.Log

data class BranchDecision(
    val shouldBranch: Boolean,
    val targetStepId: String? = null,
    val branchTaken: String = "NONE"
)

class BranchingEngine {

    companion object {
        private const val TAG = "BranchingEngine"
    }

    fun evaluateBranch(
        condition: BranchCondition,
        snapshot: UiSnapshot?,
        screenObservation: CurrentObservation? = null
    ): BranchDecision {
        if (snapshot == null) {
            Log.w(TAG, "BRANCH_EVAL_NULL_SNAPSHOT: Cannot evaluate branch without UI snapshot")
            return if (condition.elseStepId != null) {
                BranchDecision(shouldBranch = true, targetStepId = condition.elseStepId, branchTaken = "ELSE")
            } else {
                BranchDecision(shouldBranch = false, branchTaken = "NONE")
            }
        }

        val expected = condition.ifExpectedValue.lowercase()

        val isConditionMet = when (condition.ifConditionType) {
            WaitConditionType.WAIT_FOR_TEXT -> {
                snapshot.visibleTexts.any { it.contains(expected, ignoreCase = true) } ||
                        snapshot.contentDescriptions.any { it.contains(expected, ignoreCase = true) }
            }
            WaitConditionType.WAIT_FOR_VIEW_ID -> {
                snapshot.viewIds.any { it.endsWith(expected, ignoreCase = true) }
            }
            WaitConditionType.WAIT_FOR_PACKAGE -> {
                snapshot.packageName.equals(expected, ignoreCase = true)
            }
            WaitConditionType.WAIT_FOR_STATE_SIGNATURE -> {
                StateSignatureGenerator.generateSignature(snapshot) == condition.ifExpectedValue
            }
            WaitConditionType.WAIT_FOR_VISUAL_CHANGE -> {
                screenObservation?.visualChangeState == VisualChangeResult.VISUAL_CHANGE.name
            }
            else -> false
        }

        return if (isConditionMet) {
            Log.i(TAG, "BRANCH_TAKEN: Condition '${condition.ifConditionType}' met -> THEN (${condition.thenStepId})")
            BranchDecision(shouldBranch = true, targetStepId = condition.thenStepId, branchTaken = "THEN")
        } else if (condition.elseStepId != null) {
            Log.i(TAG, "BRANCH_TAKEN: Condition '${condition.ifConditionType}' NOT met -> ELSE (${condition.elseStepId})")
            BranchDecision(shouldBranch = true, targetStepId = condition.elseStepId, branchTaken = "ELSE")
        } else {
            Log.i(TAG, "BRANCH_EVAL_FALSE: Condition NOT met, no ELSE step defined -> CONTINUING")
            BranchDecision(shouldBranch = false, branchTaken = "NONE")
        }
    }
}
