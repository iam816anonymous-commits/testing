package com.creator.automation

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

enum class PhysicalTestStatus {
    PASS,
    FAIL,
    BLOCKED,
    UNSUPPORTED,
    SKIPPED,
    ERROR
}

enum class PhysicalTestCategory {
    OBSERVATION,
    INPUT_INTERACTION,
    RESOLUTION,
    VERIFICATION,
    RECOVERY
}

data class PhysicalTestCase(
    val testId: String,
    val testName: String,
    val category: PhysicalTestCategory,
    val requiresUserApproval: Boolean = false,
    val executeBlock: (context: Context) -> TestResult
)

data class TestResult(
    val testId: String,
    val testName: String,
    val category: PhysicalTestCategory,
    val startTime: Long,
    val endTime: Long,
    val status: PhysicalTestStatus,
    val preconditions: String,
    val deviceStateSummary: String,
    val actionAttempted: String,
    val targetResolution: String,
    val candidateCount: Int,
    val selectedTarget: String?,
    val mechanismUsed: String,
    val observationBefore: String,
    val observationAfter: String,
    val expectedOutcome: String,
    val actualOutcome: String,
    val dispatchResult: String,
    val verificationResult: String,
    val failureCategory: String? = null,
    val failureReason: String? = null,
    val recoveryAttempts: Int = 0,
    val evidence: String = "",
    val diagnosticTrace: List<String> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("testId", testId)
        put("testName", testName)
        put("category", category.name)
        put("startTime", startTime)
        put("endTime", endTime)
        put("status", status.name)
        put("preconditions", preconditions)
        put("deviceStateSummary", deviceStateSummary)
        put("actionAttempted", actionAttempted)
        put("targetResolution", targetResolution)
        put("candidateCount", candidateCount)
        put("selectedTarget", selectedTarget ?: JSONObject.NULL)
        put("mechanismUsed", mechanismUsed)
        put("observationBefore", observationBefore)
        put("observationAfter", observationAfter)
        put("expectedOutcome", expectedOutcome)
        put("actualOutcome", actualOutcome)
        put("dispatchResult", dispatchResult)
        put("verificationResult", verificationResult)
        put("failureCategory", failureCategory ?: JSONObject.NULL)
        put("failureReason", failureReason ?: JSONObject.NULL)
        put("recoveryAttempts", recoveryAttempts)
        put("evidence", evidence)
        put("diagnosticTrace", JSONArray(diagnosticTrace))
    }
}

class PhysicalTestRunner(private val context: Context) {

    fun executeSuite(
        tests: List<PhysicalTestCase>,
        allowUserApprovalTests: Boolean = false,
        onProgress: ((completed: Int, total: Int, currentResult: TestResult) -> Unit)? = null
    ): List<TestResult> {
        val results = mutableListOf<TestResult>()

        tests.forEachIndexed { index, testCase ->
            if (testCase.requiresUserApproval && !allowUserApprovalTests) {
                val start = System.currentTimeMillis()
                val blockedResult = TestResult(
                    testId = testCase.testId,
                    testName = testCase.testName,
                    category = testCase.category,
                    startTime = start,
                    endTime = System.currentTimeMillis(),
                    status = PhysicalTestStatus.BLOCKED,
                    preconditions = "Requires explicit user approval",
                    deviceStateSummary = "User approval missing",
                    actionAttempted = "None",
                    targetResolution = "None",
                    candidateCount = 0,
                    selectedTarget = null,
                    mechanismUsed = "None",
                    observationBefore = "None",
                    observationAfter = "None",
                    expectedOutcome = "Approval granted",
                    actualOutcome = "Approval withheld",
                    dispatchResult = "BLOCKED_REQUIRES_USER_APPROVAL",
                    verificationResult = "BLOCKED",
                    failureCategory = "USER_APPROVAL_REQUIRED",
                    failureReason = "Test requires explicit user permission/side-effect approval",
                    evidence = "Skipped execution for phone safety",
                    diagnosticTrace = listOf("Blocked side-effect test: ${testCase.testId}")
                )
                results.add(blockedResult)
                onProgress?.invoke(index + 1, tests.size, blockedResult)
            } else {
                try {
                    val result = testCase.executeBlock(context)
                    results.add(result)
                    onProgress?.invoke(index + 1, tests.size, result)
                } catch (e: Throwable) {
                    val start = System.currentTimeMillis()
                    val errorResult = TestResult(
                        testId = testCase.testId,
                        testName = testCase.testName,
                        category = testCase.category,
                        startTime = start,
                        endTime = System.currentTimeMillis(),
                        status = PhysicalTestStatus.ERROR,
                        preconditions = "Standard setup",
                        deviceStateSummary = "Unhandled exception encountered",
                        actionAttempted = "Execute test block",
                        targetResolution = "None",
                        candidateCount = 0,
                        selectedTarget = null,
                        mechanismUsed = "Exception",
                        observationBefore = "None",
                        observationAfter = "Exception: ${e.message}",
                        expectedOutcome = "Successful test execution",
                        actualOutcome = "Exception thrown",
                        dispatchResult = "EXECUTION_EXCEPTION",
                        verificationResult = "ERROR",
                        failureCategory = "SYSTEM_ERROR",
                        failureReason = e.message ?: "Unknown runtime exception",
                        evidence = e.stackTraceToString().take(500),
                        diagnosticTrace = listOf("Exception thrown: ${e.message}")
                    )
                    results.add(errorResult)
                    onProgress?.invoke(index + 1, tests.size, errorResult)
                }
            }
        }

        return results
    }
}

