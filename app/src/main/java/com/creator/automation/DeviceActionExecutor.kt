package com.creator.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class DeviceActionExecutor(
    private val context: Context,
    private val actionResolver: ActionResolver = ActionResolver(),
    private val auditDao: ActionAuditDao = AppDatabase.getDatabase(context).actionAuditDao(),
    private val waitEngine: WaitEngine = WaitEngine(actionResolver),
    private val appResolver: AppResolver = AppResolver(context)
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
        verificationAction: AutomationAction? = null,
        screenObservationProvider: ObservationProvider? = null
    ): ActionResult {
        val startTime = System.currentTimeMillis()

        // 1. Capture Before State (Always capture fresh state)
        val beforeRoot = service.getRootNode()
        val beforeSnapshot = ActionResolver.captureSnapshot(beforeRoot, service.packageName ?: "")
        val beforeStateSig = StateSignatureGenerator.generateSignature(beforeSnapshot)

        val redactedTarget = redactSensitiveText(action.targetValue)
        val redactedInput = redactSensitiveText(action.inputData)

        Log.i(TAG, "EXECUTING_ACTION: Type=${action.type}, Target='$redactedTarget', Input='$redactedInput', Semantics=${action.semantics}, Pkg='${beforeSnapshot.packageName}'")

        // 2. Check Action Preconditions (Evaluated against fresh snapshot before dispatch)
        val preconditionResult = checkPreconditions(action.preconditions, beforeSnapshot)
        if (!preconditionResult.success) {
            Log.w(TAG, "PRECONDITION_FAILED: ${preconditionResult.failureReason}")
            val auditRecord = ActionAuditRecord(
                timestamp = System.currentTimeMillis(),
                workflowId = workflowId,
                trigger = trigger.name,
                activePackage = beforeSnapshot.packageName,
                beforeStateSignature = beforeStateSig,
                actionType = action.type.name,
                targetIdentifier = redactedTarget,
                actionParametersSummary = "Semantics=${action.semantics}, Preconditions=${action.preconditions.size}",
                reason = ExecutionReason.PRECONDITION_FAILED.name,
                dispatchResult = ActionResultStatus.BLOCKED.name,
                verificationStatus = VerificationStatus.FAILED.name,
                success = false,
                failureReason = preconditionResult.failureReason
            )
            auditDao.insertAuditRecord(auditRecord)
            return ActionResult(
                status = ActionResultStatus.BLOCKED,
                reason = ExecutionReason.PRECONDITION_FAILED,
                trigger = trigger,
                message = preconditionResult.failureReason,
                snapshot = beforeSnapshot
            )
        }

        // 3. Dispatch Action via Generic Accessibility Engine
        val dispatchResult = when (action.type) {
            ActionType.LAUNCH_APP -> performLaunchApp(action.targetValue)
            ActionType.OPEN_URL -> performOpenUrl(action.targetValue)
            ActionType.CHECK_AUTH_STATE -> performAuthCheck(beforeSnapshot)
            ActionType.WAIT -> performWait(action.targetValue)
            ActionType.WAIT_FOR_TEXT, ActionType.VERIFY_TEXT -> performWaitForText(action.targetValue, action.timeoutMs, service)
            ActionType.CLICK_TEXT -> performClickText(action.targetValue, beforeSnapshot)
            ActionType.LONG_CLICK -> performLongClick(action.targetValue, beforeSnapshot)
            ActionType.TYPE_TEXT -> performTypeText(action.targetValue, action.inputData, beforeSnapshot)
            ActionType.CLEAR_TEXT -> performClearText(action.targetValue, beforeSnapshot)
            ActionType.PRESS_ENTER -> performPressEnter(service, beforeSnapshot)
            ActionType.SCROLL, ActionType.SCROLL_DOWN -> performScroll(service, beforeSnapshot, forward = true, beforeStateSig = beforeStateSig)
            ActionType.SCROLL_UP -> performScroll(service, beforeSnapshot, forward = false, beforeStateSig = beforeStateSig)
            ActionType.GO_BACK -> performGoBack(service)
            ActionType.PRESS_HOME -> performGlobalAction(service, AccessibilityService.GLOBAL_ACTION_HOME, "HOME")
            ActionType.PRESS_RECENTS -> performGlobalAction(service, AccessibilityService.GLOBAL_ACTION_RECENTS, "RECENTS")
            ActionType.CAPTURE_SCREEN -> performCaptureScreen(service, beforeSnapshot)
            ActionType.READ_VISIBLE_UI, ActionType.REFRESH_OBSERVATION -> ActionResult(status = ActionResultStatus.SUCCESS, snapshot = beforeSnapshot)
            else -> ActionResult(status = ActionResultStatus.SUCCESS, snapshot = beforeSnapshot)
        }

        // 4. Evaluate WaitCondition if attached to action
        if (dispatchResult.status == ActionResultStatus.SUCCESS && action.waitCondition != null) {
            val waitRes = waitEngine.waitUntil(
                condition = action.waitCondition,
                service = service,
                screenProvider = screenObservationProvider,
                initialSignature = beforeStateSig
            )
            if (!waitRes.success) {
                Log.w(TAG, "ACTION_WAIT_CONDITION_FAILED: ${waitRes.failureReason}")
            }
        }

        val durationMs = System.currentTimeMillis() - startTime

        // 5. Capture After State & Compare
        val afterRoot = service.getRootNode()
        val afterSnapshot = ActionResolver.captureSnapshot(afterRoot, service.packageName ?: "")
        val afterStateSig = StateSignatureGenerator.generateSignature(afterSnapshot)

        val stateChange = compareStates(beforeStateSig, afterStateSig, verificationAction != null)

        // 6. Verify Result
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

        // 7. Persist Audit Record
        val auditRecord = ActionAuditRecord(
            timestamp = System.currentTimeMillis(),
            workflowId = workflowId,
            trigger = trigger.name,
            activePackage = beforeSnapshot.packageName,
            beforeStateSignature = beforeStateSig,
            actionType = action.type.name,
            targetIdentifier = redactedTarget,
            actionParametersSummary = "Type=${action.type}, Timeout=${action.timeoutMs}ms, Semantics=${action.semantics}",
            dispatchResult = dispatchResult.status.name,
            afterStateSignature = afterStateSig,
            stateChangeResult = stateChange.name,
            verificationStatus = verificationStatus.name,
            reason = dispatchResult.reason.name,
            success = isOverallSuccess,
            failureReason = if (!isOverallSuccess) dispatchResult.message ?: "Verification failed" else null,
            durationMs = durationMs
        )

        try {
            auditDao.insertAuditRecord(auditRecord)
            Log.i(TAG, "AUDIT_LOGGED: Action ${action.type} -> Success=$isOverallSuccess, Verification=$verificationStatus, Reason=${dispatchResult.reason}")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing action audit record", e)
        }

        return dispatchResult.copy(
            snapshot = afterSnapshot,
            message = if (!isOverallSuccess) dispatchResult.message ?: "Verification failed" else dispatchResult.message
        )
    }

    fun checkPreconditions(
        preconditions: List<ActionPrecondition>,
        snapshot: UiSnapshot
    ): PreconditionCheckResult {
        for (pre in preconditions) {
            val expected = pre.expectedValue ?: ""
            val satisfied = when (pre.type) {
                PreconditionType.PACKAGE_MATCH -> snapshot.packageName.equals(expected, ignoreCase = true)
                PreconditionType.TEXT_PRESENT -> snapshot.visibleTexts.any { it.contains(expected, ignoreCase = true) }
                PreconditionType.VIEW_ID_PRESENT -> snapshot.viewIds.any { it.endsWith(expected, ignoreCase = true) }
                PreconditionType.EDITABLE_PRESENT -> snapshot.editableNodes.isNotEmpty() || snapshot.allNodes.any { it.className?.contains("EditText", ignoreCase = true) == true }
                PreconditionType.SCROLLABLE_PRESENT -> snapshot.scrollableNodes.isNotEmpty()
                PreconditionType.AUTH_AUTHENTICATED -> actionResolver.detectAuthState(snapshot) == AuthState.AUTHENTICATED
            }

            if (!satisfied) {
                return PreconditionCheckResult(
                    success = false,
                    failureReason = "Precondition '${pre.type}' unsatisfied on package '${snapshot.packageName}' (Expected '$expected')"
                )
            }
        }
        return PreconditionCheckResult(success = true)
    }

    fun compareStates(beforeSig: String, afterSig: String, isVerificationExpected: Boolean): StateChangeResult {
        return when {
            beforeSig == afterSig -> StateChangeResult.NO_CHANGE
            isVerificationExpected -> StateChangeResult.EXPECTED_STATE_REACHED
            else -> StateChangeResult.STATE_CHANGED
        }
    }

    private fun performLaunchApp(appQuery: String?): ActionResult {
        if (appQuery.isNullOrBlank()) return ActionResult(status = ActionResultStatus.FAILED, message = "LAUNCH_APP requires target app name or package")
        val appRes = appResolver.resolveApplication(appQuery)

        return when (appRes.status) {
            AppResolutionStatus.SUCCESS -> {
                try {
                    val intent = appRes.launchIntent
                        ?: context.packageManager.getLaunchIntentForPackage(appRes.packageName!!)
                        ?.apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }

                    if (intent != null) {
                        context.startActivity(intent)
                        Log.i(TAG, "LAUNCHED_APP: Launched '${appRes.appLabel}' (${appRes.packageName})")
                        ActionResult(status = ActionResultStatus.SUCCESS)
                    } else {
                        ActionResult(status = ActionResultStatus.FAILED, reason = ExecutionReason.APP_NOT_INSTALLED, message = "No launch activity intent found for '${appRes.packageName}'")
                    }
                } catch (e: Exception) {
                    ActionResult(status = ActionResultStatus.FAILED, message = "Failed to launch '${appRes.packageName}': ${e.message}")
                }
            }
            AppResolutionStatus.APP_NOT_INSTALLED -> {
                ActionResult(status = ActionResultStatus.BLOCKED, reason = ExecutionReason.APP_NOT_INSTALLED, message = appRes.explanation)
            }
            AppResolutionStatus.AMBIGUOUS_APPLICATION -> {
                ActionResult(status = ActionResultStatus.BLOCKED, reason = ExecutionReason.AMBIGUOUS_APPLICATION, message = appRes.explanation)
            }
            else -> {
                ActionResult(status = ActionResultStatus.FAILED, message = appRes.explanation)
            }
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
            val res = actionResolver.resolveTargetWithAmbiguity(snap, targetText)
            if (res.isAmbiguous) {
                val redacted = redactSensitiveText(targetText)
                Log.w(TAG, "AMBIGUOUS_TARGET_BLOCKED: Target '$redacted' matched ${res.candidateCount} nodes during wait. Aborting dispatch.")
                return ActionResult(
                    status = ActionResultStatus.BLOCKED,
                    reason = ExecutionReason.AMBIGUOUS_TARGET,
                    message = "Target '$redacted' matched ${res.candidateCount} ambiguous UI candidates"
                )
            }
            if (res.match != null) {
                return ActionResult(status = ActionResultStatus.SUCCESS, matchedNode = res.match.node, matchMethod = res.match.matchMethod)
            }
            kotlinx.coroutines.delay(500L)
        }
        return ActionResult(status = ActionResultStatus.TIMEOUT, reason = ExecutionReason.TIMEOUT, message = "Timeout waiting for '$targetText'")
    }

    private fun performClickText(targetText: String?, snapshot: UiSnapshot): ActionResult {
        if (targetText.isNullOrBlank()) return ActionResult(status = ActionResultStatus.FAILED, message = "CLICK_TEXT requires target text")
        val res = actionResolver.resolveTargetWithAmbiguity(snapshot, targetText)

        if (res.match == null) {
            return ActionResult(status = ActionResultStatus.NOT_FOUND, reason = ExecutionReason.UI_NOT_FOUND, message = "Text '$targetText' not found")
        }

        // STRICT AMBIGUOUS TARGET SAFETY: Never blindly select candidate 0 when target is ambiguous!
        if (res.isAmbiguous) {
            val redacted = redactSensitiveText(targetText)
            Log.w(TAG, "AMBIGUOUS_TARGET_CLICK_BLOCKED: Target '$redacted' matched ${res.candidateCount} nodes. Execution strictly blocked.")
            return ActionResult(
                status = ActionResultStatus.BLOCKED,
                reason = ExecutionReason.AMBIGUOUS_TARGET,
                message = "Target '$redacted' is ambiguous (${res.candidateCount} candidate UI nodes matched)"
            )
        }

        val match = res.match

        if (!match.node.isEnabled) {
            return ActionResult(status = ActionResultStatus.BLOCKED, reason = ExecutionReason.PRECONDITION_FAILED, message = "Target '$targetText' is disabled")
        }

        val nodeRef = match.node.nodeRef as? AccessibilityNodeInfo
        if (nodeRef != null) {
            var targetNode: AccessibilityNodeInfo? = nodeRef
            while (targetNode != null && !targetNode.isClickable) {
                targetNode = targetNode.parent
            }
            if (targetNode != null && targetNode.isClickable) {
                if (targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return ActionResult(status = ActionResultStatus.SUCCESS, matchedNode = match.node, matchMethod = match.matchMethod)
                }
            }
        }
        return ActionResult(status = ActionResultStatus.FAILED, reason = ExecutionReason.UI_NOT_FOUND, message = "Node found for '$targetText' but click failed")
    }

    private fun performLongClick(targetText: String?, snapshot: UiSnapshot): ActionResult {
        if (targetText.isNullOrBlank()) return ActionResult(status = ActionResultStatus.FAILED, message = "LONG_CLICK requires target text")
        val res = actionResolver.resolveTargetWithAmbiguity(snapshot, targetText)

        if (res.match == null) {
            return ActionResult(status = ActionResultStatus.NOT_FOUND, reason = ExecutionReason.UI_NOT_FOUND, message = "Text '$targetText' not found")
        }

        if (res.isAmbiguous) {
            val redacted = redactSensitiveText(targetText)
            return ActionResult(
                status = ActionResultStatus.BLOCKED,
                reason = ExecutionReason.AMBIGUOUS_TARGET,
                message = "Long click target '$redacted' is ambiguous"
            )
        }

        val match = res.match
        if (!match.node.isEnabled) {
            return ActionResult(status = ActionResultStatus.BLOCKED, reason = ExecutionReason.PRECONDITION_FAILED, message = "Target '$targetText' is disabled")
        }

        val nodeRef = match.node.nodeRef as? AccessibilityNodeInfo
        if (nodeRef != null) {
            if (nodeRef.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
                return ActionResult(status = ActionResultStatus.SUCCESS, matchedNode = match.node, matchMethod = match.matchMethod)
            }
        }
        return ActionResult(status = ActionResultStatus.FAILED, reason = ExecutionReason.UI_NOT_FOUND, message = "Long click failed on target '$targetText'")
    }

    private fun performTypeText(targetLabel: String?, inputData: String?, snapshot: UiSnapshot): ActionResult {
        val textToType = inputData ?: targetLabel
        if (textToType.isNullOrBlank()) {
            return ActionResult(status = ActionResultStatus.FAILED, message = "TYPE_TEXT requires input text string")
        }

        val res = actionResolver.resolveEditableTarget(snapshot, targetLabel)
        if (res.match == null) {
            return ActionResult(status = ActionResultStatus.NOT_FOUND, reason = ExecutionReason.UI_NOT_FOUND, message = "No editable field found for TYPE_TEXT")
        }

        if (!res.match.node.isEnabled) {
            return ActionResult(status = ActionResultStatus.BLOCKED, reason = ExecutionReason.PRECONDITION_FAILED, message = "Editable input field is disabled")
        }

        val editableNode = res.match.node.nodeRef as? AccessibilityNodeInfo
            ?: return ActionResult(status = ActionResultStatus.FAILED, reason = ExecutionReason.UI_NOT_FOUND, message = "Editable node reference missing")

        // 1. Focus node if focusable
        if (!editableNode.isFocused) {
            editableNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            editableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        // 2. Inject text using Android Accessibility API
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
        }

        val success = editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        val redactedText = redactSensitiveText(textToType)

        return if (success) {
            Log.i(TAG, "TEXT_TYPED_SUCCESS: Injected '$redactedText' into editable field")
            ActionResult(status = ActionResultStatus.SUCCESS, matchedNode = res.match.node, matchMethod = res.match.matchMethod)
        } else {
            Log.e(TAG, "TEXT_TYPED_FAILED: Failed to set text on editable field")
            ActionResult(status = ActionResultStatus.FAILED, message = "Failed to inject text '$redactedText'")
        }
    }

    private fun performClearText(targetLabel: String?, snapshot: UiSnapshot): ActionResult {
        val res = actionResolver.resolveEditableTarget(snapshot, targetLabel)
        if (res.match == null) {
            return ActionResult(status = ActionResultStatus.NOT_FOUND, reason = ExecutionReason.UI_NOT_FOUND, message = "No editable field found for CLEAR_TEXT")
        }

        val editableNode = res.match.node.nodeRef as? AccessibilityNodeInfo
            ?: return ActionResult(status = ActionResultStatus.FAILED, reason = ExecutionReason.UI_NOT_FOUND, message = "Editable node reference missing")

        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }

        val success = editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        return if (success) {
            ActionResult(status = ActionResultStatus.SUCCESS, matchedNode = res.match.node, matchMethod = res.match.matchMethod)
        } else {
            ActionResult(status = ActionResultStatus.FAILED, message = "Failed to clear text on editable field")
        }
    }

    private fun performPressEnter(service: AutomationAccessibilityService, snapshot: UiSnapshot): ActionResult {
        // 1. Try finding visible Search / Enter / Go button on UI
        for (btnText in listOf("Search", "Go", "Enter", "Submit")) {
            val searchButtonRes = actionResolver.resolveTargetWithAmbiguity(snapshot, btnText)
            if (searchButtonRes.match != null && !searchButtonRes.isAmbiguous) {
                val nodeRef = searchButtonRes.match.node.nodeRef as? AccessibilityNodeInfo
                if (nodeRef != null) {
                    var targetNode: AccessibilityNodeInfo? = nodeRef
                    while (targetNode != null && !targetNode.isClickable) {
                        targetNode = targetNode.parent
                    }
                    if (targetNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                        return ActionResult(status = ActionResultStatus.SUCCESS, matchedNode = searchButtonRes.match.node, matchMethod = "SEARCH_BUTTON_CLICK")
                    }
                }
            }
        }

        // 2. Fallback to IME search or clicking focused view
        val focusedNode = snapshot.focusedNodes.firstOrNull()?.nodeRef as? AccessibilityNodeInfo
        if (focusedNode != null && !focusedNode.isEditable) {
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        return ActionResult(status = ActionResultStatus.SUCCESS, message = "PRESS_ENTER dispatched")
    }

    private suspend fun performScroll(service: AutomationAccessibilityService, snapshot: UiSnapshot, forward: Boolean, beforeStateSig: String): ActionResult {
        val scrollableNode = snapshot.scrollableNodes.firstOrNull()?.nodeRef as? AccessibilityNodeInfo
        if (scrollableNode != null) {
            val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            if (scrollableNode.performAction(action)) {
                // Post-scroll verification: Capture fresh snapshot to check for scroll progress
                kotlinx.coroutines.delay(500L)
                val afterRoot = service.getRootNode()
                val afterSnapshot = ActionResolver.captureSnapshot(afterRoot, service.packageName ?: "")
                val afterStateSig = StateSignatureGenerator.generateSignature(afterSnapshot)

                if (beforeStateSig == afterStateSig) {
                    Log.w(TAG, "SCROLL_NO_PROGRESS: Scroll action produced no UI state change")
                    return ActionResult(status = ActionResultStatus.FAILED, reason = ExecutionReason.STUCK, message = "Scroll action produced no state change (NO_PROGRESS)")
                }

                return ActionResult(status = ActionResultStatus.SUCCESS)
            }
        }
        return ActionResult(status = ActionResultStatus.FAILED, reason = ExecutionReason.UI_NOT_FOUND, message = "No scrollable container found for scroll")
    }

    private fun performGoBack(service: AutomationAccessibilityService): ActionResult {
        return if (service.performGoBack()) ActionResult(status = ActionResultStatus.SUCCESS)
        else ActionResult(status = ActionResultStatus.FAILED, message = "GO_BACK failed")
    }

    private fun performGlobalAction(service: AutomationAccessibilityService, actionId: Int, actionName: String): ActionResult {
        return if (service.performGlobalAction(actionId)) ActionResult(status = ActionResultStatus.SUCCESS, message = "Global action $actionName executed")
        else ActionResult(status = ActionResultStatus.FAILED, message = "Global action $actionName failed")
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

data class PreconditionCheckResult(
    val success: Boolean,
    val failureReason: String? = null
)
