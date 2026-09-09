package com.creator.automation

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay

class WorkflowEngine(
    private val context: Context,
    private val actionResolver: ActionResolver = ActionResolver(),
    private val learnedDecisionResolver: LearnedDecisionResolver = LearnedDecisionResolver(
        AppDatabase.getDatabase(context).demonstrationDao()
    ),
    private val deviceActionExecutor: DeviceActionExecutor = DeviceActionExecutor(context, actionResolver),
    private val autonomousGate: AutonomousExecutionGate = AutonomousExecutionGate()
) {

    companion object {
        private const val TAG = "WorkflowEngine"
    }

    suspend fun executeWorkflow(
        workflow: Workflow,
        service: AutomationAccessibilityService? = AutomationAccessibilityService.instance,
        trigger: ExecutionTrigger = ExecutionTrigger.MANUAL,
        globalAutonomousEnabled: Boolean = true
    ): ActionResult {
        Log.i(TAG, "WORKFLOW_STARTED: ${workflow.name} (ID: ${workflow.id}, Trigger: $trigger)")

        if (service == null) {
            Log.e(TAG, "WORKFLOW_BLOCKED: AccessibilityService is not enabled/connected")
            return ActionResult(
                status = ActionResultStatus.BLOCKED,
                reason = ExecutionReason.ACCESSIBILITY_DISABLED,
                trigger = trigger,
                message = "AccessibilityService is disabled or not running."
            )
        }

        var lastSnapshot: UiSnapshot? = null
        var lastScreenshotPath: String? = null

        for (step in workflow.steps) {
            Log.i(TAG, "ACTION_STARTED: Step ${step.id} - ${step.action.type} (target: ${step.action.targetValue})")

            // Enforce bounded recovery (maxRetries = 1 by default)
            val maxRetries = minOf(1, workflow.retryPolicy.maxRetries)
            var attempt = 0
            var stepResult: ActionResult? = null

            while (attempt <= maxRetries) {
                if (attempt > 0) {
                    Log.w(TAG, "BOUNDED_RETRY: Step ${step.id}, Attempt ${attempt + 1}/${maxRetries + 1}")
                    delay(workflow.retryPolicy.retryDelayMs)
                }

                if (step.action.type == ActionType.EXECUTE_LEARNED_DECISION) {
                    val root = service.getRootNode()
                    val snapshot = ActionResolver.captureSnapshot(root, service.packageName ?: "")
                    val proposed = learnedDecisionResolver.resolveDecision(snapshot, globalAutonomousEnabled)

                    val gateEval = autonomousGate.evaluate(
                        learningMode = AutomationAccessibilityService.currentLearningMode.value,
                        globalAutonomousEnabled = globalAutonomousEnabled,
                        proposedDecision = proposed
                    )

                    if (!gateEval.allowed) {
                        Log.i(TAG, "AUTONOMOUS_GATE_REJECTED: ${gateEval.explanation}")
                        stepResult = ActionResult(
                            status = ActionResultStatus.SUCCESS,
                            reason = ExecutionReason.LEARNING_REQUIRED,
                            trigger = trigger,
                            message = gateEval.explanation,
                            snapshot = snapshot
                        )
                        break
                    }

                    stepResult = deviceActionExecutor.executeAndAudit(
                        workflowId = workflow.id,
                        action = proposed!!.action!!,
                        service = service,
                        trigger = trigger,
                        verificationAction = step.verificationAction
                    )

                    // Update demonstration statistics
                    val dao = AppDatabase.getDatabase(context).demonstrationDao()
                    if (stepResult.status == ActionResultStatus.SUCCESS) {
                        dao.insertRecord(proposed.record.copy(successCount = proposed.record.successCount + 1))
                    } else {
                        dao.insertRecord(proposed.record.copy(failureCount = proposed.record.failureCount + 1))
                    }
                } else {
                    stepResult = deviceActionExecutor.executeAndAudit(
                        workflowId = workflow.id,
                        action = step.action,
                        service = service,
                        trigger = trigger,
                        verificationAction = step.verificationAction
                    )
                }

                lastSnapshot = stepResult.snapshot ?: lastSnapshot
                if (stepResult.screenshotPath != null) {
                    lastScreenshotPath = stepResult.screenshotPath
                }

                if (stepResult.authState == AuthState.LOGIN_REQUIRED) {
                    Log.w(TAG, "WORKFLOW_TERMINATED: LOGIN_REQUIRED detected at step ${step.id}")
                    return ActionResult(
                        status = ActionResultStatus.BLOCKED,
                        reason = ExecutionReason.LOGIN_REQUIRED,
                        trigger = trigger,
                        message = "Sign-in required to continue workflow.",
                        screenshotPath = lastScreenshotPath,
                        snapshot = lastSnapshot,
                        authState = AuthState.LOGIN_REQUIRED
                    )
                }

                if (stepResult.status == ActionResultStatus.SUCCESS) {
                    Log.i(TAG, "ACTION_SUCCESS: Step ${step.id}")
                    break
                }

                attempt++
            }

            if (stepResult == null || stepResult.status != ActionResultStatus.SUCCESS) {
                val finalStatus = stepResult?.status ?: ActionResultStatus.FAILED
                val finalReason = stepResult?.reason ?: ExecutionReason.NONE
                val failureMsg = stepResult?.message ?: "Step ${step.id} failed after retries."
                Log.e(TAG, "WORKFLOW_FAILED: $failureMsg")
                return ActionResult(
                    status = finalStatus,
                    reason = finalReason,
                    trigger = trigger,
                    message = "Workflow '${workflow.name}' failed at step '${step.id}': $failureMsg",
                    screenshotPath = lastScreenshotPath,
                    snapshot = lastSnapshot
                )
            }
        }

        Log.i(TAG, "WORKFLOW_COMPLETED: ${workflow.name} (Trigger: $trigger)")
        return ActionResult(
            status = ActionResultStatus.SUCCESS,
            reason = ExecutionReason.NONE,
            trigger = trigger,
            message = "Workflow '${workflow.name}' completed successfully.",
            screenshotPath = lastScreenshotPath,
            snapshot = lastSnapshot
        )
    }
}
