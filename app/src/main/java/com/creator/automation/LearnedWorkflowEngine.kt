package com.creator.automation

import android.content.Context
import android.util.Log

class LearnedWorkflowEngine(
    private val context: Context,
    private val learnedWorkflowDao: LearnedWorkflowDao = AppDatabase.getDatabase(context).learnedWorkflowDao(),
    private val deviceActionExecutor: DeviceActionExecutor = DeviceActionExecutor(context),
    private val autonomousGate: AutonomousExecutionGate = AutonomousExecutionGate()
) {

    companion object {
        private const val TAG = "LearnedWorkflowEngine"
    }

    suspend fun executeLearnedWorkflow(
        learnedWorkflow: LearnedWorkflow,
        service: AutomationAccessibilityService,
        trigger: ExecutionTrigger = ExecutionTrigger.MANUAL,
        globalAutonomousEnabled: Boolean = true
    ): ActionResult {
        Log.i(TAG, "LEARNED_WORKFLOW_STARTED: ${learnedWorkflow.name} (ID: ${learnedWorkflow.id})")

        val steps = learnedWorkflowDao.getStepsForWorkflow(learnedWorkflow.id)
        if (steps.isEmpty()) {
            Log.w(TAG, "LEARNED_WORKFLOW_EMPTY: Workflow ${learnedWorkflow.id} has no steps")
            return ActionResult(
                status = ActionResultStatus.FAILED,
                reason = ExecutionReason.UI_NOT_FOUND,
                trigger = trigger,
                message = "Learned workflow contains no steps."
            )
        }

        var lastSnapshot: UiSnapshot? = null

        // Execute steps sequentially using observe-act-verify loop
        for (step in steps) {
            // 1. OBSERVE current UI state before step
            val currentRoot = service.getRootNode()
            val currentSnapshot = ActionResolver.captureSnapshot(currentRoot, service.packageName ?: "")
            val currentStateSig = StateSignatureGenerator.generateSignature(currentSnapshot)
            lastSnapshot = currentSnapshot

            Log.i(TAG, "OBSERVED_STATE: Step ${step.sequenceNumber}/${steps.size} - Current State: $currentStateSig (Expected Before: ${step.beforeStateSignature})")

            // 2. VERIFY state match or recovery compatibility
            if (step.beforeStateSignature.isNotBlank() && currentStateSig != step.beforeStateSignature) {
                Log.w(TAG, "STATE_MISMATCH_PAUSED: Step ${step.sequenceNumber} state mismatch. Expected ${step.beforeStateSignature}, got $currentStateSig")

                // Update workflow failure statistics
                val updatedWorkflow = learnedWorkflow.copy(
                    failureCount = learnedWorkflow.failureCount + 1,
                    updatedAt = System.currentTimeMillis()
                )
                learnedWorkflowDao.insertWorkflow(updatedWorkflow)

                return ActionResult(
                    status = ActionResultStatus.BLOCKED,
                    reason = ExecutionReason.VERIFICATION_FAILED,
                    trigger = trigger,
                    message = "Workflow paused due to state signature mismatch at step ${step.sequenceNumber}",
                    snapshot = currentSnapshot
                )
            }

            // 3. ACT & VERIFY step via DeviceActionExecutor
            val actionTypeEnum = try {
                ActionType.valueOf(step.actionType)
            } catch (e: Exception) {
                ActionType.CLICK_TEXT
            }

            val automationAction = AutomationAction(
                type = actionTypeEnum,
                targetValue = step.targetIdentifier
            )

            val verificationAction = step.verificationExpectation?.let {
                AutomationAction(type = ActionType.VERIFY_TEXT, targetValue = it)
            }

            val stepResult = deviceActionExecutor.executeAndAudit(
                workflowId = learnedWorkflow.id,
                action = automationAction,
                service = service,
                trigger = trigger,
                verificationAction = verificationAction
            )

            if (stepResult.status != ActionResultStatus.SUCCESS) {
                Log.e(TAG, "STEP_FAILED: Step ${step.sequenceNumber} failed with result ${stepResult.status}")

                // Update step & workflow failure stats
                val updatedStep = step.copy(failureCount = step.failureCount + 1)
                learnedWorkflowDao.insertSteps(listOf(updatedStep))

                val updatedWorkflow = learnedWorkflow.copy(
                    failureCount = learnedWorkflow.failureCount + 1,
                    updatedAt = System.currentTimeMillis()
                )
                learnedWorkflowDao.insertWorkflow(updatedWorkflow)

                return ActionResult(
                    status = stepResult.status,
                    reason = stepResult.reason,
                    trigger = trigger,
                    message = "Learned workflow '${learnedWorkflow.name}' failed at step ${step.sequenceNumber}: ${stepResult.message}",
                    snapshot = stepResult.snapshot ?: currentSnapshot
                )
            }

            // Update step success stats
            val updatedStep = step.copy(successCount = step.successCount + 1)
            learnedWorkflowDao.insertSteps(listOf(updatedStep))
        }

        // Update workflow success statistics
        val updatedWorkflow = learnedWorkflow.copy(
            successCount = learnedWorkflow.successCount + 1,
            confidence = ConfidenceLevel.HIGH.name,
            updatedAt = System.currentTimeMillis()
        )
        learnedWorkflowDao.insertWorkflow(updatedWorkflow)

        Log.i(TAG, "LEARNED_WORKFLOW_COMPLETED: Successfully executed all ${steps.size} steps for '${learnedWorkflow.name}'")
        return ActionResult(
            status = ActionResultStatus.SUCCESS,
            reason = ExecutionReason.NONE,
            trigger = trigger,
            message = "Learned workflow '${learnedWorkflow.name}' executed successfully.",
            snapshot = lastSnapshot
        )
    }
}
