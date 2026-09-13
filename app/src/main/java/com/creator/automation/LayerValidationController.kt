package com.creator.automation

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class ValidationTargetStatus {
    NONE,
    READY,
    STALE
}

enum class OverlayMenuView {
    MAIN_MENU,
    L3_TARGET,
    L4_TOUCH,
    L5_SCROLL,
    DETAILS,
    TRACE
}

data class RetainedTarget(
    val candidate: TargetCandidate,
    val sourceQuery: String,
    val creationStateSignature: String,
    val creationTimestamp: Long = System.currentTimeMillis(),
    val status: ValidationTargetStatus = ValidationTargetStatus.READY
)

data class ValidationLayerStatus(
    val l1Accessibility: String = "PASS",
    val l2Observation: String = "PASS",
    val l3TargetDiscovery: String = "PASS",
    val l4Touch: String = "AVAILABLE",
    val l5Scroll: String = "AVAILABLE",
    val l6Focus: String = "LOCKED",
    val l7TextInput: String = "LOCKED",
    val l8Submit: String = "LOCKED",
    val l9Navigation: String = "LOCKED"
)

data class LayerValidationTrace(
    val traceId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val layer: Int,
    val actionType: String,
    val foregroundPackage: String,
    val targetIdentifier: String?,
    val resolutionStatus: String,
    val mechanism: String,
    val dispatchAttempted: Boolean,
    val dispatchResult: String,
    val beforeStateSignature: String,
    val afterStateSignature: String,
    val verificationStatus: String,
    val isConfirmed: Boolean,
    val failureReason: String? = null
)

