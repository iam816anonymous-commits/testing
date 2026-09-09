package com.creator.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

class WorkflowEngine(
    private val context: Context,
    private val actionResolver: ActionResolver = ActionResolver(),
    private val learnedDecisionResolver: LearnedDecisionResolver = LearnedDecisionResolver(
        AppDatabase.getDatabase(context).demonstrationDao()
    )
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

            var attempt = 0
            var stepResult: ActionResult? = null

            while (attempt <= workflow.retryPolicy.maxRetries) {
                if (attempt > 0) {
                    Log.w(TAG, "ACTION_RETRY: Step ${step.id}, Attempt ${attempt + 1}/${workflow.retryPolicy.maxRetries + 1}")
                    delay(workflow.retryPolicy.retryDelayMs)
                }

                stepResult = executeAction(step.action, service, trigger, globalAutonomousEnabled)
                lastSnapshot = stepResult.snapshot ?: lastSnapshot
                if (stepResult.screenshotPath != null) {
                    lastScreenshotPath = stepResult.screenshotPath
                }

                // If authentication check indicates LOGIN_REQUIRED, stop workflow gracefully
                if (stepResult.authState == AuthState.LOGIN_REQUIRED) {
                    Log.w(TAG, "WORKFLOW_TERMINATED: LOGIN_REQUIRED detected at step ${step.id}")
                    return ActionResult(
                        status = ActionResultStatus.BLOCKED,
                        reason = ExecutionReason.LOGIN_REQUIRED,
                        trigger = trigger,
                        message = "Sign-in required to continue YouTube Studio workflow.",
                        screenshotPath = lastScreenshotPath,
                        snapshot = lastSnapshot,
                        authState = AuthState.LOGIN_REQUIRED
                    )
                }

                if (stepResult.status == ActionResultStatus.SUCCESS) {
                    // Check verification action if specified
                    if (step.verificationAction != null) {
                        Log.i(TAG, "VERIFICATION_STARTED: Step ${step.id}")
                        val verifyResult = executeAction(step.verificationAction, service, trigger, globalAutonomousEnabled)
                        if (verifyResult.status == ActionResultStatus.SUCCESS) {
                            Log.i(TAG, "VERIFICATION_SUCCESS: Step ${step.id}")
                        } else {
                            Log.w(TAG, "VERIFICATION_FAILED: Step ${step.id} - ${verifyResult.message}")
                            stepResult = ActionResult(
                                status = ActionResultStatus.FAILED,
                                reason = ExecutionReason.VERIFICATION_FAILED,
                                trigger = trigger,
                                message = "Verification failed: ${verifyResult.message}",
                                snapshot = verifyResult.snapshot ?: lastSnapshot
                            )
                        }
                    }
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

    suspend fun executeAction(
        action: AutomationAction,
        service: AutomationAccessibilityService,
        trigger: ExecutionTrigger = ExecutionTrigger.MANUAL,
        globalAutonomousEnabled: Boolean = true
    ): ActionResult {
        val root = service.getRootNode()
        val snapshot = ActionResolver.captureSnapshot(root, service.packageName ?: "")

        return when (action.type) {
            ActionType.OPEN_URL -> {
                val url = action.targetValue ?: return ActionResult(
                    status = ActionResultStatus.FAILED,
                    trigger = trigger,
                    message = "OPEN_URL requires target URL"
                )
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Log.i(TAG, "URL_OPENED: $url")
                    ActionResult(status = ActionResultStatus.SUCCESS, trigger = trigger, snapshot = snapshot)
                } catch (e: Exception) {
                    Log.e(TAG, "ACTION_FAILED: OPEN_URL error", e)
                    ActionResult(status = ActionResultStatus.FAILED, trigger = trigger, message = e.message, snapshot = snapshot)
                }
            }

            ActionType.EXECUTE_LEARNED_DECISION -> {
                val proposed = learnedDecisionResolver.resolveDecision(snapshot, globalAutonomousEnabled)
                if (proposed == null || proposed.action == null) {
                    Log.i(TAG, "LEARNING_REQUIRED: No learned action available for state signature.")
                    return ActionResult(
                        status = ActionResultStatus.SUCCESS,
                        reason = ExecutionReason.LEARNING_REQUIRED,
                        trigger = trigger,
                        message = "No learned action needed or available for current state signature",
                        snapshot = snapshot
                    )
                }

                if (proposed.isAmbiguous) {
                    Log.w(TAG, "AMBIGUOUS_STATE: Cannot execute learned decision because choices are ambiguous")
                    return ActionResult(
                        status = ActionResultStatus.BLOCKED,
                        reason = ExecutionReason.LEARNING_REQUIRED,
                        trigger = trigger,
                        message = proposed.reason,
                        snapshot = snapshot
                    )
                }

                if (!proposed.canAutoExecute) {
                    Log.i(TAG, "ASSISTED_MODE: Decision proposed (${proposed.record.targetText}) but autonomous execution disabled or confidence low (${proposed.confidence})")
                    return ActionResult(
                        status = ActionResultStatus.SUCCESS,
                        reason = ExecutionReason.NONE,
                        trigger = trigger,
                        message = "Decision proposed: '${proposed.record.targetText}' (Confidence: ${proposed.confidence})",
                        snapshot = snapshot
                    )
                }

                Log.i(TAG, "EXECUTING_LEARNED_DECISION: Executing '${proposed.action.type}' -> '${proposed.action.targetValue}'")
                val result = executeAction(proposed.action, service, trigger, globalAutonomousEnabled)

                // Update success / failure stats on record
                val dao = AppDatabase.getDatabase(context).demonstrationDao()
                if (result.status == ActionResultStatus.SUCCESS) {
                    val updated = proposed.record.copy(successCount = proposed.record.successCount + 1)
                    dao.insertRecord(updated)
                } else {
                    val updated = proposed.record.copy(failureCount = proposed.record.failureCount + 1)
                    dao.insertRecord(updated)
                }

                result
            }

            ActionType.CHECK_AUTH_STATE -> {
                val authState = actionResolver.detectAuthState(snapshot)
                if (authState == AuthState.LOGIN_REQUIRED) {
                    ActionResult(
                        status = ActionResultStatus.BLOCKED,
                        reason = ExecutionReason.LOGIN_REQUIRED,
                        trigger = trigger,
                        message = "Sign-in prompt detected in visible UI",
                        snapshot = snapshot,
                        authState = AuthState.LOGIN_REQUIRED
                    )
                } else {
                    ActionResult(
                        status = ActionResultStatus.SUCCESS,
                        trigger = trigger,
                        message = "Authenticated state confirmed",
                        snapshot = snapshot,
                        authState = AuthState.AUTHENTICATED
                    )
                }
            }

            ActionType.WAIT -> {
                val duration = action.targetValue?.toLongOrNull() ?: 2000L
                delay(duration)
                ActionResult(status = ActionResultStatus.SUCCESS, trigger = trigger, snapshot = snapshot)
            }

            ActionType.WAIT_FOR_TEXT, ActionType.VERIFY_TEXT -> {
                val targetText = action.targetValue ?: return ActionResult(
                    status = ActionResultStatus.FAILED,
                    trigger = trigger,
                    message = "Action requires target text"
                )

                val startTime = System.currentTimeMillis()
                val timeout = action.timeoutMs

                while (System.currentTimeMillis() - startTime < timeout) {
                    val currentRoot = service.getRootNode()
                    val currentSnapshot = ActionResolver.captureSnapshot(currentRoot, service.packageName ?: "")

                    val authState = actionResolver.detectAuthState(currentSnapshot)
                    if (authState == AuthState.LOGIN_REQUIRED) {
                        return ActionResult(
                            status = ActionResultStatus.BLOCKED,
                            reason = ExecutionReason.LOGIN_REQUIRED,
                            trigger = trigger,
                            message = "Login required detected while waiting for '$targetText'",
                            snapshot = currentSnapshot,
                            authState = AuthState.LOGIN_REQUIRED
                        )
                    }

                    val match = actionResolver.resolveTarget(currentSnapshot, targetText)
                    if (match != null) {
                        Log.i(TAG, "TEXT_FOUND: '$targetText' via ${match.reason}")
                        return ActionResult(
                            status = ActionResultStatus.SUCCESS,
                            trigger = trigger,
                            matchedNode = match.node,
                            matchMethod = match.matchMethod,
                            snapshot = currentSnapshot
                        )
                    }

                    delay(500L)
                }

                Log.w(TAG, "ACTION_TIMEOUT: Timeout waiting for '$targetText'")
                ActionResult(
                    status = ActionResultStatus.TIMEOUT,
                    reason = ExecutionReason.TIMEOUT,
                    trigger = trigger,
                    message = "Timeout waiting for text: '$targetText'",
                    snapshot = snapshot
                )
            }

            ActionType.CLICK_TEXT -> {
                val targetText = action.targetValue ?: return ActionResult(
                    status = ActionResultStatus.FAILED,
                    trigger = trigger,
                    message = "CLICK_TEXT requires target text"
                )

                val match = actionResolver.resolveTarget(snapshot, targetText)
                    ?: return ActionResult(
                        status = ActionResultStatus.NOT_FOUND,
                        reason = ExecutionReason.UI_NOT_FOUND,
                        trigger = trigger,
                        message = "Could not find node for text: '$targetText'",
                        snapshot = snapshot
                    )

                val nodeRef = match.node.nodeRef as? AccessibilityNodeInfo
                if (nodeRef != null) {
                    var targetNode: AccessibilityNodeInfo? = nodeRef
                    // Walk up parent chain if text node itself is not clickable
                    while (targetNode != null && !targetNode.isClickable) {
                        targetNode = targetNode.parent
                    }

                    if (targetNode != null && targetNode.isClickable) {
                        val performed = targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (performed) {
                            Log.i(TAG, "ACTION_SUCCESS: CLICK_TEXT on '$targetText'")
                            return ActionResult(
                                status = ActionResultStatus.SUCCESS,
                                trigger = trigger,
                                matchedNode = match.node,
                                matchMethod = match.matchMethod,
                                snapshot = snapshot
                            )
                        }
                    }
                }

                Log.w(TAG, "ACTION_FAILED: Node found for '$targetText' but click action failed")
                ActionResult(
                    status = ActionResultStatus.FAILED,
                    reason = ExecutionReason.UI_NOT_FOUND,
                    trigger = trigger,
                    message = "Node found for '$targetText' but click action could not be executed",
                    matchedNode = match.node,
                    snapshot = snapshot
                )
            }

            ActionType.SCROLL -> {
                val scrollableNode = snapshot.scrollableNodes.firstOrNull()
                val nodeRef = scrollableNode?.nodeRef as? AccessibilityNodeInfo
                if (nodeRef != null) {
                    val performed = nodeRef.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    if (performed) {
                        Log.i(TAG, "ACTION_SUCCESS: SCROLL_FORWARD")
                        return ActionResult(status = ActionResultStatus.SUCCESS, trigger = trigger, snapshot = snapshot)
                    }
                }
                ActionResult(
                    status = ActionResultStatus.FAILED,
                    reason = ExecutionReason.UI_NOT_FOUND,
                    trigger = trigger,
                    message = "No scrollable node available to perform scroll action",
                    snapshot = snapshot
                )
            }

            ActionType.GO_BACK -> {
                val performed = service.performGoBack()
                if (performed) {
                    Log.i(TAG, "ACTION_SUCCESS: GO_BACK")
                    ActionResult(status = ActionResultStatus.SUCCESS, trigger = trigger, snapshot = snapshot)
                } else {
                    ActionResult(
                        status = ActionResultStatus.FAILED,
                        trigger = trigger,
                        message = "Global GO_BACK failed",
                        snapshot = snapshot
                    )
                }
            }

            ActionType.CAPTURE_SCREEN -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    Log.w(TAG, "SCREENSHOT_FAILED: Android version < R (API 30)")
                    return ActionResult(
                        status = ActionResultStatus.FAILED,
                        reason = ExecutionReason.UNSUPPORTED_ANDROID_VERSION,
                        trigger = trigger,
                        message = "Screenshot API requires Android 11 (API 30)+",
                        snapshot = snapshot
                    )
                }

                val path = service.captureScreenshot()
                if (path != null) {
                    ActionResult(status = ActionResultStatus.SUCCESS, trigger = trigger, screenshotPath = path, snapshot = snapshot)
                } else {
                    ActionResult(
                        status = ActionResultStatus.FAILED,
                        reason = ExecutionReason.SCREENSHOT_FAILED,
                        trigger = trigger,
                        message = "Failed to capture screenshot",
                        snapshot = snapshot
                    )
                }
            }

            ActionType.READ_VISIBLE_UI -> {
                Log.i(TAG, "READ_VISIBLE_UI: Read ${snapshot.visibleTexts.size} text elements")
                ActionResult(
                    status = ActionResultStatus.SUCCESS,
                    trigger = trigger,
                    message = "Read ${snapshot.visibleTexts.size} text elements: ${snapshot.visibleTexts.take(5).joinToString(", ")}",
                    snapshot = snapshot
                )
            }

            ActionType.END -> {
                ActionResult(status = ActionResultStatus.SUCCESS, trigger = trigger, snapshot = snapshot)
            }
        }
    }
}
