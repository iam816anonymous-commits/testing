package com.creator.automation

import android.util.Log

enum class GoalVerificationStatus {
    GOAL_REACHED,
    GOAL_NOT_REACHED,
    PROGRESSING,
    BLOCKED,
    UNKNOWN,
    GOAL_VERIFIED,
    GOAL_TEXT_NOT_FOUND,
    WRONG_PACKAGE,
    AUTH_PROMPT_DETECTED,
    NO_SNAPSHOT
}

data class GoalVerificationResult(
    val status: GoalVerificationStatus,
    val isVerified: Boolean,
    val explanation: String
)

class GoalVerifier(
    private val actionResolver: ActionResolver = ActionResolver()
) {

    companion object {
        private const val TAG = "GoalVerifier"
    }

    fun verifyGoal(
        expectedGoalText: String?,
        expectedPackage: String?,
        snapshot: UiSnapshot?
    ): GoalVerificationResult {
        if (snapshot == null) {
            return GoalVerificationResult(
                status = GoalVerificationStatus.NO_SNAPSHOT,
                isVerified = false,
                explanation = "Cannot verify goal: UI snapshot is null"
            )
        }

        // 1. Check for Auth/Sign-in Block
        if (actionResolver.detectAuthState(snapshot) == AuthState.LOGIN_REQUIRED) {
            Log.w(TAG, "GOAL_VERIFY_BLOCKED: Sign-in prompt detected on current screen")
            return GoalVerificationResult(
                status = GoalVerificationStatus.AUTH_PROMPT_DETECTED,
                isVerified = false,
                explanation = "Goal verification blocked: Sign-in or login prompt detected"
            )
        }

        // 2. Package verification
        if (!expectedPackage.isNullOrBlank() && !snapshot.packageName.equals(expectedPackage, ignoreCase = true)) {
            Log.w(TAG, "GOAL_VERIFY_FAILED: Package mismatch (Expected '$expectedPackage', got '${snapshot.packageName}')")
            return GoalVerificationResult(
                status = GoalVerificationStatus.WRONG_PACKAGE,
                isVerified = false,
                explanation = "Current active package '${snapshot.packageName}' does not match expected '$expectedPackage'"
            )
        }

        // 3. Expected Goal Text Verification
        if (!expectedGoalText.isNullOrBlank()) {
            val goalLower = expectedGoalText.lowercase()
            val textFound = snapshot.visibleTexts.any { it.contains(goalLower, ignoreCase = true) } ||
                    snapshot.contentDescriptions.any { it.contains(goalLower, ignoreCase = true) }

            if (!textFound) {
                Log.w(TAG, "GOAL_VERIFY_FAILED: Expected goal text '$expectedGoalText' not present in visible UI")
                return GoalVerificationResult(
                    status = GoalVerificationStatus.GOAL_TEXT_NOT_FOUND,
                    isVerified = false,
                    explanation = "Expected goal text '$expectedGoalText' not found on current UI screen"
                )
            }
        }

        Log.i(TAG, "GOAL_VERIFIED: Successfully verified task/goal criteria")
        return GoalVerificationResult(
            status = GoalVerificationStatus.GOAL_VERIFIED,
            isVerified = true,
            explanation = "Task goal criteria verified successfully on current screen"
        )
    }
}
