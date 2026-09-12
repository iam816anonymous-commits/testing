package com.creator.automation

import android.content.Context
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
    private val reasoningProvider: ReasoningProvider = ChatGPTReasoningProvider(),
    private val appResolver: AppResolver? = null
) {

    companion object {
        private const val TAG = "TaskResolver"

        private val SEARCH_KEYWORDS = listOf("search for", "search", "find", "type", "look for")
    }

    suspend fun resolveTask(
        taskDescription: String,
        currentSnapshot: UiSnapshot? = null
    ): TaskResolution {
        Log.i(TAG, "RESOLVING_TASK: '$taskDescription'")
        val taskRecord = TaskRecord(description = taskDescription)
        val descLower = taskDescription.trim().lowercase()

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

        // 2. Check for exact matching DefaultWorkflows (e.g. YouTube Studio workflow)
        val defaultMatch = DefaultWorkflows.getAllWorkflows().firstOrNull {
            it.name.lowercase() == descLower || (descLower.contains("studio") && it.id == DefaultWorkflows.youtubeStudioReadOnlyWorkflow.id)
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

        // 3. Generic Unseen App / Action Resolution (No pre-recorded workflow required!)
        val genericWorkflow = generateGenericWorkflowForTask(taskDescription)
        if (genericWorkflow != null) {
            Log.i(TAG, "TASK_RESOLVED: GENERIC_ANDROID_RESOLUTION -> Generated workflow '${genericWorkflow.name}' with ${genericWorkflow.steps.size} steps")
            return TaskResolution(
                taskRecord = taskRecord.copy(
                    status = TaskStatus.EXECUTING.name,
                    source = TaskSource.LOCAL_RULE.name,
                    resolutionReason = ResolutionReason.LOCAL_WORKFLOW_MATCH.name,
                    totalSteps = genericWorkflow.steps.size
                ),
                source = TaskSource.LOCAL_RULE,
                resolutionReason = ResolutionReason.LOCAL_WORKFLOW_MATCH,
                localWorkflow = genericWorkflow
            )
        }

        // 4. Fallback to ReasoningProvider (ChatGPT)
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

    /**
     * Generates a generic multi-step workflow dynamically for unseen tasks like:
     * "Open Chrome and search for new Telugu movies", "Open WhatsApp", "Open Settings and turn on Wi-Fi"
     */
    fun generateGenericWorkflowForTask(taskDescription: String): Workflow? {
        val descLower = taskDescription.trim().lowercase()

        // 1. Direct System Navigation Commands (GO_HOME, GO_BACK, PRESS_RECENTS)
        if (descLower == "go home" || descLower == "press home" || descLower == "home" || descLower == "navigate home") {
            return Workflow(
                id = "system_home_${System.currentTimeMillis()}",
                name = "System Home Navigation",
                steps = listOf(
                    WorkflowStep(
                        id = "step_1",
                        action = AutomationAction(type = ActionType.PRESS_HOME, semantics = ActionSemantics.REPEATABLE)
                    )
                )
            )
        }

        if (descLower == "go back" || descLower == "press back" || descLower == "back" || descLower == "navigate back") {
            return Workflow(
                id = "system_back_${System.currentTimeMillis()}",
                name = "System Back Navigation",
                steps = listOf(
                    WorkflowStep(
                        id = "step_1",
                        action = AutomationAction(type = ActionType.GO_BACK, semantics = ActionSemantics.REPEATABLE)
                    )
                )
            )
        }

        if (descLower == "recents" || descLower == "open recents" || descLower == "press recents" || descLower == "app switcher") {
            return Workflow(
                id = "system_recents_${System.currentTimeMillis()}",
                name = "System Recents Navigation",
                steps = listOf(
                    WorkflowStep(
                        id = "step_1",
                        action = AutomationAction(type = ActionType.PRESS_RECENTS, semantics = ActionSemantics.REPEATABLE)
                    )
                )
            )
        }

        if (descLower.contains("read") && (descLower.contains("screen") || descLower.contains("ui"))) {
            return Workflow(
                id = "system_read_screen_${System.currentTimeMillis()}",
                name = "Read Screen Perception",
                steps = listOf(
                    WorkflowStep(
                        id = "step_1",
                        action = AutomationAction(type = ActionType.READ_VISIBLE_UI, semantics = ActionSemantics.READ_ONLY)
                    )
                )
            )
        }

        // 2. Direct Pure Typing Task ("type hello", "enter text john")
        if (descLower.startsWith("type ") || descLower.startsWith("enter text ")) {
            val textToInput = taskDescription.substring(taskDescription.indexOf(" ") + 1).trim()
            return Workflow(
                id = "type_only_${System.currentTimeMillis()}",
                name = "Pure Text Input Task",
                steps = listOf(
                    WorkflowStep(
                        id = "step_1",
                        action = AutomationAction(
                            type = ActionType.TYPE_TEXT,
                            inputData = textToInput,
                            semantics = ActionSemantics.REPEATABLE
                        )
                    ),
                    WorkflowStep(
                        id = "step_2",
                        action = AutomationAction(
                            type = ActionType.READ_VISIBLE_UI,
                            semantics = ActionSemantics.READ_ONLY
                        )
                    )
                )
            )
        }

        // 3. Extract app name query (e.g. "open chrome and search...", "launch whatsapp", "open youtube", "open settings")
        var targetAppName: String? = null
        if (descLower.startsWith("open ") || descLower.startsWith("launch ")) {
            val afterVerb = taskDescription.substring(descLower.indexOf(" ") + 1).trim()
            val words = afterVerb.split(" ")
            targetAppName = if (words.size > 1 && words[0].lowercase() == "google") {
                "${words[0]} ${words[1]}"
            } else if (words.isNotEmpty()) {
                words[0]
            } else null
        } else if (descLower == "settings") {
            targetAppName = "settings"
        }

        if (targetAppName.isNullOrBlank()) return null

        // Check if AppResolver can resolve app
        val resolvedApp = appResolver?.resolveApplication(targetAppName)
        val targetPackage = resolvedApp?.packageName

        val steps = mutableListOf<WorkflowStep>()
        var stepIdCounter = 1

        val launchWait = if (!targetPackage.isNullOrBlank()) {
            WaitCondition(
                type = WaitConditionType.WAIT_FOR_PACKAGE,
                expectedValue = targetPackage,
                timeoutMs = 10000L
            )
        } else {
            WaitCondition(
                type = WaitConditionType.WAIT_FOR_STATE_CHANGE,
                timeoutMs = 10000L
            )
        }

        // Step 1: LAUNCH_APP
        steps.add(
            WorkflowStep(
                id = "step_${stepIdCounter++}",
                action = AutomationAction(
                    type = ActionType.LAUNCH_APP,
                    targetValue = targetAppName,
                    timeoutMs = 10000L,
                    semantics = ActionSemantics.REPEATABLE,
                    waitCondition = launchWait
                )
            )
        )

        // Step 2: Extract search/type query if present
        var searchQuery: String? = null
        for (kw in SEARCH_KEYWORDS) {
            val kwIndex = descLower.indexOf(kw)
            if (kwIndex != -1) {
                searchQuery = taskDescription.substring(kwIndex + kw.length).trim()
                    .removePrefix("for").removePrefix("about").removePrefix(":").trim()
                break
            }
        }

        if (!searchQuery.isNullOrBlank()) {
            // Step 2a: TYPE_TEXT
            steps.add(
                WorkflowStep(
                    id = "step_${stepIdCounter++}",
                    action = AutomationAction(
                        type = ActionType.TYPE_TEXT,
                        targetValue = "Search",
                        inputData = searchQuery,
                        timeoutMs = 5000L,
                        semantics = ActionSemantics.REPEATABLE
                    )
                )
            )

            // Step 2b: SUBMIT_INPUT
            steps.add(
                WorkflowStep(
                    id = "step_${stepIdCounter++}",
                    action = AutomationAction(
                        type = ActionType.SUBMIT_INPUT,
                        timeoutMs = 5000L,
                        semantics = ActionSemantics.REPEATABLE,
                        waitCondition = WaitCondition(
                            type = WaitConditionType.WAIT_FOR_STATE_CHANGE,
                            timeoutMs = 5000L
                        )
                    )
                )
            )
        }

        // Final Step: READ_VISIBLE_UI
        steps.add(
            WorkflowStep(
                id = "step_${stepIdCounter}",
                action = AutomationAction(
                    type = ActionType.READ_VISIBLE_UI,
                    semantics = ActionSemantics.READ_ONLY
                )
            )
        )

        return Workflow(
            id = "generic_wf_${System.currentTimeMillis()}",
            name = "Generic Task Workflow: $taskDescription",
            targetPackage = targetPackage,
            steps = steps,
            timeoutMs = 30000L
        )
    }
}
