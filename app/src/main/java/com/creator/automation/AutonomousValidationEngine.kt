package com.creator.automation

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class CapabilityStatus {
    FULLY_WORKING,
    WORKING_WITH_FALLBACK,
    PARTIALLY_WORKING,
    FAILED,
    INSUFFICIENT_EVIDENCE,
    BLOCKED
}

enum class SingleTestResultStatus {
    PASS,
    PARTIAL,
    FAIL,
    BLOCKED,
    NOT_APPLICABLE,
    SKIPPED
}

enum class TargetSafetyLevel {
    SAFE,
    CAUTION,
    DESTRUCTIVE,
    UNKNOWN
}

data class DispatchTruth(
    val acceptedBySystem: Boolean = false,
    val mechanismUsed: String = "NONE",
    val attemptCount: Int = 1,
    val details: String = ""
)

data class ObservationTruth(
    val uiStateChanged: Boolean = false,
    val textChanged: Boolean = false,
    val packageChanged: Boolean = false,
    val details: String = ""
)

data class GoalTruth(
    val goalAchieved: Boolean = false,
    val confidenceScore: Double = 0.0,
    val explanation: String = ""
)

data class ThreeDimensionTruth(
    val dispatchTruth: DispatchTruth = DispatchTruth(),
    val observationTruth: ObservationTruth = ObservationTruth(),
    val goalTruth: GoalTruth = GoalTruth()
) {
    val isFullPass: Boolean
        get() = dispatchTruth.acceptedBySystem && (observationTruth.uiStateChanged || observationTruth.textChanged || observationTruth.packageChanged) && goalTruth.goalAchieved
}

data class ValidationScenarioResult(
    val scenarioId: String,
    val scenarioName: String,
    val targetDescription: String?,
    val mechanismAttempted: String,
    val fallbackUsed: Boolean,
    val threeDimensionTruth: ThreeDimensionTruth,
    val status: SingleTestResultStatus,
    val failureCategory: String? = null,
    val failureReason: String? = null
)

data class CapabilityValidationSummary(
    val layer: Int,
    val capabilityName: String,
    val status: CapabilityStatus,
    val totalScenarios: Int,
    val passedScenarios: Int,
    val partialScenarios: Int,
    val failedScenarios: Int,
    val blockedScenarios: Int,
    val primaryMechanismStatus: String,
    val fallbackMechanismStatus: String,
    val scenarioResults: List<ValidationScenarioResult>
)

data class AutonomousValidationProgress(
    val isRunning: Boolean = false,
    val isCancelled: Boolean = false,
    val currentTestName: String = "Idle",
    val currentLayer: Int = 0,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val passCount: Int = 0,
    val failCount: Int = 0,
    val blockedCount: Int = 0,
    val lastSummaryReportPath: String? = null
)

