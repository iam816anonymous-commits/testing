package com.creator.automation

data class AIContext(
    val userGoal: String, // Trusted user intent
    val packageName: String,
    val visibleTexts: List<String>,
    val contentDescriptions: List<String>,
    val recentActionType: String? = null,
    val recentActionResult: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Sanitizes AI context by redacting sensitive user input (passwords, PINs, tokens, secrets).
         */
        fun createSanitized(
            userGoal: String,
            worldState: WorldState,
            lastActionType: String? = null,
            lastActionResult: String? = null
        ): AIContext {
            val sanitizedTexts = worldState.visibleTexts.map { DeviceActionExecutor.redactSensitiveText(it) ?: "" }
            val sanitizedDescs = worldState.contentDescriptions.map { DeviceActionExecutor.redactSensitiveText(it) ?: "" }

            return AIContext(
                userGoal = userGoal.trim(),
                packageName = worldState.packageName,
                visibleTexts = sanitizedTexts,
                contentDescriptions = sanitizedDescs,
                recentActionType = lastActionType,
                recentActionResult = lastActionResult
            )
        }
    }
}
