package com.creator.automation

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class ReportGeneratorTest {

    @Test
    fun testGenerateAndExportReportsCreatesExpectedFiles() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val scanner = DeviceCapabilityScanner(context)
        val profile = scanner.scanDeviceProfile()

        val sampleResults = listOf(
            TestResult(
                testId = "TEST-001",
                testName = "Sample Test",
                category = PhysicalTestCategory.OBSERVATION,
                startTime = 1000L,
                endTime = 1050L,
                status = PhysicalTestStatus.PASS,
                preconditions = "None",
                deviceStateSummary = "OK",
                actionAttempted = "None",
                targetResolution = "None",
                candidateCount = 1,
                selectedTarget = "Node",
                mechanismUsed = "Accessibility",
                observationBefore = "Obs1",
                observationAfter = "Obs2",
                expectedOutcome = "Success",
                actualOutcome = "Success",
                dispatchResult = "OK",
                verificationResult = "VERIFIED_SUCCESS"
            )
        )

        val generator = ReportGenerator(context)
        val exportedFiles = generator.generateAndExportReports(profile, sampleResults)

        assertNotNull(exportedFiles)
        assertTrue(exportedFiles.containsKey("DEVICE_PROFILE.json"))
        assertTrue(exportedFiles.containsKey("CAPABILITIES.md"))
        assertTrue(exportedFiles.containsKey("SENSOR_INVENTORY.json"))
        assertTrue(exportedFiles.containsKey("ACTUATOR_INVENTORY.json"))
        assertTrue(exportedFiles.containsKey("TEST_RESULTS.json"))
        assertTrue(exportedFiles.containsKey("TEST_REPORT.md"))
        assertTrue(exportedFiles.containsKey("FAILURES.md"))

        val reportMd = exportedFiles["TEST_REPORT.md"]!!
        assertTrue(reportMd.exists())
        val text = reportMd.readText()
        assertTrue(text.contains("PHYSICAL DEVICE VALIDATION REPORT"))
        assertTrue(text.contains("TEST-001"))
    }
}
