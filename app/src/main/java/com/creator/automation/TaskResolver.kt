package com.creator.automation

import android.util.Log

data class TaskResolution(
    val taskRecord: TaskRecord,
    val source: TaskSource,
    val resolutionReason: ResolutionReason,
    val localWorkflow: Workflow? = null,
    val learnedWorkflow: LearnedWorkflow? = null,
    val reasoningPlan: ReasoningPlan? = null
)

class TaskResolver(
    private val learnedWorkflowDao: LearnedWorkflowDao,
    private val reasoningProvider: ReasoningProvider = ChatGPTReasoningProvider()
) {

    companion object {
        private const val TAG = "TaskResolver"
    }

    suspend fun resolveTask(
        taskDescription: String,
        currentSnapshot: UiSnapshot? = null
    ): TaskResolution {
        Log.i(TAG, "RESOLVING_TASK: '$taskDescription'")
        val taskRecord = TaskRecord(description = taskDescription)
        val descLower = taskDescription.lowercase()

        // 1. Check for matching LearnedWorkflow
        if (currentSnapshot != null) {
            val stateSig = StateSignatureGenerator.generateSignature(currentSnapshot)
            val learnedCandidates = learnedWorkflowDao.getWorkflowsForStartingState(stateSig)
            if (learnedCandidates.isNotEmpty()) {
                val match = learnedCandidates.first()
                Log.i(TAG, "TASK_RESOLVED: LEARNED_WORKFLOW_MATCH -> '${match.name}'")
                return TaskResolution(
                    taskRecord = taskRecord.copy(
                        status = TaskStatus.EXECUTING.name,
                        source = TaskSource.LEARNED_WORKFLOW.name,
                        resolutionReason = ResolutionReason.LEARNED_WORKFLOW_MATCH.name
                    ),
                    source = TaskSource.LEARNED_WORKFLOW,
                    resolutionReason = ResolutionReason.LEARNED_WORKFLOW_MATCH,
                    learnedWorkflow = match
                )
            }
        }

        // 2. Check for matching DefaultWorkflows
        val defaultMatch = DefaultWorkflows.getAllWorkflows().firstOrNull {
            it.name.lowercase().contains(descLower) || descLower.contains("studio") || descLower.contains("analytics")
        }
        if (defaultMatch != null) {
            Log.i(TAG, "TASK_RESOLVED: LOCAL_WORKFLOW_MATCH -> '${defaultMatch.name}'")
            return TaskResolution(
                taskRecord = taskRecord.copy(
                    status = TaskStatus.EXECUTING.name,
                    source = TaskSource.LOCAL_RULE.name,
                    resolutionReason = ResolutionReason.LOCAL_WORKFLOW_MATCH.name
                ),
                source = TaskSource.LOCAL_RULE,
                resolutionReason = ResolutionReason.LOCAL_WORKFLOW_MATCH,
                localWorkflow = defaultMatch
            )
        }

        // 3. Fallback to ReasoningProvider (ChatGPT)
        Log.i(TAG, "TASK_FALLBACK: Routing task '$taskDescription' to ReasoningProvider (${reasoningProvider.getProviderName()})")
        val req = ReasoningRequest(
            taskDescription = taskDescription,
            currentApp = currentSnapshot?.packageName ?: "unknown",
            currentStateSignature = currentSnapshot?.let { StateSignatureGenerator.generateSignature(it) } ?: "none",
            visibleUISummary = currentSnapshot?.visibleTexts?.take(10)?.joinToString("; ") ?: "no UI",
            availableActionTypes = ActionType.values().map { it.name }
        )

        val plan = reasoningProvider.requestReasoning(req)

        return if (plan != null && plan.isValid) {
            Log.i(TAG, "TASK_RESOLVED: CHATGPT_FALLBACK -> Plan generated with ${plan.steps.size} steps")
            TaskResolution(
                taskRecord = taskRecord.copy(
                    status = TaskStatus.EXECUTING.name,
                    source = TaskSource.CHATGPT.name,
                    resolutionReason = ResolutionReason.CHATGPT_FALLBACK.name,
                    totalSteps = plan.steps.size
                ),
                source = TaskSource.CHATGPT,
                resolutionReason = ResolutionReason.CHATGPT_FALLBACK,
                reasoningPlan = plan
            )
        } else {
            Log.w(TAG, "TASK_UNRESOLVED: ReasoningProvider failed or network unavailable")
            TaskResolution(
                taskRecord = taskRecord.copy(
                    status = TaskStatus.PAUSED.name,
                    source = TaskSource.USER.name,
                    resolutionReason = ResolutionReason.CHATGPT_UNAVAILABLE.name
                ),
                source = TaskSource.USER,
                resolutionReason = ResolutionReason.CHATGPT_UNAVAILABLE
            )
        }
    }
}
