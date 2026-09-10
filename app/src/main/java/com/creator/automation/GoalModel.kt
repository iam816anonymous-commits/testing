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
    val normalizedObjective: String = rawUserIntent,
    val targetAppQuery: String? = null,
    val expectedTextInResult: String? = null,
    val requestedActionType: ActionType? = null,
    val requestedActionTarget: String? = null,
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

            // Command Decomposition Rules
            var requestedType: ActionType? = null
            var requestedTarget: String? = null
            var expectedText: String? = null

            when {
                lower == "press go" || lower == "click go" -> {
                    requestedType = ActionType.CLICK_TEXT
                    requestedTarget = "Go"
                }
                lower == "press enter" || lower == "submit" -> {
                    requestedType = ActionType.SUBMIT_INPUT
                }
                lower.startsWith("type ") -> {
                    requestedType = ActionType.TYPE_TEXT
                    expectedText = trimmed.substringAfter("type ").trim()
                }
                lower.contains("and press go") || lower.contains("and click go") -> {
                    val searchPart = trimmed.substringBefore("and press go").substringBefore("and click go").trim()
                    expectedText = when {
                        searchPart.lowercase().contains("search for ") -> searchPart.substringAfter("search for ").trim()
                        searchPart.lowercase().contains("search ") -> searchPart.substringAfter("search ").trim()
                        else -> searchPart
                    }
                    requestedType = ActionType.SUBMIT_INPUT
                    requestedTarget = "Go"
                }
                else -> {
                    expectedText = when {
                        lower.contains("search for ") -> trimmed.substringAfter("search for ").trim()
                        lower.contains("search ") -> trimmed.substringAfter("search ").trim()
                        lower.contains("query ") -> trimmed.substringAfter("query ").trim()
                        else -> null
                    }
                }
            }

            return GoalModel(
                rawUserIntent = trimmed,
                normalizedObjective = trimmed,
                targetAppQuery = targetApp,
                expectedTextInResult = expectedText,
                requestedActionType = requestedType,
                requestedActionTarget = requestedTarget
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