class AutonomousValidationEngine(
    private val context: Context,
    private val actionResolver: ActionResolver = ActionResolver(),
    private val deviceActionExecutor: DeviceActionExecutor? = null,
    private val goalVerifier: GoalVerifier = GoalVerifier()
) {
    companion object {
        private const val TAG = "AutoValidationEngine"

        private val _progress = MutableStateFlow(AutonomousValidationProgress())
        val progress: StateFlow<AutonomousValidationProgress> = _progress.asStateFlow()

        private val _capabilitySummaries = MutableStateFlow<List<CapabilityValidationSummary>>(emptyList())
        val capabilitySummaries: StateFlow<List<CapabilityValidationSummary>> = _capabilitySummaries.asStateFlow()

        var instance: AutonomousValidationEngine? = null
            private set

        fun getOrCreateInstance(
            context: Context,
            resolver: ActionResolver = ActionResolver(),
            executor: DeviceActionExecutor? = null
        ): AutonomousValidationEngine {
            if (instance == null) {
                instance = AutonomousValidationEngine(context.applicationContext, resolver, executor)
            }
            return instance!!
        }

        fun resetForTesting() {
            _progress.value = AutonomousValidationProgress()
            _capabilitySummaries.value = emptyList()
            instance = null
        }
    }

    private val engineScope = CoroutineScope(Dispatchers.IO)
    private var activeJob: Job? = null

    /**
     * Evaluates whether a UI node / target string represents a destructive or high-risk operation.
     */
    fun classifyTargetSafety(targetText: String?, className: String? = null): TargetSafetyLevel {
        if (targetText == null) return TargetSafetyLevel.SAFE
        val lower = targetText.lowercase()

        val destructiveKeywords = listOf(
            "delete", "uninstall", "remove", "clear data", "factory reset",
            "purchase", "buy", "pay", "checkout", "transfer", "publish",
            "sign out", "logout", "forget account", "format"
        )
        if (destructiveKeywords.any { lower.contains(it) }) {
            return TargetSafetyLevel.DESTRUCTIVE
        }

        val cautionKeywords = listOf(
            "settings", "permission", "password", "pin", "security", "grant"
        )
        if (cautionKeywords.any { lower.contains(it) }) {
            return TargetSafetyLevel.CAUTION
        }

        return TargetSafetyLevel.SAFE
    }

    /**
     * Starts the autonomous on-device validation suite across Layers 3 to 8.
     */
    fun startAutonomousValidation(service: AutomationAccessibilityService) {
        if (_progress.value.isRunning) {
            Log.w(TAG, "Validation suite already running.")
            return
        }

        _progress.value = AutonomousValidationProgress(
            isRunning = true,
            isCancelled = false,
            currentTestName = "Initializing Validation Suite",
            currentLayer = 3,
            completedCount = 0,
            totalCount = 18,
            passCount = 0,
            failCount = 0,
            blockedCount = 0
        )

        activeJob = engineScope.launch {
            runAutonomousSuiteInternal(service)
        }
    }

    /**
     * Instantly stops the active autonomous validation run and resets the runtime to IDLE.
     */
    fun stopValidation() {
        Log.i(TAG, "STOP_VALIDATION_REQUESTED")
        activeJob?.cancel()
        activeJob = null
        _progress.value = _progress.value.copy(
            isRunning = false,
            isCancelled = true,
            currentTestName = "Validation Cancelled by User"
        )
    }

    private suspend fun runAutonomousSuiteInternal(service: AutomationAccessibilityService) {
        val summaries = mutableListOf<CapabilityValidationSummary>()
        val dateFolder = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val outputDir = File(context.filesDir, "physical_validation/$dateFolder/full_autonomous_validation")
        outputDir.mkdirs()

        try {
            // L3: Target Discovery Validation
            updateProgress("L3: Target Discovery (Multi-Scenario)", 3)
            val l3Summary = validateLayer3TargetDiscovery(service)
            summaries.add(l3Summary)
            if (checkCancelled()) return

            // L4: Touch Execution Validation
            updateProgress("L4: Touch Execution (Multi-Scenario)", 4)
            val l4Summary = validateLayer4Touch(service)
            summaries.add(l4Summary)
            if (checkCancelled()) return

            // L5: Scroll Execution Validation
            updateProgress("L5: Multi-Movement Scroll", 5)
            val l5Summary = validateLayer5Scroll(service)
            summaries.add(l5Summary)
            if (checkCancelled()) return

            // L6: Focus Truth Validation
            updateProgress("L6: Focus Truth", 6)
            val l6Summary = validateLayer6Focus(service)
            summaries.add(l6Summary)
            if (checkCancelled()) return

            // Dependency check for L7 & L8
            val l6Passed = l6Summary.status == CapabilityStatus.FULLY_WORKING || l6Summary.status == CapabilityStatus.WORKING_WITH_FALLBACK

            // L7: Text Input Validation
            updateProgress("L7: Text Input Truth", 7)
            val l7Summary = if (l6Passed) {
                validateLayer7Input(service)
            } else {
                createBlockedCapabilitySummary(7, "Layer 7 Text Input", "Blocked by Layer 6 Focus Failure")
            }
            summaries.add(l7Summary)
            if (checkCancelled()) return

            val l7Passed = l7Summary.status == CapabilityStatus.FULLY_WORKING || l7Summary.status == CapabilityStatus.WORKING_WITH_FALLBACK

            // L8: Submit / Search Truth Validation
            updateProgress("L8: Search & Submit Truth", 8)
            val l8Summary = if (l6Passed && l7Passed) {
                validateLayer8Submit(service)
            } else {
                createBlockedCapabilitySummary(8, "Layer 8 Submit", "Blocked by Layer 6/7 Precondition Failure")
            }
            summaries.add(l8Summary)

            _capabilitySummaries.value = summaries

            // Export comprehensive JSON and Markdown reports
            val reportPath = exportValidationReports(outputDir, summaries)

            _progress.value = _progress.value.copy(
                isRunning = false,
                currentTestName = "Validation Complete",
                lastSummaryReportPath = reportPath
            )
            Log.i(TAG, "AUTONOMOUS_VALIDATION_COMPLETE: Reports saved to $reportPath")
        } catch (e: Exception) {
            Log.e(TAG, "Error during autonomous validation execution", e)
            _progress.value = _progress.value.copy(
                isRunning = false,
                currentTestName = "Error: ${e.message}"
            )
        }
    }

    private fun checkCancelled(): Boolean {
        if (_progress.value.isCancelled) {
            Log.w(TAG, "Execution job cancelled.")
            return true
        }
        return false
    }

    private fun updateProgress(testName: String, layer: Int) {
        val current = _progress.value
        _progress.value = current.copy(
            currentTestName = testName,
            currentLayer = layer
        )
    }

    private fun incrementCounters(pass: Boolean, blocked: Boolean = false) {
        val current = _progress.value
        _progress.value = current.copy(
            completedCount = current.completedCount + 1,
            passCount = if (pass && !blocked) current.passCount + 1 else current.passCount,
            failCount = if (!pass && !blocked) current.failCount + 1 else current.failCount,
            blockedCount = if (blocked) current.blockedCount + 1 else current.blockedCount
        )
    }

    // --- LAYER VALIDATION SUITES ---

    private suspend fun validateLayer3TargetDiscovery(service: AutomationAccessibilityService): CapabilityValidationSummary {
        val snapshot = service.refreshCurrentScreenObservation()
        val surfaces = actionResolver.discoverInteractionSurfaces(snapshot)

        val scenarioResults = mutableListOf<ValidationScenarioResult>()

        // Scenario 1: Exact Visible Text Target
        val textSurface = surfaces.firstOrNull { !it.text.isNullOrBlank() }
        if (textSurface != null) {
            val q = textSurface.text!!
            val res = actionResolver.discoverTarget(snapshot, TargetRequest(requestedText = q))
            val isPass = res.status == TargetResolutionStatus.FOUND_UNIQUE && !res.isAmbiguous
            scenarioResults.add(
                ValidationScenarioResult(
                    scenarioId = "L3-SCEN-1",
                    scenarioName = "Exact Visible Text Target",
                    targetDescription = q,
                    mechanismAttempted = "ActionResolver.discoverTarget (Read-Only)",
                    fallbackUsed = false,
                    threeDimensionTruth = ThreeDimensionTruth(
                        dispatchTruth = DispatchTruth(acceptedBySystem = true, mechanismUsed = "READ_ONLY_INSPECTION"),
                        observationTruth = ObservationTruth(uiStateChanged = false, details = "Read-Only Target Resolution"),
                        goalTruth = GoalTruth(goalAchieved = isPass, confidenceScore = res.match?.confidence ?: 0.0)
                    ),
                    status = if (isPass) SingleTestResultStatus.PASS else SingleTestResultStatus.FAIL
                )
            )
            incrementCounters(isPass)
        } else {
            scenarioResults.add(createInsufficientScenario("L3-SCEN-1", "Exact Visible Text Target"))
        }

        // Scenario 2: View ID Resource Target
        val viewIdSurface = surfaces.firstOrNull { !it.viewId.isNullOrBlank() }
        if (viewIdSurface != null) {
            val q = viewIdSurface.viewId!!
            val res = actionResolver.discoverTarget(snapshot, TargetRequest(requestedViewId = q))
            val isPass = res.status == TargetResolutionStatus.FOUND_UNIQUE
            scenarioResults.add(
                ValidationScenarioResult(
                    scenarioId = "L3-SCEN-2",
                    scenarioName = "View ID Resource Target",
                    targetDescription = q,
                    mechanismAttempted = "ActionResolver.discoverTarget (View ID)",
                    fallbackUsed = false,
                    threeDimensionTruth = ThreeDimensionTruth(
                        dispatchTruth = DispatchTruth(acceptedBySystem = true, mechanismUsed = "READ_ONLY_VIEW_ID"),
                        observationTruth = ObservationTruth(uiStateChanged = false),
                        goalTruth = GoalTruth(goalAchieved = isPass)
                    ),
                    status = if (isPass) SingleTestResultStatus.PASS else SingleTestResultStatus.FAIL
                )
            )
            incrementCounters(isPass)
        } else {
            scenarioResults.add(createInsufficientScenario("L3-SCEN-2", "View ID Resource Target"))
        }

        // Scenario 3: Role / Class Target Ambiguity
        val roleRes = actionResolver.discoverTarget(snapshot, TargetRequest(requestedRole = "Button"))
        val rolePass = roleRes.status == TargetResolutionStatus.AMBIGUOUS || roleRes.status == TargetResolutionStatus.FOUND_UNIQUE
        scenarioResults.add(
            ValidationScenarioResult(
                scenarioId = "L3-SCEN-3",
                scenarioName = "Role / Class Ambiguity Check",
                targetDescription = "Button Role",
                mechanismAttempted = "ActionResolver.discoverTarget (Role)",
                fallbackUsed = false,
                threeDimensionTruth = ThreeDimensionTruth(
                    dispatchTruth = DispatchTruth(acceptedBySystem = true, mechanismUsed = "ROLE_INSPECTION"),
                    observationTruth = ObservationTruth(uiStateChanged = false),
                    goalTruth = GoalTruth(goalAchieved = rolePass)
                ),
                status = if (rolePass) SingleTestResultStatus.PASS else SingleTestResultStatus.FAIL
            )
        )
        incrementCounters(rolePass)

        return aggregateCapabilitySummary(3, "L3 Target Discovery", scenarioResults)
    }

    private suspend fun validateLayer4Touch(service: AutomationAccessibilityService): CapabilityValidationSummary {
        val snapshot = service.refreshCurrentScreenObservation()
        val safeSurfaces = actionResolver.discoverInteractionSurfaces(snapshot).filter {
            it.isClickable && classifyTargetSafety(it.text ?: it.contentDescription) == TargetSafetyLevel.SAFE
        }

        val scenarioResults = mutableListOf<ValidationScenarioResult>()

        for (idx in 0 until 3) {
            val surf = safeSurfaces.getOrNull(idx)
            if (surf != null) {
                val label = surf.text ?: surf.contentDescription ?: surf.viewId ?: "Target #${idx + 1}"
                val controller = LayerValidationController.getOrCreateInstance(actionResolver, deviceActionExecutor)
                controller.selectAndRetainTarget(
                    TargetCandidate(node = UiNodeInfo(text = label, isClickable = true), matchType = "EXACT_TEXT"),
                    label,
                    snapshot
                )
                val trace = controller.executeLayer4TouchTest(service)
                val isPass = trace.isConfirmed && trace.dispatchResult == "SUCCESS"

                scenarioResults.add(
                    ValidationScenarioResult(
                        scenarioId = "L4-SCEN-${idx + 1}",
                        scenarioName = "Touch Scenario #${idx + 1} ($label)",
                        targetDescription = label,
                        mechanismAttempted = trace.mechanism,
                        fallbackUsed = trace.mechanism.contains("GESTURE"),
                        threeDimensionTruth = ThreeDimensionTruth(
                            dispatchTruth = DispatchTruth(acceptedBySystem = trace.dispatchAttempted, mechanismUsed = trace.mechanism),
                            observationTruth = ObservationTruth(uiStateChanged = trace.beforeStateSignature != trace.afterStateSignature),
                            goalTruth = GoalTruth(goalAchieved = trace.isConfirmed, explanation = trace.verificationStatus)
                        ),
                        status = if (isPass) SingleTestResultStatus.PASS else SingleTestResultStatus.FAIL,
                        failureReason = trace.failureReason
                    )
                )
                incrementCounters(isPass)
            } else {
                scenarioResults.add(createInsufficientScenario("L4-SCEN-${idx + 1}", "Touch Scenario #${idx + 1}"))
            }
        }

        return aggregateCapabilitySummary(4, "L4 Touch Execution", scenarioResults)
    }

    private suspend fun validateLayer5Scroll(service: AutomationAccessibilityService): CapabilityValidationSummary {
        val scenarioResults = mutableListOf<ValidationScenarioResult>()
        val controller = LayerValidationController.getOrCreateInstance(actionResolver, deviceActionExecutor)

        // Scenario 1: Multi-movement Scroll Down (DOWN #1, #2, #3)
        var downPassCount = 0
        for (i in 1..3) {
            controller.setSelectedScrollIndex(0)
            val trace = controller.executeLayer5ScrollTest(service)
            if (trace.isConfirmed) downPassCount++
            kotlinx.coroutines.delay(300L)
        }
        val downPass = downPassCount > 0
        scenarioResults.add(
            ValidationScenarioResult(
                scenarioId = "L5-SCEN-1",
                scenarioName = "Multi-movement Scroll Down",
                targetDescription = "Primary Scroll Container",
                mechanismAttempted = "Accessibility ACTION_SCROLL / Gesture Swipe",
                fallbackUsed = false,
                threeDimensionTruth = ThreeDimensionTruth(
                    dispatchTruth = DispatchTruth(acceptedBySystem = true, mechanismUsed = "SCROLL_DOWN"),
                    observationTruth = ObservationTruth(uiStateChanged = downPass),
                    goalTruth = GoalTruth(goalAchieved = downPass, explanation = "Passed $downPassCount/3 Down scrolls")
                ),
                status = if (downPass) SingleTestResultStatus.PASS else SingleTestResultStatus.FAIL
            )
        )
        incrementCounters(downPass)

        // Scenario 2: Multi-movement Scroll Up (UP #1, #2, #3)
        controller.toggleScrollDirection() // Set to UP
        var upPassCount = 0
        for (i in 1..3) {
            val trace = controller.executeLayer5ScrollTest(service)
            if (trace.isConfirmed) upPassCount++
            kotlinx.coroutines.delay(300L)
        }
        val upPass = upPassCount > 0
        scenarioResults.add(
            ValidationScenarioResult(
                scenarioId = "L5-SCEN-2",
                scenarioName = "Multi-movement Scroll Up",
                targetDescription = "Primary Scroll Container",
                mechanismAttempted = "Accessibility ACTION_SCROLL / Gesture Swipe",
                fallbackUsed = false,
                threeDimensionTruth = ThreeDimensionTruth(
                    dispatchTruth = DispatchTruth(acceptedBySystem = true, mechanismUsed = "SCROLL_UP"),
                    observationTruth = ObservationTruth(uiStateChanged = upPass),
                    goalTruth = GoalTruth(goalAchieved = upPass, explanation = "Passed $upPassCount/3 Up scrolls")
                ),
                status = if (upPass) SingleTestResultStatus.PASS else SingleTestResultStatus.FAIL
            )
        )
        incrementCounters(upPass)

        return aggregateCapabilitySummary(5, "L5 Multi-Movement Scroll", scenarioResults)
    }

    private suspend fun validateLayer6Focus(service: AutomationAccessibilityService): CapabilityValidationSummary {
        val scenarioResults = mutableListOf<ValidationScenarioResult>()
        val controller = LayerValidationController.getOrCreateInstance(actionResolver, deviceActionExecutor)

        val trace = controller.executeLayer6FocusTest(service)
        val isPass = trace.isConfirmed || trace.dispatchResult == "SUCCESS"

        scenarioResults.add(
            ValidationScenarioResult(
                scenarioId = "L6-SCEN-1",
                scenarioName = "Editable Target Focus Request",
                targetDescription = trace.targetIdentifier ?: "Active Field",
                mechanismAttempted = trace.mechanism,
                fallbackUsed = false,
                threeDimensionTruth = ThreeDimensionTruth(
                    dispatchTruth = DispatchTruth(acceptedBySystem = trace.dispatchAttempted, mechanismUsed = trace.mechanism),
                    observationTruth = ObservationTruth(uiStateChanged = trace.beforeStateSignature != trace.afterStateSignature),
                    goalTruth = GoalTruth(goalAchieved = isPass, explanation = trace.verificationStatus)
                ),
                status = if (isPass) SingleTestResultStatus.PASS else SingleTestResultStatus.FAIL
            )
        )
        incrementCounters(isPass)

        return aggregateCapabilitySummary(6, "L6 Focus Truth", scenarioResults)
    }

    private suspend fun validateLayer7Input(service: AutomationAccessibilityService): CapabilityValidationSummary {
        val scenarioResults = mutableListOf<ValidationScenarioResult>()
        val controller = LayerValidationController.getOrCreateInstance(actionResolver, deviceActionExecutor)

        val trace = controller.executeLayer7InputTest(service, "Autonomous Validation Test")
        val isPass = trace.isConfirmed || trace.dispatchResult == "SUCCESS"

        scenarioResults.add(
            ValidationScenarioResult(
                scenarioId = "L7-SCEN-1",
                scenarioName = "Generic ACTION_SET_TEXT Injection",
                targetDescription = trace.targetIdentifier ?: "Active Field",
                mechanismAttempted = trace.mechanism,
                fallbackUsed = false,
                threeDimensionTruth = ThreeDimensionTruth(
                    dispatchTruth = DispatchTruth(acceptedBySystem = trace.dispatchAttempted, mechanismUsed = trace.mechanism),
                    observationTruth = ObservationTruth(textChanged = isPass),
                    goalTruth = GoalTruth(goalAchieved = isPass, explanation = trace.verificationStatus)
                ),
                status = if (isPass) SingleTestResultStatus.PASS else SingleTestResultStatus.FAIL
            )
        )
        incrementCounters(isPass)

        return aggregateCapabilitySummary(7, "L7 Text Input Truth", scenarioResults)
    }

    private suspend fun validateLayer8Submit(service: AutomationAccessibilityService): CapabilityValidationSummary {
        val scenarioResults = mutableListOf<ValidationScenarioResult>()
        val controller = LayerValidationController.getOrCreateInstance(actionResolver, deviceActionExecutor)

        val trace = controller.executeLayer8SubmitTest(service)
        val isPass = trace.isConfirmed || trace.dispatchResult == "SUCCESS"

        scenarioResults.add(
            ValidationScenarioResult(
                scenarioId = "L8-SCEN-1",
                scenarioName = "Semantic Control / IME Submit Discovery",
                targetDescription = trace.targetIdentifier ?: "Submit Control",
                mechanismAttempted = trace.mechanism,
                fallbackUsed = false,
                threeDimensionTruth = ThreeDimensionTruth(
                    dispatchTruth = DispatchTruth(acceptedBySystem = trace.dispatchAttempted, mechanismUsed = trace.mechanism),
                    observationTruth = ObservationTruth(uiStateChanged = trace.beforeStateSignature != trace.afterStateSignature),
                    goalTruth = GoalTruth(goalAchieved = isPass, explanation = trace.verificationStatus)
                ),
                status = if (isPass) SingleTestResultStatus.PASS else SingleTestResultStatus.FAIL
            )
        )
        incrementCounters(isPass)

        return aggregateCapabilitySummary(8, "L8 Search & Submit Truth", scenarioResults)
    }

    // --- HELPER AGGREGATION & REPORT EXPORTER ---

    private fun createInsufficientScenario(id: String, name: String): ValidationScenarioResult {
        return ValidationScenarioResult(
            scenarioId = id,
            scenarioName = name,
            targetDescription = "No suitable target found in active window",
            mechanismAttempted = "NONE",
            fallbackUsed = false,
            threeDimensionTruth = ThreeDimensionTruth(),
            status = SingleTestResultStatus.NOT_APPLICABLE,
            failureReason = "INSUFFICIENT_SCENARIOS"
        )
    }

    private fun createBlockedCapabilitySummary(layer: Int, name: String, reason: String): CapabilityValidationSummary {
        val blockedScenario = ValidationScenarioResult(
            scenarioId = "L${layer}-BLOCKED",
            scenarioName = "Dependency Blocked",
            targetDescription = null,
            mechanismAttempted = "NONE",
            fallbackUsed = false,
            threeDimensionTruth = ThreeDimensionTruth(),
            status = SingleTestResultStatus.BLOCKED,
            failureReason = reason
        )
        incrementCounters(pass = false, blocked = true)
        return CapabilityValidationSummary(
            layer = layer,
            capabilityName = name,
            status = CapabilityStatus.BLOCKED,
            totalScenarios = 1,
            passedScenarios = 0,
            partialScenarios = 0,
            failedScenarios = 0,
            blockedScenarios = 1,
            primaryMechanismStatus = "BLOCKED",
            fallbackMechanismStatus = "BLOCKED",
            scenarioResults = listOf(blockedScenario)
        )
    }

    private fun aggregateCapabilitySummary(
        layer: Int,
        name: String,
        results: List<ValidationScenarioResult>
    ): CapabilityValidationSummary {
        val passCount = results.count { it.status == SingleTestResultStatus.PASS }
        val partialCount = results.count { it.status == SingleTestResultStatus.PARTIAL }
        val failCount = results.count { it.status == SingleTestResultStatus.FAIL }
        val blockedCount = results.count { it.status == SingleTestResultStatus.BLOCKED || it.status == SingleTestResultStatus.NOT_APPLICABLE }

        val capStatus = when {
            passCount == results.size && results.none { it.fallbackUsed } -> CapabilityStatus.FULLY_WORKING
            passCount > 0 && results.any { it.fallbackUsed } -> CapabilityStatus.WORKING_WITH_FALLBACK
            passCount > 0 || partialCount > 0 -> CapabilityStatus.PARTIALLY_WORKING
            blockedCount == results.size -> CapabilityStatus.BLOCKED
            else -> CapabilityStatus.FAILED
        }

        return CapabilityValidationSummary(
            layer = layer,
            capabilityName = name,
            status = capStatus,
            totalScenarios = results.size,
            passedScenarios = passCount,
            partialScenarios = partialCount,
            failedScenarios = failCount,
            blockedScenarios = blockedCount,
            primaryMechanismStatus = if (passCount > 0) "WORKING" else "FAILED",
            fallbackMechanismStatus = if (results.any { it.fallbackUsed }) "WORKING" else "UNTESTED",
            scenarioResults = results
        )
    }

    private fun exportValidationReports(outputDir: File, summaries: List<CapabilityValidationSummary>): String {
        val summaryJson = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("device", "TECNO IN6 (Android 8.1 API 27)")
            val arr = JSONArray()
            summaries.forEach { s ->
                val capObj = JSONObject().apply {
                    put("layer", s.layer)
                    put("capability", s.capabilityName)
                    put("status", s.status.name)
                    put("passedScenarios", s.passedScenarios)
                    put("totalScenarios", s.totalScenarios)
                }
                arr.put(capObj)
            }
            put("summaries", arr)
        }

        val jsonFile = File(outputDir, "VALIDATION_RESULTS.json")
        jsonFile.writeText(summaryJson.toString(2))

        val mdContent = StringBuilder().apply {
            append("# FULL AUTONOMOUS ON-DEVICE VALIDATION REPORT\n\n")
            append("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
            append("Target Device: TECNO IN6 / Android 8.1 / API 27\n\n")
            append("## Capability Summary\n\n")
            summaries.forEach { s ->
                append("### Layer ${s.layer}: ${s.capabilityName}\n")
                append("- Status: **${s.status.name}**\n")
                append("- Scenarios: ${s.passedScenarios}/${s.totalScenarios} Passed\n")
                append("- Primary Mechanism: ${s.primaryMechanismStatus}\n")
                append("- Fallback Mechanism: ${s.fallbackMechanismStatus}\n\n")
            }
        }.toString()

        val mdFile = File(outputDir, "VALIDATION_SUMMARY.md")
        mdFile.writeText(mdContent)

        return mdFile.absolutePath
    }
}
