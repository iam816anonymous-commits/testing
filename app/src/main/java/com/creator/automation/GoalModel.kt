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
    val constraints: List<String> = emptyList(),
    val requiredCapabilities: List<String> = emptyList(),
    val requiredPermissions: List<String> = emptyList(),
    val sideEffectRisk: ActionSemantics = ActionSemantics.REPEATABLE,
    val subgoals: List<String> = emptyList(),
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

            // Extract constraints from raw intent
            val constraintList = mutableListOf<String>()
            if (lower.contains("don't send") || lower.contains("without sending")) constraintList.add("NO_SEND_MESSAGE")
            if (lower.contains("don't delete") || lower.contains("without deleting")) constraintList.add("NO_DELETE_DATA")
            if (lower.contains("don't purchase") || lower.contains("without buying")) constraintList.add("NO_PURCHASE")
            if (lower.contains("read only") || lower.contains("only read")) constraintList.add("READ_ONLY")

            // Extract required capabilities/permissions
            val caps = mutableListOf<String>()
            val perms = mutableListOf<String>()
            if (lower.contains("camera") || lower.contains("picture") || lower.contains("photo")) {
                caps.add("CAMERA_VISION")
                perms.add(android.Manifest.permission.CAMERA)
            }
            if (lower.contains("record") || lower.contains("audio") || lower.contains("microphone")) {
                perms.add(android.Manifest.permission.RECORD_AUDIO)
            }

            // 1. URL Navigation Intent: "go to google.com", "open https://example.com"
            if (lower.startsWith("go to ") || lower.contains("http://") || lower.contains("https://")) {
                val urlCandidate = if (lower.startsWith("go to ")) trimmed.substringAfterIgnoreCase("go to ").trim() else trimmed
                return GoalModel(
                    rawUserIntent = trimmed,
                    normalizedObjective = trimmed,
                    requestedActionType = ActionType.OPEN_URL,
                    requestedActionTarget = urlCandidate,
                    constraints = constraintList,
                    requiredCapabilities = caps,
                    requiredPermissions = perms,
                    sideEffectRisk = if (constraintList.isNotEmpty() || perms.isNotEmpty()) ActionSemantics.HIGH_RISK else ActionSemantics.REPEATABLE
                )
            }

            // Multi-step Compound Intent: "Open <App> and search for <Text>"
            if ((lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start ")) &&
                (lower.contains(" and search") || lower.contains(" and query"))) {
                val appPart = trimmed.substringAfterIgnoreCase("open ")
                    .substringAfterIgnoreCase("launch ")
                    .substringAfterIgnoreCase("start ")
                    .substringBeforeIgnoreCase(" and ").trim()

                val searchPart = if (lower.contains("search for ")) {
                    trimmed.substringAfterIgnoreCase("search for ").trim()
                } else if (lower.contains("search ")) {
                    trimmed.substringAfterIgnoreCase("search ").trim()
                } else {
                    trimmed.substringAfterIgnoreCase("query ").trim()
                }

                return GoalModel(
                    rawUserIntent = trimmed,
                    normalizedObjective = trimmed,
                    targetAppQuery = appPart,
                    expectedTextInResult = searchPart,
                    requestedActionType = null,
                    requestedActionTarget = null,
                    constraints = constraintList,
                    requiredCapabilities = caps,
                    requiredPermissions = perms,
                    sideEffectRisk = if (constraintList.isNotEmpty() || perms.isNotEmpty()) ActionSemantics.HIGH_RISK else ActionSemantics.REPEATABLE
                )
            }

            // 2. Generic App Launch Intent Extraction (no hardcoded app maps)
            val targetApp = when {
                lower.startsWith("open ") && !lower.contains(" in ") -> trimmed.substringAfterIgnoreCase("open ").trim()
                lower.startsWith("launch ") -> trimmed.substringAfterIgnoreCase("launch ").trim()
                lower.startsWith("start ") -> trimmed.substringAfterIgnoreCase("start ").trim()
                else -> null
            }

            // Command Decomposition Rules
            var requestedType: ActionType? = if (targetApp != null) ActionType.LAUNCH_APP else null
            var requestedTarget: String? = targetApp
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
                    expectedText = trimmed.substringAfterIgnoreCase("type ").trim()
                }
                // Compound: "... after typing <payload>" (e.g. "Press Go after typing hello")
                lower.contains("after typing ") -> {
                    expectedText = trimmed.substringAfterIgnoreCase("after typing ").trim()
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
                        trimmed.substringBeforeIgnoreCase("and press go").trim()
                    } else {
                        trimmed.substringBeforeIgnoreCase("and click go").trim()
                    }
                    expectedText = when {
                        prefixPart.lowercase().startsWith("search for ") -> prefixPart.substringAfterIgnoreCase("search for ").trim()
                        prefixPart.lowercase().startsWith("search ") -> prefixPart.substringAfterIgnoreCase("search ").trim()
                        else -> prefixPart
                    }
                    requestedType = ActionType.SUBMIT_INPUT
                    requestedTarget = "Go"
                }
                // Standard Search command: "Search for <payload>" / "Search <payload>" / "Query <payload>"
                lower.startsWith("search for ") || lower.startsWith("search ") || lower.startsWith("query ") -> {
                    expectedText = when {
                        lower.startsWith("search for ") -> trimmed.substringAfterIgnoreCase("search for ").trim()
                        lower.startsWith("search ") -> trimmed.substringAfterIgnoreCase("search ").trim()
                        else -> trimmed.substringAfterIgnoreCase("query ").trim()
                    }
                    requestedType = ActionType.SUBMIT_INPUT
                }
                else -> {
                    if (expectedText == null && targetApp == null) {
                        expectedText = when {
                            lower.contains("search for ") -> trimmed.substringAfterIgnoreCase("search for ").trim()
                            lower.contains("search ") -> trimmed.substringAfterIgnoreCase("search ").trim()
                            else -> null
                        }
                    }
                }
            }

            return GoalModel(
                rawUserIntent = trimmed,
                normalizedObjective = trimmed,
                targetAppQuery = targetApp,
                expectedTextInResult = expectedText,
                requestedActionType = requestedType,
                requestedActionTarget = requestedTarget,
                constraints = constraintList,
                requiredCapabilities = caps,
                requiredPermissions = perms,
                sideEffectRisk = if (constraintList.isNotEmpty() || perms.isNotEmpty()) ActionSemantics.HIGH_RISK else ActionSemantics.REPEATABLE
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

private fun String.substringAfterIgnoreCase(delimiter: String, missingDelimiterValue: String = this): String {
    val index = indexOf(delimiter, ignoreCase = true)
    return if (index == -1) missingDelimiterValue else substring(index + delimiter.length)
}

private fun String.substringBeforeIgnoreCase(delimiter: String, missingDelimiterValue: String = this): String {
    val index = indexOf(delimiter, ignoreCase = true)
    return if (index == -1) missingDelimiterValue else substring(0, index)
}
