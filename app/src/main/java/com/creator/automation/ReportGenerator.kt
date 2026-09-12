package com.creator.automation

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ReportGenerator(private val context: Context) {

    fun generateAndExportReports(
        deviceProfile: DeviceProfile,
        testResults: List<TestResult>
    ): Map<String, File> {
        val outputDir = File(context.getExternalFilesDir(null), "physical_validation").apply {
            if (!exists()) mkdirs()
        }

        val generatedFiles = mutableMapOf<String, File>()

        // 1. DEVICE_PROFILE.json
        val deviceProfileFile = File(outputDir, "DEVICE_PROFILE.json")
        deviceProfileFile.writeText(deviceProfile.toJson().toString(2))
        generatedFiles["DEVICE_PROFILE.json"] = deviceProfileFile

        // 2. CAPABILITIES.md
        val capabilitiesMdFile = File(outputDir, "CAPABILITIES.md")
        capabilitiesMdFile.writeText(renderCapabilitiesMarkdown(deviceProfile))
        generatedFiles["CAPABILITIES.md"] = capabilitiesMdFile

        // 3. SENSOR_INVENTORY.json
        val sensorInventoryFile = File(outputDir, "SENSOR_INVENTORY.json")
        val sensorArray = JSONArray(deviceProfile.sensors.map { it.toJson() })
        sensorInventoryFile.writeText(sensorArray.toString(2))
        generatedFiles["SENSOR_INVENTORY.json"] = sensorInventoryFile

        // 4. ACTUATOR_INVENTORY.json
        val actuatorInventoryFile = File(outputDir, "ACTUATOR_INVENTORY.json")
        val actuatorArray = JSONArray(deviceProfile.actuators.map { it.toJson() })
        actuatorInventoryFile.writeText(actuatorArray.toString(2))
        generatedFiles["ACTUATOR_INVENTORY.json"] = actuatorInventoryFile

        // 5. TEST_RESULTS.json
        val testResultsFile = File(outputDir, "TEST_RESULTS.json")
        val testResultsArray = JSONArray(testResults.map { it.toJson() })
        testResultsFile.writeText(testResultsArray.toString(2))
        generatedFiles["TEST_RESULTS.json"] = testResultsFile

        // 6. TEST_REPORT.md
        val testReportMdFile = File(outputDir, "TEST_REPORT.md")
        testReportMdFile.writeText(renderTestReportMarkdown(deviceProfile, testResults))
        generatedFiles["TEST_REPORT.md"] = testReportMdFile

        // 7. FAILURES.md
        val failuresMdFile = File(outputDir, "FAILURES.md")
        failuresMdFile.writeText(renderFailuresMarkdown(testResults))
        generatedFiles["FAILURES.md"] = failuresMdFile

        return generatedFiles
    }

    private fun renderCapabilitiesMarkdown(profile: DeviceProfile): String {
        return buildString {
            appendLine("# PHYSICAL DEVICE CAPABILITY MATRIX")
            appendLine("Device: ${profile.manufacturer} ${profile.model} (${profile.device})")
            appendLine("Android: ${profile.androidVersion} (API ${profile.apiLevel})")
            appendLine()
            appendLine("| Capability | Hardware | Permission | Operation | Verification |")
            appendLine("| --- | --- | --- | --- | --- |")
            profile.capabilityMappings.forEach { cap ->
                appendLine("| ${cap.name} | ${cap.hardwareState} | ${cap.permissionState} | ${cap.operationState} | ${cap.verificationState} |")
            }
        }
    }

    private fun renderTestReportMarkdown(
        profile: DeviceProfile,
        results: List<TestResult>
    ): String {
        val total = results.size
        val passed = results.count { it.status == PhysicalTestStatus.PASS }
        val failed = results.count { it.status == PhysicalTestStatus.FAIL }
        val blocked = results.count { it.status == PhysicalTestStatus.BLOCKED }
        val unsupported = results.count { it.status == PhysicalTestStatus.UNSUPPORTED }
        val skipped = results.count { it.status == PhysicalTestStatus.SKIPPED }
        val errors = results.count { it.status == PhysicalTestStatus.ERROR }

        val passRate = if (total > 0) (passed.toDouble() / total) * 100 else 0.0

        return buildString {
            appendLine("# PHYSICAL DEVICE VALIDATION REPORT")
            appendLine()
            appendLine("Device: ${profile.manufacturer} ${profile.model} (${profile.device})")
            appendLine("Android: ${profile.androidVersion} (API ${profile.apiLevel})")
            appendLine("RAM: ${profile.availableMemoryMb} MB free / ${profile.totalMemoryMb} MB total")
            appendLine("Storage: ${profile.availableStorageMb} MB free / ${profile.totalStorageMb} MB total")
            appendLine("Timestamp: ${System.currentTimeMillis()}")
            appendLine()
            appendLine("## SUMMARY")
            appendLine("-------")
            appendLine("Total: $total")
            appendLine("Passed: $passed")
            appendLine("Failed: $failed")
            appendLine("Blocked: $blocked")
            appendLine("Unsupported: $unsupported")
            appendLine("Skipped: $skipped")
            appendLine("Errors: $errors")
            appendLine("Pass Rate: ${String.format("%.1f", passRate)}%")
            appendLine()
            appendLine("## DETAILED TEST RESULTS")
            appendLine("----------------------")

            results.forEach { res ->
                appendLine("### ${res.testId} — ${res.testName}")
                appendLine("Category: ${res.category}")
                appendLine("Status: ${res.status}")
                appendLine("Expected: ${res.expectedOutcome}")
                appendLine("Actual: ${res.actualOutcome}")
                appendLine("Mechanism: ${res.mechanismUsed}")
                appendLine("Observation Before: ${res.observationBefore}")
                appendLine("Observation After: ${res.observationAfter}")
                appendLine("Verification: ${res.verificationResult}")
                if (res.failureReason != null) {
                    appendLine("Failure Category: ${res.failureCategory}")
                    appendLine("Failure Reason: ${res.failureReason}")
                }
                appendLine("Evidence: ${res.evidence}")
                if (res.diagnosticTrace.isNotEmpty()) {
                    appendLine("Diagnostic Trace:")
                    res.diagnosticTrace.forEach { line -> appendLine("  - $line") }
                }
                appendLine()
            }

            appendLine("## FAILURE SUMMARY")
            appendLine("===============")
            val failures = results.filter { it.status == PhysicalTestStatus.FAIL || it.status == PhysicalTestStatus.ERROR }
            if (failures.isEmpty()) {
                appendLine("No physical test failures observed.")
            } else {
                failures.forEachIndexed { idx, fail ->
                    appendLine("### Failure #${idx + 1}: ${fail.testId}")
                    appendLine("Layer: Execution / Verification")
                    appendLine("Hypothesis: Likely hardware/permission constraint or timing mismatch")
                    appendLine("Observed: ${fail.actualOutcome}")
                    appendLine("Evidence: ${fail.evidence}")
                    appendLine()
                }
            }
        }
    }

    private fun renderFailuresMarkdown(results: List<TestResult>): String {
        val failures = results.filter { it.status == PhysicalTestStatus.FAIL || it.status == PhysicalTestStatus.ERROR || it.status == PhysicalTestStatus.BLOCKED }
        return buildString {
            appendLine("# PHYSICAL VALIDATION FAILURES & BLOCKS")
            appendLine()
            if (failures.isEmpty()) {
                appendLine("Zero test failures or blocked tests recorded.")
            } else {
                failures.forEach { fail ->
                    appendLine("## ${fail.testId} - ${fail.testName}")
                    appendLine("Status: ${fail.status}")
                    appendLine("Reason: ${fail.failureReason ?: "Blocked / Unverified"}")
                    appendLine("Trace:")
                    fail.diagnosticTrace.forEach { line -> appendLine("  - $line") }
                    appendLine()
                }
            }
        }
    }
}
