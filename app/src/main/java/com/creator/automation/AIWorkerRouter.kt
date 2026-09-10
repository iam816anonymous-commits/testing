package com.creator.automation

import android.content.Context
import android.util.Log

class AIWorkerRouter(
    private val context: Context,
    private val primaryProvider: ReasoningProvider = ChatGPTReasoningProvider(),
    private val secondaryProvider: ReasoningProvider = GeminiReasoningProvider(),
    private val taskDecisionEngine: TaskDecisionEngine = TaskDecisionEngine(context)
) {

    companion object {
        private const val TAG = "AIWorkerRouter"
    }

    suspend fun routeTaskReasoning(
        goal: GoalModel,
        worldState: WorldState,
        capabilities: List<AutomationCapabilitySnapshot>
    ): ActionDecision {
        val availableActions = capabilities.flatMap { it.availableCapabilities }
            .ifEmpty { ActionType.values().map { it.name } }
            .distinct()

        val request = ReasoningRequest(
            taskDescription = goal.rawUserIntent,
            currentApp = worldState.packageName,
            currentStateSignature = worldState.screenSignature,
            visibleUISummary = worldState.visibleTexts.take(8).joinToString("; "),
            availableActionTypes = availableActions
        )

        // 1. Try Primary Provider (e.g. ChatGPT)
        if (primaryProvider.isConfigured()) {
            try {
                val plan = primaryProvider.requestReasoning(request)
                if (plan != null && plan.isValid && plan.steps.isNotEmpty()) {
                    val firstStep = plan.steps.first()
                    val actionEnum = try { ActionType.valueOf(firstStep.actionType) } catch (e: Exception) { ActionType.READ_VISIBLE_UI }
                    Log.i(TAG, "ROUTED_PRIMARY: ${primaryProvider.getProviderName()} proposed step $actionEnum")
                    return ActionDecision(
                        actionType = actionEnum,
                        target = firstStep.targetHint,
                        reason = "Plan from ${primaryProvider.getProviderName()}",
                        confidence = 0.90
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Primary provider ${primaryProvider.getProviderName()} failed: ${e.message}")
            }
        }

        // 2. Try Secondary Provider (e.g. Gemini)
        if (secondaryProvider.isConfigured()) {
            try {
                val plan = secondaryProvider.requestReasoning(request)
                if (plan != null && plan.isValid && plan.steps.isNotEmpty()) {
                    val firstStep = plan.steps.first()
                    val actionEnum = try { ActionType.valueOf(firstStep.actionType) } catch (e: Exception) { ActionType.READ_VISIBLE_UI }
                    Log.i(TAG, "ROUTED_SECONDARY: ${secondaryProvider.getProviderName()} proposed step $actionEnum")
                    return ActionDecision(
                        actionType = actionEnum,
                        target = firstStep.targetHint,
                        reason = "Plan from ${secondaryProvider.getProviderName()}",
                        confidence = 0.85
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Secondary provider ${secondaryProvider.getProviderName()} failed: ${e.message}")
            }
        }

        // 3. Fallback to Local Deterministic TaskDecisionEngine
        Log.i(TAG, "ROUTED_FALLBACK: Routing to local deterministic TaskDecisionEngine")
        return taskDecisionEngine.decideNextAction(goal, worldState)
    }
}
