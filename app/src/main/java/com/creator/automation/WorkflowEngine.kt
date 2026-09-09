package com.creator.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import java.io.File

class WorkflowEngine(
    private val context: Context,
    private val actionResolver: ActionResolver = ActionResolver()
) {

    companion object {
        private const val TAG = "WorkflowEngine"
    }

    suspend fun executeWorkflow(
        workflow: Workflow,
        service: AutomationAccessibilityService? = AutomationAccessibilityService.instance
    ): ActionResult {
        Log.i(TAG, "WORKFLOW_STARTED: ${workflow.name} (ID: ${workflow.id})")

        if (service == null) {
            Log.e(TAG, "WORKFLOW_BLOCKED: AccessibilityService is not enabled/connected")
            return ActionResult(
                status = ActionResultStatus.BLOCKED,
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

                stepResult = executeAction(step.action, service)
                lastSnapshot = stepResult.snapshot ?: lastSnapshot
                if (stepResult.screenshotPath != null) {
                    lastScreenshotPath = stepResult.screenshotPath
                }

                if (stepResult.status == ActionResultStatus.SUCCESS) {
                    // Check verification action if specified
                    if (step.verificationAction != null) {
                        Log.i(TAG, "VERIFICATION_STARTED: Step ${step.id}")
                        val verifyResult = executeAction(step.verificationAction, service)
                        if (verifyResult.status == ActionResultStatus.SUCCESS) {
                            Log.i(TAG, "VERIFICATION_SUCCESS: Step ${step.id}")
                        } else {
                            Log.w(TAG, "VERIFICATION_FAILED: Step ${step.id} - ${verifyResult.message}")
                            stepResult = ActionResult(
                                status = ActionResultStatus.FAILED,
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
                val failureMsg = stepResult?.message ?: "Step ${step.id} failed after retries."
                Log.e(TAG, "WORKFLOW_FAILED: $failureMsg")
                return ActionResult(
                    status = finalStatus,
                    message = "Workflow '${workflow.name}' failed at step '${step.id}': $failureMsg",
                    screenshotPath = lastScreenshotPath,
                    snapshot = lastSnapshot
                )
            }
        }

        Log.i(TAG, "WORKFLOW_COMPLETED: ${workflow.name}")
        return ActionResult(
            status = ActionResultStatus.SUCCESS,
            message = "Workflow '${workflow.name}' completed successfully.",
            screenshotPath = lastScreenshotPath,
            snapshot = lastSnapshot
        )
    }

    suspend fun executeAction(
        action: AutomationAction,
        service: AutomationAccessibilityService
    ): ActionResult {
        val root = service.getRootNode()
        val snapshot = ActionResolver.captureSnapshot(root, service.packageName ?: "")

        return when (action.type) {
            ActionType.OPEN_URL -> {
                val url = action.targetValue ?: return ActionResult(
                    status = ActionResultStatus.FAILED,
                    message = "OPEN_URL requires target URL"
                )
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ActionResult(status = ActionResultStatus.SUCCESS, snapshot = snapshot)
                } catch (e: Exception) {
                    ActionResult(status = ActionResultStatus.FAILED, message = e.message)
                }
            }

            ActionType.WAIT -> {
                val duration = action.targetValue?.toLongOrNull() ?: 2000L
                delay(duration)
                ActionResult(status = ActionResultStatus.SUCCESS, snapshot = snapshot)
            }

            ActionType.WAIT_FOR_TEXT, ActionType.VERIFY_TEXT -> {
                val targetText = action.targetValue ?: return ActionResult(
                    status = ActionResultStatus.FAILED,
                    message = "Action requires target text"
                )

                val startTime = System.currentTimeMillis()
                val timeout = action.timeoutMs

                while (System.currentTimeMillis() - startTime < timeout) {
                    val currentRoot = service.getRootNode()
                    val currentSnapshot = ActionResolver.captureSnapshot(currentRoot, service.packageName ?: "")
                    val match = actionResolver.resolveTarget(currentSnapshot, targetText)

                    if (match != null) {
                        Log.i(TAG, "UI_MATCH_FOUND: ${match.reason}")
                        return ActionResult(
                            status = ActionResultStatus.SUCCESS,
                            matchedNode = match.node,
                            matchMethod = match.matchMethod,
                            snapshot = currentSnapshot
                        )
                    }

                    delay(500L)
                }

                Log.w(TAG, "UI_MATCH_NOT_FOUND: Timeout waiting for '$targetText'")
                ActionResult(
                    status = ActionResultStatus.TIMEOUT,
                    message = "Timeout waiting for text: '$targetText'",
                    snapshot = snapshot
                )
            }

            ActionType.CLICK_TEXT -> {
                val targetText = action.targetValue ?: return ActionResult(
                    status = ActionResultStatus.FAILED,
                    message = "CLICK_TEXT requires target text"
                )

                val match = actionResolver.resolveTarget(snapshot, targetText)
                    ?: return ActionResult(
                        status = ActionResultStatus.NOT_FOUND,
                        message = "Could not find node for text: '$targetText'",
                        snapshot = snapshot
                    )

                val nodeRef = match.node.nodeRef as? AccessibilityNodeInfo
                if (nodeRef != null) {
                    var targetNode: AccessibilityNodeInfo? = nodeRef
                    // Walk up parents if the matched text node itself is not clickable
                    while (targetNode != null && !targetNode.isClickable) {
                        targetNode = targetNode.parent
                    }

                    if (targetNode != null && targetNode.isClickable) {
                        val performed = targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (performed) {
                            return ActionResult(
                                status = ActionResultStatus.SUCCESS,
                                matchedNode = match.node,
                                matchMethod = match.matchMethod,
                                snapshot = snapshot
                            )
                        }
                    }
                }

                ActionResult(
                    status = ActionResultStatus.FAILED,
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
                        return ActionResult(status = ActionResultStatus.SUCCESS, snapshot = snapshot)
                    }
                }
                ActionResult(
                    status = ActionResultStatus.FAILED,
                    message = "No scrollable node available to perform scroll action",
                    snapshot = snapshot
                )
            }

            ActionType.GO_BACK -> {
                val performed = service.performGoBack()
                if (performed) {
                    ActionResult(status = ActionResultStatus.SUCCESS, snapshot = snapshot)
                } else {
                    ActionResult(status = ActionResultStatus.FAILED, message = "Global GO_BACK failed", snapshot = snapshot)
                }
            }

            ActionType.CAPTURE_SCREEN -> {
                val path = service.captureScreenshot()
                if (path != null) {
                    ActionResult(status = ActionResultStatus.SUCCESS, screenshotPath = path, snapshot = snapshot)
                } else {
                    ActionResult(status = ActionResultStatus.FAILED, message = "Failed to capture screenshot", snapshot = snapshot)
                }
            }

            ActionType.READ_VISIBLE_UI -> {
                ActionResult(
                    status = ActionResultStatus.SUCCESS,
                    message = "Read ${snapshot.visibleTexts.size} text elements: ${snapshot.visibleTexts.take(5).joinToString(", ")}",
                    snapshot = snapshot
                )
            }

            ActionType.END -> {
                ActionResult(status = ActionResultStatus.SUCCESS, snapshot = snapshot)
            }
        }
    }
}
