package com.creator.automation

import java.util.UUID

class GeminiReasoningProvider(
    private val apiKey: String? = null
) : ReasoningProvider {

    override fun getProviderName(): String = "Gemini"

    override fun isConfigured(): Boolean = !apiKey.isNullOrBlank()

    override suspend fun requestReasoning(request: ReasoningRequest): ReasoningPlan? {
        if (!isConfigured()) {
            return ReasoningPlan(
                taskId = UUID.randomUUID().toString(),
                taskDescription = request.taskDescription,
                steps = emptyList(),
                isValid = false,
                rejectionReason = "Gemini API key is unconfigured"
            )
        }

        val targetApp = when {
            request.taskDescription.lowercase().contains("chrome") -> "Chrome"
            request.taskDescription.lowercase().contains("youtube") -> "YouTube"
            else -> "Settings"
        }

        val steps = listOf(
            ReasoningStep(1, ActionType.LAUNCH_APP.name, targetApp),
            ReasoningStep(2, ActionType.READ_VISIBLE_UI.name, null)
        )

        return ReasoningPlan(
            taskId = UUID.randomUUID().toString(),
            taskDescription = request.taskDescription,
            steps = steps,
            isValid = true
        )
    }
}
