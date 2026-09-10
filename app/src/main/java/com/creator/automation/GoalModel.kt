package com.creator.automation

enum class GoalStatus {
    PARSED,
    IN_PROGRESS,
    VERIFYING,
    COMPLETED,
    FAILED,
    BLOCKED
}

data class GoalModel(
    val id: String = java.util.UUID.randomUUID().toString(),
    val rawUserIntent: String, // Trusted user intent
    val normalizedObjective: String,
    val targetAppQuery: String? = null,
    val expectedTextInResult: String? = null,
    val maxActionBudget: Int = 10,
    val currentActionCount: Int = 0,
    val status: GoalStatus = GoalStatus.PARSED,
    val failureReason: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Safely parses user natural language into a GoalModel, separating user intent from untrusted UI text.
         */
        fun parse(rawGoal: String): GoalModel {
            val trimmed = rawGoal.trim()
            val lower = trimmed.lowercase()

            val targetApp = when {
                lower.contains("chrome") -> "Chrome"
                lower.contains("youtube studio") -> "YouTube Studio"
                lower.contains("youtube") -> "YouTube"
                lower.contains("settings") -> "Settings"
                lower.contains("chatgpt") -> "ChatGPT"
                else -> null
            }

            val expectedText = when {
                lower.contains("search for") -> trimmed.substringAfter("search for").trim()
                lower.contains("search") -> trimmed.substringAfter("search").trim()
                lower.contains("query") -> trimmed.substringAfter("query").trim()
                else -> null
            }

            return GoalModel(
                rawUserIntent = trimmed,
                normalizedObjective = trimmed,
                targetAppQuery = targetApp,
                expectedTextInResult = expectedText
            )
        }
    }

    /**
     * Verifies if the goal is satisfied based on live WorldState observation.
     */
    fun isGoalSatisfied(worldState: WorldState): Boolean {
        if (!expectedTextInResult.isNullOrBlank()) {
            val foundInTexts = worldState.visibleTexts.any { it.contains(expectedTextInResult, ignoreCase = true) }
            val foundInDescs = worldState.contentDescriptions.any { it.contains(expectedTextInResult, ignoreCase = true) }
            return foundInTexts || foundInDescs
        }

        if (!targetAppQuery.isNullOrBlank()) {
            return worldState.packageName.contains(targetAppQuery, ignoreCase = true)
        }

        return worldState.freshness == WorldStateFreshness.OBSERVED
    }
}
