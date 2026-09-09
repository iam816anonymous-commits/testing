package com.creator.automation

import android.content.Context
import android.util.Log

class DeviceActionExecutor(
    private val context: Context,
    private val actionResolver: ActionResolver = ActionResolver(),
    private val auditDao: ActionAuditDao = AppDatabase.getDatabase(context).actionAuditDao()
) {

    companion object {
        private const val TAG = "DeviceActionExecutor"

        fun redactSensitiveText(targetValue: String?): String? {
            if (targetValue == null) return null
            val lower = targetValue.lowercase()
            if (lower.contains("password") || lower.contains("secret") || lower.contains("token") || lower.contains("pin")) {
                return "[REDACTED]"
            }
            return targetValue
        }
    }

    suspend fun executeAndAudit(
        workflowId: String,
        action: AutomationAction,
        service: AutomationAccessibilityService,
        trigger: ExecutionTrigger = ExecutionTrigger.MANUAL,
        verificationAction: AutomationAction? = null
    ): ActionResult {
        val startTime = System.currentTimeMillis()

        // 1. Capture Before State
        val beforeRoot = service.getRootNode()
        val beforeSnapshot = ActionResolver.captureSnapshot(beforeRoot, service.packageName ?: "")
        val beforeStateSig = StateSignatureGenerator.generateSignature(beforeSnapshot)

        val redactedTarget = redactSensitiveText(action.targetValue)

        Log.i(TAG, "EXECUTING_ACTION: Type=${action.type}, Target='$redactedTarget', Pkg='${beforeSnapshot.packageName}'")

        // 2. Dispatch Action via Accessibility Engine
        val dispatchResult = when (action.type) {
            ActionType.OPEN_URL -> performOpenUrl(action.targetValue)
            ActionType.CHECK_AUTH_STATE -> performAuthCheck(beforeSnapshot)
            ActionType.WAIT -> performWait(action.targetValue)
            ActionType.WAIT_FOR_TEXT, ActionType.VERIFY_TEXT -> performWaitForText(action.targetValue, action.timeoutMs, service)
            ActionType.CLICK_TEXT -> performClickText(action.targetValue, beforeSnapshot)
            ActionType.SCROLL -> performScroll(beforeSnapshot)
            ActionType.GO_BACK -> performGoBack(service)
            ActionType.CAPTURE_SCREEN -> performCaptureScreen(service, beforeSnapshot)
            ActionType.READ_VISIBLE_UI -> ActionResult(status = ActionResultStatus.SUCCESS, snapshot = beforeSnapshot)
            else -> ActionResult(status = ActionResultStatus.SUCCESS, snapshot = beforeSnapshot)
        }

        val durationMs = System.currentTimeMillis() - startTime

        // 3. Capture After State & Compare
        val afterRoot = service.getRootNode()
        val afterSnapshot = ActionResolver.captureSnapshot(afterRoot, service.packageName ?: "")
        val afterStateSig = StateSignatureGenerator.generateSignature(afterSnapshot)

        val stateChange = compareStates(beforeStateSig, afterStateSig, verificationAction != null)

        // 4. Verify Result
        val verificationStatus = if (dispatchResult.status == ActionResultStatus.SUCCESS) {
            if (verificationAction != null) {
                val verifyRes = performWaitForText(verificationAction.targetValue, verificationAction.timeoutMs, service)
                if (verifyRes.status == ActionResultStatus.SUCCESS) VerificationStatus.SUCCESSFULLY_VERIFIED else VerificationStatus.FAILED
            } else {
                VerificationStatus.DISPATCHED
            }
        } else {
            VerificationStatus.FAILED
        }

        val isOverallSuccess = dispatchResult.status == ActionResultStatus.SUCCESS && verificationStatus != VerificationStatus.FAILED

        // 5. Persist Audit Record
        val auditRecord = ActionAuditRecord(
            timestamp = System.currentTimeMillis(),
            workflowId = workflowId,
            trigger = trigger.name,
            activePackage = beforeSnapshot.packageName,
            beforeStateSignature = beforeStateSig,
            actionType = action.type.name,
            targetIdentifier = redactedTarget,
            actionParametersSummary = "Type=${action.type}, Timeout=${action.timeoutMs}ms",
            dispatchResult = dispatchResult.status.name,
            afterStateSignature = afterStateSig,
            stateChangeResult = stateChange.name,
            verificationStatus = verificationStatus.name,
            success = isOverallSuccess,
            failureReason = if (!isOverallSuccess) dispatchResult.message ?: "Verification failed" else null,
            durationMs = durationMs
        )

        try {
            auditDao.insertAuditRecord(auditRecord)
            Log.i(TAG, "AUDIT_LOGGED: Action ${action.type} -> Success=$isOverallSuccess, Verification=$verificationStatus, StateChange=$stateChange")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing action audit record", e)
        }

        return dispatchResult.copy(
            snapshot = afterSnapshot,
            message = if (!isOverallSuccess) dispatchResult.message ?: "Verification failed" else dispatchResult.message
        )
    }

    fun compareStates(beforeSig: String, afterSig: String, isVerificationExpected: Boolean): StateChangeResult {
        return when {
            beforeSig == afterSig -> StateChangeResult.NO_CHANGE
            isVerificationExpected -> StateChangeResult.EXPECTED_STATE_REACHED
            else -> StateChangeResult.STATE_CHANGED
        }
    }

    private fun performOpenUrl(url: String?): ActionResult {
        if (url.isNullOrBlank()) return ActionResult(status = ActionResultStatus.FAILED, message = "OPEN_URL requires target URL")
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(status = ActionResultStatus.SUCCESS)
        } catch (e: Exception) {
            ActionResult(status = ActionResultStatus.FAILED, message = e.message)
        }
    }

    private fun performAuthCheck(snapshot: UiSnapshot): ActionResult {
        val authState = actionResolver.detectAuthState(snapshot)
        return if (authState == AuthState.LOGIN_REQUIRED) {
            ActionResult(status = ActionResultStatus.BLOCKED, reason = ExecutionReason.LOGIN_REQUIRED, authState = AuthState.LOGIN_REQUIRED)
        } else {
            ActionResult(status = ActionResultStatus.SUCCESS, authState = AuthState.AUTHENTICATED)
        }
    }

    private suspend fun performWait(durationStr: String?): ActionResult {
        val duration = durationStr?.toLongOrNull() ?: 2000L
        kotlinx.coroutines.delay(duration)
        return ActionResult(status = ActionResultStatus.SUCCESS)
    }

    private suspend fun performWaitForText(targetText: String?, timeoutMs: Long, service: AutomationAccessibilityService): ActionResult {
        if (targetText.isNullOrBlank()) return ActionResult(status = ActionResultStatus.FAILED, message = "Target text required")
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val root = service.getRootNode()
            val snap = ActionResolver.captureSnapshot(root, service.packageName ?: "")
            if (actionResolver.detectAuthState(snap) == AuthState.LOGIN_REQUIRED) {
                return ActionResult(status = ActionResultStatus.BLOCKED, reason = ExecutionReason.LOGIN_REQUIRED, authState = AuthState.LOGIN_REQUIRED)
            }
            val match = actionResolver.resolveTarget(snap, targetText)
            if (match != null) {
                return ActionResult(status = ActionResultStatus.SUCCESS, matchedNode = match.node, matchMethod = match.matchMethod)
            }
            kotlinx.coroutines.delay(500L)
        }
        return ActionResult(status = ActionResultStatus.TIMEOUT, reason = ExecutionReason.TIMEOUT, message = "Timeout waiting for '$targetText'")
    }

    private fun performClickText(targetText: String?, snapshot: UiSnapshot): ActionResult {
        if (targetText.isNullOrBlank()) return ActionResult(status = ActionResultStatus.FAILED, message = "CLICK_TEXT requires target text")
        val match = actionResolver.resolveTarget(snapshot, targetText)
            ?: return ActionResult(status = ActionResultStatus.NOT_FOUND, reason = ExecutionReason.UI_NOT_FOUND, message = "Text '$targetText' not found")

        val nodeRef = match.node.nodeRef as? android.view.accessibility.AccessibilityNodeInfo
        if (nodeRef != null) {
            var targetNode: android.view.accessibility.AccessibilityNodeInfo? = nodeRef
            while (targetNode != null && !targetNode.isClickable) {
                targetNode = targetNode.parent
            }
            if (targetNode != null && targetNode.isClickable) {
                if (targetNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) {
                    return ActionResult(status = ActionResultStatus.SUCCESS, matchedNode = match.node, matchMethod = match.matchMethod)
                }
            }
        }
        return ActionResult(status = ActionResultStatus.FAILED, reason = ExecutionReason.UI_NOT_FOUND, message = "Node found for '$targetText' but click failed")
    }

    private fun performScroll(snapshot: UiSnapshot): ActionResult {
        val scrollableNode = snapshot.scrollableNodes.firstOrNull()?.nodeRef as? android.view.accessibility.AccessibilityNodeInfo
        if (scrollableNode != null && scrollableNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            return ActionResult(status = ActionResultStatus.SUCCESS)
        }
        return ActionResult(status = ActionResultStatus.FAILED, reason = ExecutionReason.UI_NOT_FOUND, message = "No scrollable node found")
    }

    private fun performGoBack(service: AutomationAccessibilityService): ActionResult {
        return if (service.performGoBack()) ActionResult(status = ActionResultStatus.SUCCESS)
        else ActionResult(status = ActionResultStatus.FAILED, message = "GO_BACK failed")
    }

    private suspend fun performCaptureScreen(service: AutomationAccessibilityService, snapshot: UiSnapshot): ActionResult {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            return ActionResult(status = ActionResultStatus.FAILED, reason = ExecutionReason.UNSUPPORTED_ANDROID_VERSION, message = "Requires Android 11+")
        }
        val path = service.captureScreenshot()
        return if (path != null) ActionResult(status = ActionResultStatus.SUCCESS, screenshotPath = path)
        else ActionResult(status = ActionResultStatus.FAILED, reason = ExecutionReason.SCREENSHOT_FAILED, message = "Screenshot capture failed")
    }
}