class LayerValidationController(
    private val actionResolver: ActionResolver = ActionResolver(),
    private val deviceActionExecutor: DeviceActionExecutor? = null
) {
    companion object {
        private const val TAG = "LayerValidationCtrl"

        private val _layerStatus = MutableStateFlow(ValidationLayerStatus())
        val layerStatus: StateFlow<ValidationLayerStatus> = _layerStatus.asStateFlow()

        private val _retainedTarget = MutableStateFlow<RetainedTarget?>(null)
        val retainedTarget: StateFlow<RetainedTarget?> = _retainedTarget.asStateFlow()

        private val _lastValidationTrace = MutableStateFlow<LayerValidationTrace?>(null)
        val lastValidationTrace: StateFlow<LayerValidationTrace?> = _lastValidationTrace.asStateFlow()

        private val _selectedScrollIndex = MutableStateFlow(0)
        val selectedScrollIndex: StateFlow<Int> = _selectedScrollIndex.asStateFlow()

        private val _selectedScrollDirection = MutableStateFlow("DOWN") // "DOWN" or "UP"
        val selectedScrollDirection: StateFlow<String> = _selectedScrollDirection.asStateFlow()

        private val _isOverlayExpanded = MutableStateFlow(false)
        val isOverlayExpanded: StateFlow<Boolean> = _isOverlayExpanded.asStateFlow()

        private val _currentMenuView = MutableStateFlow(OverlayMenuView.MAIN_MENU)
        val currentMenuView: StateFlow<OverlayMenuView> = _currentMenuView.asStateFlow()

        private val _activeSearchQuery = MutableStateFlow("Search")
        val activeSearchQuery: StateFlow<String> = _activeSearchQuery.asStateFlow()

        var instance: LayerValidationController? = null
            private set

        fun getOrCreateInstance(resolver: ActionResolver = ActionResolver(), executor: DeviceActionExecutor? = null): LayerValidationController {
            if (instance == null) {
                instance = LayerValidationController(resolver, executor)
            }
            return instance!!
        }

        fun setOverlayExpanded(expanded: Boolean) {
            _isOverlayExpanded.value = expanded
            if (!expanded) {
                _currentMenuView.value = OverlayMenuView.MAIN_MENU
            }
        }

        fun navigateMenuView(view: OverlayMenuView) {
            _currentMenuView.value = view
        }

        fun setActiveSearchQuery(query: String) {
            _activeSearchQuery.value = query
        }

        fun resetForTesting() {
            _retainedTarget.value = null
            _lastValidationTrace.value = null
            _selectedScrollIndex.value = 0
            _selectedScrollDirection.value = "DOWN"
            _isOverlayExpanded.value = false
            _currentMenuView.value = OverlayMenuView.MAIN_MENU
            _activeSearchQuery.value = "Search"
            _layerStatus.value = ValidationLayerStatus()
            instance = null
        }
    }

    init {
        instance = this
    }

    /**
     * Retains a resolved TargetCandidate as the active validation target.
     */
    fun selectAndRetainTarget(candidate: TargetCandidate, sourceQuery: String, currentSnapshot: UiSnapshot) {
        val currentSig = StateSignatureGenerator.generateSignature(currentSnapshot)
        val retained = RetainedTarget(
            candidate = candidate,
            sourceQuery = sourceQuery,
            creationStateSignature = currentSig,
            creationTimestamp = System.currentTimeMillis(),
            status = ValidationTargetStatus.READY
        )
        _retainedTarget.value = retained
        Log.i(TAG, "TARGET_RETAINED: '${candidate.text ?: candidate.contentDescription ?: candidate.viewId}' (query: '$sourceQuery', sig: $currentSig)")
    }

    /**
     * Checks whether the retained target is still valid against fresh screen observation.
     */
    fun evaluateTargetFreshness(currentSnapshot: UiSnapshot): ValidationTargetStatus {
        val retained = _retainedTarget.value ?: return ValidationTargetStatus.NONE
        val currentSig = StateSignatureGenerator.generateSignature(currentSnapshot)

        val updatedStatus = if (currentSig == retained.creationStateSignature) {
            ValidationTargetStatus.READY
        } else {
            // Screen signature shifted - check if candidate is still present in current snapshot
            val targetLabel = retained.candidate.text ?: retained.candidate.contentDescription ?: retained.sourceQuery
            val res = actionResolver.resolveTargetWithAmbiguity(currentSnapshot, targetLabel)
            if (res.match != null && !res.isAmbiguous) {
                ValidationTargetStatus.READY
            } else {
                ValidationTargetStatus.STALE
            }
        }

        if (retained.status != updatedStatus) {
            _retainedTarget.value = retained.copy(status = updatedStatus)
            Log.w(TAG, "RETAINED_TARGET_STATUS_SHIFT: ${retained.status} -> $updatedStatus (currentSig=$currentSig)")
        }

        return updatedStatus
    }

    fun setSelectedScrollIndex(index: Int) {
        _selectedScrollIndex.value = index
    }

    fun toggleScrollDirection() {
        _selectedScrollDirection.value = if (_selectedScrollDirection.value == "DOWN") "UP" else "DOWN"
    }

    fun clearRetainedTarget() {
        _retainedTarget.value = null
    }

    /**
     * Discovers a target on demand using production ActionResolver without side effects.
     */
    fun discoverTargetForValidation(snapshot: UiSnapshot, query: String): TargetCandidate? {
        val res = actionResolver.discoverTarget(snapshot, TargetRequest(
            requestedText = query,
            requestedContentDescription = query,
            requestedViewId = query
        ))
        return if (res.status == TargetResolutionStatus.FOUND_UNIQUE && res.match != null) {
            TargetCandidate(
                node = res.match.node,
                matchType = res.match.matchMethod,
                score = res.match.confidence
            )
        } else null
    }

    /**
     * Executes a Layer 4 (Touch / Click) physical test action using the production execution path.
     * Separates dispatch result from verification result.
     */
    suspend fun executeLayer4TouchTest(
        service: AutomationAccessibilityService
    ): LayerValidationTrace {
        val beforeRoot = service.getRootNode()
        val beforeSnapshot = ActionResolver.captureSnapshot(beforeRoot, service.packageName ?: "")
        val beforeSig = StateSignatureGenerator.generateSignature(beforeSnapshot)

        val retained = _retainedTarget.value
        if (retained == null) {
            val trace = LayerValidationTrace(
                layer = 4,
                actionType = "CLICK",
                foregroundPackage = beforeSnapshot.packageName,
                targetIdentifier = null,
                resolutionStatus = "NO_TARGET_SELECTED",
                mechanism = "NONE",
                dispatchAttempted = false,
                dispatchResult = "BLOCKED",
                beforeStateSignature = beforeSig,
                afterStateSignature = beforeSig,
                verificationStatus = "FAILED",
                isConfirmed = false,
                failureReason = "No target selected for Layer 4 validation"
            )
            _lastValidationTrace.value = trace
            return trace
        }

        val freshness = evaluateTargetFreshness(beforeSnapshot)
        if (freshness == ValidationTargetStatus.STALE) {
            val trace = LayerValidationTrace(
                layer = 4,
                actionType = "CLICK",
                foregroundPackage = beforeSnapshot.packageName,
                targetIdentifier = retained.candidate.text ?: retained.sourceQuery,
                resolutionStatus = "STALE_TARGET",
                mechanism = "NONE",
                dispatchAttempted = false,
                dispatchResult = "BLOCKED",
                beforeStateSignature = retained.creationStateSignature,
                afterStateSignature = beforeSig,
                verificationStatus = "FAILED",
                isConfirmed = false,
                failureReason = "Selected target is STALE - screen changed since selection"
            )
            _lastValidationTrace.value = trace
            return trace
        }

        val targetLabel = retained.candidate.text
            ?: retained.candidate.contentDescription
            ?: retained.candidate.viewId
            ?: retained.sourceQuery

        val executor = deviceActionExecutor ?: DeviceActionExecutor(service.applicationContext)
        val action = AutomationAction(
            type = ActionType.CLICK_TEXT,
            targetValue = targetLabel
        )

        val actionResult = executor.executeAndAudit(
            workflowId = "LAYER_4_VALIDATION_TEST",
            action = action,
            service = service,
            trigger = ExecutionTrigger.MANUAL
        )

        val afterSnapshot = actionResult.snapshot ?: ActionResolver.captureSnapshot(service.getRootNode(), service.packageName ?: "")
        val afterSig = StateSignatureGenerator.generateSignature(afterSnapshot)

        val isVerified = actionResult.message?.contains("VERIFIED_SUCCESS") == true ||
                beforeSig != afterSig ||
                beforeSnapshot.packageName != afterSnapshot.packageName

        val verificationStatusStr = when {
            isVerified -> "CONFIRMED"
            actionResult.status == ActionResultStatus.SUCCESS -> "NOT_CONFIRMED"
            else -> "FAILED"
        }

        val trace = LayerValidationTrace(
            layer = 4,
            actionType = "CLICK",
            foregroundPackage = beforeSnapshot.packageName,
            targetIdentifier = targetLabel,
            resolutionStatus = "FOUND_UNIQUE",
            mechanism = actionResult.matchMethod ?: "Accessibility ACTION_CLICK",
            dispatchAttempted = true,
            dispatchResult = actionResult.status.name,
            beforeStateSignature = beforeSig,
            afterStateSignature = afterSig,
            verificationStatus = verificationStatusStr,
            isConfirmed = isVerified,
            failureReason = if (!isVerified && actionResult.status == ActionResultStatus.SUCCESS) {
                "Click dispatched but no screen or state signature change detected"
            } else if (actionResult.status != ActionResultStatus.SUCCESS) {
                actionResult.message ?: "Click dispatch failed"
            } else null
        )

        _lastValidationTrace.value = trace
        Log.i(TAG, "LAYER_4_TRACE: target='$targetLabel', dispatch=${trace.dispatchResult}, verify=${trace.verificationStatus}, confirmed=${trace.isConfirmed}")
        return trace
    }

    /**
     * Executes a Layer 5 (Scroll) physical test action using the production execution path.
     * Evaluates scrollable candidates and separates dispatch result from state confirmation.
     */
    suspend fun executeLayer5ScrollTest(
        service: AutomationAccessibilityService
    ): LayerValidationTrace {
        val beforeRoot = service.getRootNode()
        val beforeSnapshot = ActionResolver.captureSnapshot(beforeRoot, service.packageName ?: "")
        val beforeSig = StateSignatureGenerator.generateSignature(beforeSnapshot)

        val scrollCandidates = beforeSnapshot.scrollableNodes
        if (scrollCandidates.isEmpty()) {
            val trace = LayerValidationTrace(
                layer = 5,
                actionType = "SCROLL",
                foregroundPackage = beforeSnapshot.packageName,
                targetIdentifier = null,
                resolutionStatus = "NO_SCROLLABLE_REGION",
                mechanism = "NONE",
                dispatchAttempted = false,
                dispatchResult = "BLOCKED",
                beforeStateSignature = beforeSig,
                afterStateSignature = beforeSig,
                verificationStatus = "FAILED",
                isConfirmed = false,
                failureReason = "No scrollable container or region found on active screen"
            )
            _lastValidationTrace.value = trace
            return trace
        }

        val idx = _selectedScrollIndex.value.coerceIn(0, scrollCandidates.lastIndex)
        val selectedCandidate = scrollCandidates[idx]
        val scrollDirection = _selectedScrollDirection.value // "DOWN" or "UP"
        val actionType = if (scrollDirection == "UP") ActionType.SCROLL_UP else ActionType.SCROLL_DOWN

        val executor = deviceActionExecutor ?: DeviceActionExecutor(service.applicationContext)
        val action = AutomationAction(type = actionType)

        val actionResult = executor.executeAndAudit(
            workflowId = "LAYER_5_VALIDATION_TEST",
            action = action,
            service = service,
            trigger = ExecutionTrigger.MANUAL
        )

        val afterSnapshot = actionResult.snapshot ?: ActionResolver.captureSnapshot(service.getRootNode(), service.packageName ?: "")
        val afterSig = StateSignatureGenerator.generateSignature(afterSnapshot)

        val textChanged = beforeSnapshot.visibleTexts.toSet() != afterSnapshot.visibleTexts.toSet()
        val isVerified = beforeSig != afterSig || textChanged

        val verificationStatusStr = when {
            isVerified -> "CONFIRMED"
            actionResult.status == ActionResultStatus.SUCCESS -> "NOT_CONFIRMED"
            else -> "FAILED"
        }

        val targetRegionLabel = selectedCandidate.viewIdResourceName
            ?: selectedCandidate.className
            ?: "Scroll Region #${idx + 1}"

        val trace = LayerValidationTrace(
            layer = 5,
            actionType = "SCROLL_${scrollDirection}",
            foregroundPackage = beforeSnapshot.packageName,
            targetIdentifier = targetRegionLabel,
            resolutionStatus = "FOUND_REGION",
            mechanism = "Accessibility ACTION_SCROLL / Gesture Swipe",
            dispatchAttempted = true,
            dispatchResult = actionResult.status.name,
            beforeStateSignature = beforeSig,
            afterStateSignature = afterSig,
            verificationStatus = verificationStatusStr,
            isConfirmed = isVerified,
            failureReason = if (!isVerified && actionResult.status == ActionResultStatus.SUCCESS) {
                "Scroll dispatched but no UI state signature or visible text movement detected"
            } else if (actionResult.status != ActionResultStatus.SUCCESS) {
                actionResult.message ?: "Scroll dispatch failed"
            } else null
        )

        _lastValidationTrace.value = trace
        Log.i(TAG, "LAYER_5_TRACE: region='$targetRegionLabel', direction=$scrollDirection, dispatch=${trace.dispatchResult}, verify=${trace.verificationStatus}, confirmed=${trace.isConfirmed}")
        return trace
    }
}