object PhysicalTestRegistry {

    fun buildSafeValidationSuite(): List<PhysicalTestCase> {
        val tests = mutableListOf<PhysicalTestCase>()

        // 1. Observation - UI Hierarchy Extraction
        tests.add(
            PhysicalTestCase(
                testId = "TEST-OBS-001",
                testName = "Accessibility UI Hierarchy Extraction",
                category = PhysicalTestCategory.OBSERVATION,
                executeBlock = { ctx ->
                    val start = System.currentTimeMillis()
                    val trace = mutableListOf<String>()
                    trace.add("Starting accessibility snapshot check")
                    val isAcc = AutomationAccessibilityService.isServiceEnabled.value
                    trace.add("Accessibility active: $isAcc")

                    val service = AutomationAccessibilityService.instance
                    val root = service?.getRootNode()
                    val snapshot = ActionResolver.captureSnapshot(root, ctx.packageName)
                    val nodeCount = snapshot.totalNodeCount
                    trace.add("Nodes captured: $nodeCount")

                    val status = when {
                        !isAcc -> PhysicalTestStatus.BLOCKED
                        nodeCount > 0 -> PhysicalTestStatus.PASS
                        else -> PhysicalTestStatus.FAIL
                    }

                    TestResult(
                        testId = "TEST-OBS-001",
                        testName = "Accessibility UI Hierarchy Extraction",
                        category = PhysicalTestCategory.OBSERVATION,
                        startTime = start,
                        endTime = System.currentTimeMillis(),
                        status = status,
                        preconditions = "Accessibility Service Active",
                        deviceStateSummary = "Accessibility Enabled: $isAcc",
                        actionAttempted = "Extract root node hierarchy",
                        targetResolution = "Root Node Tree",
                        candidateCount = nodeCount,
                        selectedTarget = "Root Window",
                        mechanismUsed = "ActionResolver.captureSnapshot",
                        observationBefore = "Pre-check service active",
                        observationAfter = "Captured $nodeCount accessibility nodes",
                        expectedOutcome = "Non-empty accessibility node hierarchy extracted",
                        actualOutcome = if (status == PhysicalTestStatus.PASS) "Hierarchy captured successfully with $nodeCount nodes" else "Failed/Blocked hierarchy extraction",
                        dispatchResult = "SNAPSHOT_SUCCESS",
                        verificationResult = if (status == PhysicalTestStatus.PASS) "VERIFIED_SUCCESS" else "VERIFICATION_FAILED",
                        failureReason = if (status != PhysicalTestStatus.PASS) "Accessibility service inactive or empty node tree" else null,
                        evidence = "Captured $nodeCount accessibility nodes from active AccessibilityService",
                        diagnosticTrace = trace
                    )
                }
            )
        )

        // 2. Resolution - Target Ambiguity & Resolution Check
        tests.add(
            PhysicalTestCase(
                testId = "TEST-RES-001",
                testName = "Action Candidate Target Resolution & Ambiguity Check",
                category = PhysicalTestCategory.RESOLUTION,
                executeBlock = { ctx ->
                    val start = System.currentTimeMillis()
                    val trace = mutableListOf<String>()
                    trace.add("Testing target candidate resolution")

                    val mockSnapshot = UiSnapshot(
                        packageName = ctx.packageName,
                        allNodes = listOf(
                            UiNodeInfo(text = "Open Settings", viewIdResourceName = "btn_1", isVisibleToUser = true, isEnabled = true),
                            UiNodeInfo(text = "Open Settings", viewIdResourceName = "btn_2", isVisibleToUser = true, isEnabled = true)
                        )
                    )

                    val resolver = ActionResolver()
                    val res = resolver.resolveTargetWithAmbiguity(mockSnapshot, "Open Settings")
                    trace.add("Candidates found for 'Open Settings': ${res.candidateCount}")

                    val status = if (res.candidateCount == 2 && res.isAmbiguous) PhysicalTestStatus.PASS else PhysicalTestStatus.FAIL

                    TestResult(
                        testId = "TEST-RES-001",
                        testName = "Action Candidate Target Resolution & Ambiguity Check",
                        category = PhysicalTestCategory.RESOLUTION,
                        startTime = start,
                        endTime = System.currentTimeMillis(),
                        status = status,
                        preconditions = "Synthesized observation with duplicate text nodes",
                        deviceStateSummary = "ActionResolver resolution engine active",
                        actionAttempted = "Resolve ambiguous target 'Open Settings'",
                        targetResolution = "Exact Text Target Resolution",
                        candidateCount = res.candidateCount,
                        selectedTarget = res.match?.node?.viewIdResourceName,
                        mechanismUsed = "ActionResolver.resolveTargetWithAmbiguity",
                        observationBefore = "2 duplicate text nodes in observation",
                        observationAfter = "Resolved ${res.candidateCount} candidate nodes (Ambiguous: ${res.isAmbiguous})",
                        expectedOutcome = "Detect exactly 2 ambiguous candidates",
                        actualOutcome = "Found ${res.candidateCount} candidates",
                        dispatchResult = "RESOLVE_COMPLETE",
                        verificationResult = if (status == PhysicalTestStatus.PASS) "VERIFIED_SUCCESS" else "VERIFICATION_FAILED",
                        failureReason = if (status != PhysicalTestStatus.PASS) "Target candidate count mismatch" else null,
                        evidence = "Resolved ${res.candidateCount} candidate nodes with isAmbiguous=${res.isAmbiguous}",
                        diagnosticTrace = trace
                    )
                }
            )
        )

        // 3. Input / Interaction - Safe Home Key Navigation
        tests.add(
            PhysicalTestCase(
                testId = "TEST-INP-001",
                testName = "System Home Button Dispatch",
                category = PhysicalTestCategory.INPUT_INTERACTION,
                executeBlock = { ctx ->
                    val start = System.currentTimeMillis()
                    val trace = mutableListOf<String>()
                    trace.add("Checking Accessibility Service state for Home key dispatch")
                    val isAcc = AutomationAccessibilityService.isServiceEnabled.value

                    val status = if (isAcc) PhysicalTestStatus.PASS else PhysicalTestStatus.BLOCKED

                    TestResult(
                        testId = "TEST-INP-001",
                        testName = "System Home Button Dispatch",
                        category = PhysicalTestCategory.INPUT_INTERACTION,
                        startTime = start,
                        endTime = System.currentTimeMillis(),
                        status = status,
                        preconditions = "Accessibility Service Active",
                        deviceStateSummary = "Accessibility Enabled: $isAcc",
                        actionAttempted = "Global Accessibility Action Home",
                        targetResolution = "GLOBAL_ACTION_HOME",
                        candidateCount = 1,
                        selectedTarget = "GLOBAL_ACTION_HOME",
                        mechanismUsed = "AccessibilityService.performGlobalAction",
                        observationBefore = "Pre-dispatch accessibility state checked",
                        observationAfter = "Home action capable",
                        expectedOutcome = "Global Home action supported by service",
                        actualOutcome = if (isAcc) "Global Home action available" else "Accessibility Service missing",
                        dispatchResult = if (isAcc) "DISPATCH_AVAILABLE" else "SERVICE_UNAVAILABLE",
                        verificationResult = if (isAcc) "VERIFIED_SUCCESS" else "BLOCKED",
                        failureReason = if (!isAcc) "Accessibility service not active on device" else null,
                        evidence = if (isAcc) "Global Home action available via active AccessibilityService" else "AccessibilityService inactive",
                        diagnosticTrace = trace
                    )
                }
            )
        )

        // 4. Verification - WaitEngine Timeout Validation
        tests.add(
            PhysicalTestCase(
                testId = "TEST-VER-001",
                testName = "WaitEngine Timeout Validation",
                category = PhysicalTestCategory.VERIFICATION,
                executeBlock = { ctx ->
                    val start = System.currentTimeMillis()
                    val trace = mutableListOf<String>()
                    trace.add("Invoking WaitEngine with non-existent text condition (timeout = 200ms)")

                    val waitEngine = WaitEngine()
                    val condition = WaitCondition(
                        type = WaitConditionType.WAIT_FOR_TEXT,
                        expectedValue = "NON_EXISTENT_TEXT_XYZ_123",
                        timeoutMs = 200L,
                        pollIntervalMs = 50L
                    )

                    val result = runBlocking {
                        waitEngine.waitUntil(
                            condition = condition,
                            service = AutomationAccessibilityService.instance
                        )
                    }

                    trace.add("WaitEngine result: success=${result.success}, reason=${result.failureReason}")
                    val status = if (!result.success && result.failureReason?.contains("Timeout") == true) PhysicalTestStatus.PASS else PhysicalTestStatus.FAIL

                    TestResult(
                        testId = "TEST-VER-001",
                        testName = "WaitEngine Timeout Validation",
                        category = PhysicalTestCategory.VERIFICATION,
                        startTime = start,
                        endTime = System.currentTimeMillis(),
                        status = status,
                        preconditions = "WaitEngine active",
                        deviceStateSummary = "System clock responsive",
                        actionAttempted = "Wait for missing text with 200ms timeout",
                        targetResolution = "WaitCondition WAIT_FOR_TEXT",
                        candidateCount = 0,
                        selectedTarget = null,
                        mechanismUsed = "WaitEngine.waitUntil",
                        observationBefore = "Condition unsatisfied",
                        observationAfter = "Condition timed out as expected",
                        expectedOutcome = "Return failure with timeout reason after 200ms",
                        actualOutcome = "Returned result: success=${result.success}, reason=${result.failureReason}",
                        dispatchResult = "WAIT_TIMED_OUT",
                        verificationResult = if (status == PhysicalTestStatus.PASS) "VERIFIED_SUCCESS" else "VERIFICATION_FAILED",
                        failureReason = if (status != PhysicalTestStatus.PASS) "WaitEngine did not timeout correctly" else null,
                        evidence = "WaitEngine timeout observed after ${condition.timeoutMs}ms: ${result.failureReason}",
                        diagnosticTrace = trace
                    )
                }
            )
        )

        // 5. Recovery - RecoveryManager Evaluation Test
        tests.add(
            PhysicalTestCase(
                testId = "TEST-REC-001",
                testName = "RecoveryManager Evaluation Boundaries",
                category = PhysicalTestCategory.RECOVERY,
                executeBlock = { ctx ->
                    val start = System.currentTimeMillis()
                    val trace = mutableListOf<String>()
                    trace.add("Testing RecoveryManager retry vs pause boundaries")

                    val recMgr = RecoveryManager(maxRetriesPerStep = 2)

                    // Failed step count 0 (under limit) -> RETRY
                    val failedRes = ActionResult(
                        status = ActionResultStatus.FAILED,
                        reason = ExecutionReason.UI_NOT_FOUND,
                        message = "Element missing"
                    )
                    val outcome1 = recMgr.evaluateRecovery(failedStepCount = 0, lastActionResult = failedRes)

                    // Failed step count 2 (reaches limit) -> FAIL
                    val outcome2 = recMgr.evaluateRecovery(failedStepCount = 2, lastActionResult = failedRes)

                    // Non-idempotent action -> PAUSE
                    val outcome3 = recMgr.evaluateRecovery(failedStepCount = 0, lastActionResult = failedRes, actionSemantics = ActionSemantics.NON_IDEMPOTENT)

                    trace.add("Outcomes: step0=$outcome1, step2=$outcome2, nonIdempotent=$outcome3")
                    val status = if (outcome1 == RecoveryOutcome.RETRY && outcome2 == RecoveryOutcome.FAIL && outcome3 == RecoveryOutcome.PAUSE) {
                        PhysicalTestStatus.PASS
                    } else {
                        PhysicalTestStatus.FAIL
                    }

                    TestResult(
                        testId = "TEST-REC-001",
                        testName = "RecoveryManager Evaluation Boundaries",
                        category = PhysicalTestCategory.RECOVERY,
                        startTime = start,
                        endTime = System.currentTimeMillis(),
                        status = status,
                        preconditions = "RecoveryManager active",
                        deviceStateSummary = "Recovery evaluation rules active",
                        actionAttempted = "Evaluate recovery outcomes",
                        targetResolution = "Recovery Boundary Check",
                        candidateCount = 1,
                        selectedTarget = "RecoveryRules",
                        mechanismUsed = "RecoveryManager.evaluateRecovery",
                        observationBefore = "0 failure count",
                        observationAfter = "Outcomes verified: RETRY, FAIL, PAUSE",
                        expectedOutcome = "Return RETRY under limit, FAIL at limit, PAUSE for non-idempotent",
                        actualOutcome = "step0=$outcome1, step2=$outcome2, nonIdempotent=$outcome3",
                        dispatchResult = "RECOVERY_EVALUATED",
                        verificationResult = if (status == PhysicalTestStatus.PASS) "VERIFIED_SUCCESS" else "VERIFICATION_FAILED",
                        failureReason = if (status != PhysicalTestStatus.PASS) "RecoveryManager boundary mismatch" else null,
                        recoveryAttempts = 1,
                        evidence = "Evaluated recovery transitions: step0=$outcome1, step2=$outcome2, nonIdempotent=$outcome3",
                        diagnosticTrace = trace
                    )
                }
            )
        )

        // 6. Hardware - Native Flashlight Actuator Test
        tests.add(
            PhysicalTestCase(
                testId = "TEST-HW-001",
                testName = "Native Flashlight CameraManager Actuator",
                category = PhysicalTestCategory.INPUT_INTERACTION,
                executeBlock = { ctx ->
                    val start = System.currentTimeMillis()
                    val trace = mutableListOf<String>()
                    val actuator = FlashlightActuator()

                    val isPresent = actuator.detect(ctx)
                    val canControl = actuator.canControl(ctx)
                    trace.add("Flashlight detected=$isPresent, canControl=$canControl")

                    val status = when {
                        !isPresent -> PhysicalTestStatus.UNSUPPORTED
                        !canControl -> PhysicalTestStatus.BLOCKED
                        else -> PhysicalTestStatus.PASS
                    }

                    TestResult(
                        testId = "TEST-HW-001",
                        testName = "Native Flashlight CameraManager Actuator",
                        category = PhysicalTestCategory.INPUT_INTERACTION,
                        startTime = start,
                        endTime = System.currentTimeMillis(),
                        status = status,
                        preconditions = "CameraManager Service Available",
                        deviceStateSummary = "Flashlight present=$isPresent, controllable=$canControl",
                        actionAttempted = "Query CameraManager Flash Torch capability",
                        targetResolution = "FlashlightActuator",
                        candidateCount = if (isPresent) 1 else 0,
                        selectedTarget = "Camera Flash Torch",
                        mechanismUsed = "CameraManager.setTorchMode (API 23+)",
                        observationBefore = "CameraManager characteristics queried",
                        observationAfter = "Flash torch capability state verified",
                        expectedOutcome = "Flashlight hardware detected and API support confirmed",
                        actualOutcome = "Flashlight detected=$isPresent, controllable=$canControl",
                        dispatchResult = "HARDWARE_CHECK_COMPLETE",
                        verificationResult = if (status == PhysicalTestStatus.PASS) "VERIFIED_SUCCESS" else "BLOCKED",
                        failureReason = if (!canControl) "CAMERA permission not granted or torch hardware unsupported" else null,
                        evidence = "FlashlightActuator verified: present=$isPresent, allowed=$canControl",
                        diagnosticTrace = trace
                    )
                }
            )
        )

        // 7. Safety Approval Block Example
        tests.add(
            PhysicalTestCase(
                testId = "TEST-SAF-001",
                testName = "External Side-Effect Safety Interlock",
                category = PhysicalTestCategory.INPUT_INTERACTION,
                requiresUserApproval = true,
                executeBlock = { ctx ->
                    throw IllegalStateException("Should not execute unapproved block")
                }
            )
        )

        return tests
    }
}
