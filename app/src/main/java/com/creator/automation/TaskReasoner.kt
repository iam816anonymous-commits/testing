package com.creator.automation

import android.content.Context
import android.util.Log

class TaskReasoner(
    private val context: Context,
    private val deviceActionExecutor: DeviceActionExecutor = DeviceActionExecutor(context),
    private val learnedWorkflowDao: LearnedWorkflowDao = AppDatabase.getDatabase(context).learnedWorkflowDao()
) {

    companion object {
        private const val TAG = "TaskReasoner"

        private val PROHIBITED_KEYWORDS = listOf(
            "password", "secret", "token", "credit_card", "pin", "cvv"
        )
    }

    /**
     * Validates an external ReasoningPlan for structure, unsupported actions, and sensitive operations.
     */
    fun validatePlan(plan: ReasoningPlan): PlanValidation {
        if (!plan.isValid) {
            return PlanValidation(isValid = false, rejectionReason = plan.rejectionReason ?: "Plan marked invalid")
        }

        if (plan.steps.isEmpty()) {
            return PlanValidation(isValid = false, rejectionReason = "Plan contains no steps")
        }

        for (step in plan.steps) {
            val actionTypeEnum = try {
                ActionType.valueOf(step.actionType)
            } catch (e: Exception) {
                return PlanValidation(
                    isValid = false,
                    rejectionReason = "Step ${step.sequenceNumber} contains unknown action type '${step.actionType}'"
                )
            }

            // Reject prohibited sensitive parameters
            val hintLower = step.targetHint?.lowercase() ?: ""
            if (PROHIBITED_KEYWORDS.any { hintLower.contains(it) }) {
                return PlanValidation(
                    isValid = false,
                    rejectionReason = "Step ${step.sequenceNumber} target '${step.targetHint}' contains prohibited sensitive keyword"
                )
            }
        }

        return PlanValidation(isValid = true)
    }

    /**
     * Executes a validated ReasoningPlan step-by-step using observe-act-verify loop
     * and converts successful executions into candidate learned workflows.
     */
    suspend fun executeReasoningPlan(
        plan: ReasoningPlan,
        service: AutomationAccessibilityService,
        trigger: ExecutionTrigger = ExecutionTrigger.MANUAL
    ): ActionResult {
        val validation = validatePlan(plan)
        if (!validation.isValid) {
            Log.e(TAG, "PLAN_REJECTED: ${validation.rejectionReason}")
            return ActionResult(
                status = ActionResultStatus.FAILED,
                reason = ExecutionReason.VERIFICATION_FAILED,
                trigger = trigger,
                message = "Plan validation failed: ${validation.rejectionReason}"
            )
        }

        Log.i(TAG, "EXECUTING_REASONING_PLAN: ${plan.steps.size} steps for '${plan.taskDescription}'")

        val transitions = mutableListOf<ObservedTransition>()
        var lastSnapshot: UiSnapshot? = null

        for (step in plan.steps) {
            // 1. OBSERVE
            val beforeRoot = service.getRootNode()
            val beforeSnapshot = ActionResolver.captureSnapshot(beforeRoot, service.packageName ?: "")
            val beforeSig = StateSignatureGenerator.generateSignature(beforeSnapshot)

            val actionEnum = ActionType.valueOf(step.actionType)
            val action = AutomationAction(type = actionEnum, targetValue = step.targetHint)

            // 2. ACT & VERIFY
            val stepResult = deviceActionExecutor.executeAndAudit(
                workflowId = "chatgpt_task_${plan.taskId}",
                action = action,
                service = service,
                trigger = trigger
            )

            // 3. OBSERVE AGAIN
            val afterRoot = service.getRootNode()
            val afterSnapshot = ActionResolver.captureSnapshot(afterRoot, service.packageName ?: "")
            val afterSig = StateSignatureGenerator.generateSignature(afterSnapshot)
            lastSnapshot = afterSnapshot

            if (stepResult.status != ActionResultStatus.SUCCESS) {
                Log.e(TAG, "REASONING_STEP_FAILED: Step ${step.sequenceNumber} failed: ${stepResult.message}")
                return ActionResult(
                    status = stepResult.status,
                    reason = stepResult.reason,
                    trigger = trigger,
                    message = "Reasoning step ${step.sequenceNumber} failed: ${stepResult.message}",
                    snapshot = afterSnapshot
                )
            }

            transitions.add(
                ObservedTransition(
                    sequenceNumber = step.sequenceNumber,
                    beforeStateSignature = beforeSig,
                    packageName = beforeSnapshot.packageName,
                    actionType = step.actionType,
                    targetText = step.targetHint,
                    afterStateSignature = afterSig
                )
            )
        }

        // Convert successful ChatGPT execution into a CANDIDATE LearnedWorkflow
        if (transitions.isNotEmpty()) {
            val workflowId = java.util.UUID.randomUUID().toString()
            val learnedWf = LearnedWorkflow(
                id = workflowId,
                name = "Learned: ${plan.taskDescription}",
                description = "Taught by ChatGPT reasoning plan",
                startingStateSignature = transitions.first().beforeStateSignature,
                status = LearnedWorkflowStatus.CANDIDATE.name,
                confidence = ConfidenceLevel.CANDIDATE.name,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            val learnedSteps = transitions.map { trans ->
                LearnedWorkflowStep(
                    workflowId = workflowId,
                    sequenceNumber = trans.sequenceNumber,
                    beforeStateSignature = trans.beforeStateSignature,
                    actionType = trans.actionType,
                    targetIdentifier = trans.targetText,
                    afterStateSignature = trans.afterStateSignature,
                    confidence = ConfidenceLevel.CANDIDATE.name
                )
            }

            learnedWorkflowDao.insertWorkflow(learnedWf)
            learnedWorkflowDao.insertSteps(learnedSteps)

            Log.i(TAG, "CHATGPT_PLAN_LEARNED: Saved candidate LearnedWorkflow '${learnedWf.name}' with ${learnedSteps.size} steps")
        }

        return ActionResult(
            status = ActionResultStatus.SUCCESS,
            reason = ExecutionReason.NONE,
            trigger = trigger,
            message = "Successfully executed and learned ChatGPT plan for '${plan.taskDescription}'",
            snapshot = lastSnapshot
        )
    }
}

data class PlanValidation(
    val isValid: Boolean,
    val rejectionReason: String? = null
)
