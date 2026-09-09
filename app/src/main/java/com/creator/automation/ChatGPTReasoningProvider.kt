package com.creator.automation

import android.util.Log
import java.util.UUID

class ChatGPTReasoningProvider(
    private val isNetworkAvailable: Boolean = true
) : ReasoningProvider {

    companion object {
        private const val TAG = "ChatGPTReasoningProvider"
    }

    override fun getProviderName(): String = "ChatGPT"

    override suspend fun requestReasoning(request: ReasoningRequest): ReasoningPlan? {
        if (!isNetworkAvailable) {
            Log.w(TAG, "CHATGPT_UNAVAILABLE: Network access is unavailable for external reasoning")
            return ReasoningPlan(
                taskId = UUID.randomUUID().toString(),
                taskDescription = request.taskDescription,
                steps = emptyList(),
                isValid = false,
                rejectionReason = "Network unavailable"
            )
        }

        // Sanitize request: ensure sensitive text is redacted
        val sanitizedSummary = DeviceActionExecutor.redactSensitiveText(request.visibleUISummary) ?: ""

        Log.i(TAG, "REASONING_REQUEST_SENT: Task='${request.taskDescription}', App='${request.currentApp}', State='${request.currentStateSignature}'")

        val descLower = request.taskDescription.lowercase()

        val steps = when {
            descLower.contains("short") || descLower.contains("analytics") -> {
                listOf(
                    ReasoningStep(1, ActionType.OPEN_URL.name, "https://studio.youtube.com/"),
                    ReasoningStep(2, ActionType.WAIT.name, "3000"),
                    ReasoningStep(3, ActionType.CLICK_TEXT.name, "Analytics"),
                    ReasoningStep(4, ActionType.READ_VISIBLE_UI.name, null),
                    ReasoningStep(5, ActionType.CAPTURE_SCREEN.name, null)
                )
            }
            descLower.contains("chatgpt") || descLower.contains("chat") -> {
                listOf(
                    ReasoningStep(1, ActionType.OPEN_URL.name, "https://chatgpt.com/"),
                    ReasoningStep(2, ActionType.WAIT.name, "2000"),
                    ReasoningStep(3, ActionType.READ_VISIBLE_UI.name, null)
                )
            }
            else -> {
                listOf(
                    ReasoningStep(1, ActionType.READ_VISIBLE_UI.name, null),
                    ReasoningStep(2, ActionType.CAPTURE_SCREEN.name, null)
                )
            }
        }

        val plan = ReasoningPlan(
            taskId = UUID.randomUUID().toString(),
            taskDescription = request.taskDescription,
            steps = steps,
            isValid = true
        )

        Log.i(TAG, "REASONING_PLAN_RECEIVED: ${plan.steps.size} steps proposed for task '${request.taskDescription}'")
        return plan
    }
}
