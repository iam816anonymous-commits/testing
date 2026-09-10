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
                // Single explicit commands: "Press Go", "Click Go"
                lower == "press go" || lower == "click go" -> {
                    requestedType = ActionType.CLICK_TEXT
                    requestedTarget = "Go"
                }
                // Single explicit commands: "Press Enter", "Submit"
                lower == "press enter" || lower == "submit" -> {
                    requestedType = ActionType.SUBMIT_INPUT
                }
                // Direct Type command: "Type <payload>" (case-insensitive substring)
                lower.startsWith("type ") -> {
                    requestedType = ActionType.TYPE_TEXT
                    expectedText = trimmed.substringAfter("type ", ignoreCase = true).trim()
                }
                // Compound: "... after typing <payload>" (e.g. "Press Go after typing hello")
                lower.contains("after typing ") -> {
                    expectedText = trimmed.substringAfter("after typing ", ignoreCase = true).trim()
                    if (lower.contains("press go") || lower.contains("click go")) {
                        requestedType = ActionType.SUBMIT_INPUT
                        requestedTarget = "Go"
                    } else if (lower.contains("press enter") || lower.contains("submit")) {
                        requestedType = ActionType.SUBMIT_INPUT
                    }
                }
                // Compound: "Search ... and press Go" / "Search ... and click Go"
                lower.contains("and press go") || lower.contains("and click go") -> {
                    val prefixPart = if (lower.contains("and press go")) {
                        trimmed.substringBefore("and press go", ignoreCase = true).trim()
                    } else {
                        trimmed.substringBefore("and click go", ignoreCase = true).trim()
                    }
                    expectedText = when {
                        prefixPart.startsWith("search for ", ignoreCase = true) -> prefixPart.substringAfter("search for ", ignoreCase = true).trim()
                        prefixPart.startsWith("search ", ignoreCase = true) -> prefixPart.substringAfter("search ", ignoreCase = true).trim()
                        else -> prefixPart
                    }
                    requestedType = ActionType.SUBMIT_INPUT
                    requestedTarget = "Go"
                }
                // Standard Search command: "Search for <payload>" / "Search <payload>" / "Query <payload>"
                lower.startsWith("search for ") || lower.startsWith("search ") || lower.startsWith("query ") -> {
                    expectedText = when {
                        lower.startsWith("search for ") -> trimmed.substringAfter("search for ", ignoreCase = true).trim()
                        lower.startsWith("search ") -> trimmed.substringAfter("search ", ignoreCase = true).trim()
                        else -> trimmed.substringAfter("query ", ignoreCase = true).trim()
                    }
                    requestedType = ActionType.SUBMIT_INPUT
                }
                else -> {
                    expectedText = when {
                        lower.contains("search for ") -> trimmed.substringAfter("search for ", ignoreCase = true).trim()
                        lower.contains("search ") -> trimmed.substringAfter("search ", ignoreCase = true).trim()
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
