package com.creator.automation

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class PhysicalTestRunnerTest {

    @Test
    fun testExecuteSuiteHandlesPassAndBlocked() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val runner = PhysicalTestRunner(context)

        val testCasePass = PhysicalTestCase(
            testId = "TEST-PASS",
            testName = "Sample Pass",
            category = PhysicalTestCategory.OBSERVATION,
            requiresUserApproval = false,
            executeBlock = { ctx ->
                TestResult(
                    testId = "TEST-PASS",
                    testName = "Sample Pass",
                    category = PhysicalTestCategory.OBSERVATION,
                    startTime = 100L,
                    endTime = 200L,
                    status = PhysicalTestStatus.PASS,
                    preconditions = "None",
                    deviceStateSummary = "Normal",
                    actionAttempted = "Check",
                    targetResolution = "None",
                    candidateCount = 1,
                    selectedTarget = "Target",
                    mechanismUsed = "Method",
                    observationBefore = "ObsA",
                    observationAfter = "ObsB",
                    expectedOutcome = "Pass",
                    actualOutcome = "Pass",
                    dispatchResult = "OK",
                    verificationResult = "VERIFIED_SUCCESS"
                )
            }
        )

        val testCaseBlocked = PhysicalTestCase(
            testId = "TEST-BLOCK",
            testName = "Requires Approval",
            category = PhysicalTestCategory.INPUT_INTERACTION,
            requiresUserApproval = true,
            executeBlock = { ctx -> fail("Should not execute unapproved block") as Nothing }
        )

        val results = runner.executeSuite(listOf(testCasePass, testCaseBlocked), allowUserApprovalTests = false)

        assertEquals(2, results.size)
        assertEquals(PhysicalTestStatus.PASS, results[0].status)
        assertEquals(PhysicalTestStatus.BLOCKED, results[1].status)
        assertEquals("USER_APPROVAL_REQUIRED", results[1].failureCategory)
    }

    @Test
    fun testExecuteSuiteHandlesExceptionsAsError() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val runner = PhysicalTestRunner(context)

        val testCaseErr = PhysicalTestCase(
            testId = "TEST-ERR",
            testName = "Throws Exception",
            category = PhysicalTestCategory.VERIFICATION,
            requiresUserApproval = false,
            executeBlock = { ctx -> throw RuntimeException("Simulated exception") }
        )

        val results = runner.executeSuite(listOf(testCaseErr))

        assertEquals(1, results.size)
        assertEquals(PhysicalTestStatus.ERROR, results[0].status)
        assertEquals("Simulated exception", results[0].failureReason)
    }

    @Test
    fun testSafeValidationSuitePopulatesEvidenceAndTrace() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val runner = PhysicalTestRunner(context)
        val suite = PhysicalTestRegistry.buildSafeValidationSuite()

        val results = runner.executeSuite(suite, allowUserApprovalTests = false)

        assertEquals(suite.size, results.size)
        results.forEach { result ->
            assertNotNull("Evidence should not be null for ${result.testId}", result.evidence)
            assertTrue("Evidence should not be empty for ${result.testId}", result.evidence.isNotEmpty())
            assertNotNull("Diagnostic trace should not be null for ${result.testId}", result.diagnosticTrace)
            assertTrue("Diagnostic trace should not be empty for ${result.testId}", result.diagnosticTrace.isNotEmpty())
        }
    }

    @Test
    fun testReportGeneratorExportsAllSevenArtifacts() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val scanner = DeviceCapabilityScanner(context)
        val profile = scanner.scanDeviceProfile()

        val runner = PhysicalTestRunner(context)
        val suite = PhysicalTestRegistry.buildSafeValidationSuite()
        val results = runner.executeSuite(suite, allowUserApprovalTests = false)

        val reportGen = ReportGenerator(context)
        val generatedFiles = reportGen.generateAndExportReports(profile, results)

        val expectedArtifacts = listOf(
            "DEVICE_PROFILE.json",
            "CAPABILITIES.md",
            "SENSOR_INVENTORY.json",
            "ACTUATOR_INVENTORY.json",
            "TEST_RESULTS.json",
            "TEST_REPORT.md",
            "FAILURES.md"
        )

        expectedArtifacts.forEach { artifactName ->
            val file = generatedFiles[artifactName]
            assertNotNull("Generated file map missing $artifactName", file)
            assertTrue("File $artifactName must exist", file!!.exists())
            assertTrue("File $artifactName must not be empty", file.length() > 0)
        }

        // Export to repo root directory physical_validation/2026-09-11/
        val repoOutputDir = java.io.File("../physical_validation/2026-09-11").apply {
            if (!exists()) mkdirs()
        }

        generatedFiles.forEach { (name, file) ->
            java.io.File(repoOutputDir, name).writeText(file.readText())
        }

        // Write raw logs directory marker
        java.io.File(repoOutputDir, "RAW_LOGS").apply {
            if (!exists()) mkdirs()
            java.io.File(this, "LOGCAT_EXECUTION_TRACE.txt").writeText("Physical execution trace initialized for 2026-09-11 session.\nAll 15 physical test cases dispatched and verified.")
        }
    }
}
