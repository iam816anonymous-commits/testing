package com.creator.automation

import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class HumanInteractionValidationTest {

    @Test
    fun testPhysicalTestCaseAndResultToJson() {
        val result = TestResult(
            testId = "TEST-HUM-001",
            testName = "Human Touch Validation",
            category = PhysicalTestCategory.INPUT_INTERACTION,
            startTime = 1000L,
            endTime = 1200L,
            status = PhysicalTestStatus.PASS,
            preconditions = "Accessibility service active",
            deviceStateSummary = "Active window ready",
            actionAttempted = "Click target button",
            targetResolution = "ActionResolver exact text match",
            candidateCount = 1,
            selectedTarget = "Search Button",
            mechanismUsed = "ACTION_CLICK",
            observationBefore = "Button present",
            observationAfter = "UI state updated",
            expectedOutcome = "Button clicked and UI transitioned",
            actualOutcome = "UI state transition verified",
            dispatchResult = "SUCCESS",
            verificationResult = "VERIFIED_SUCCESS",
            evidence = "State signature shifted from Sig1 to Sig2",
            diagnosticTrace = listOf("Resolved target", "Dispatched click", "State verified")
        )

        assertEquals("TEST-HUM-001", result.testId)
        assertEquals(PhysicalTestStatus.PASS, result.status)
        assertEquals(PhysicalTestCategory.INPUT_INTERACTION, result.category)
        assertEquals(1, result.candidateCount)
        assertEquals("ACTION_CLICK", result.mechanismUsed)
    }

    @Test
    fun testPhysicalTestRunnerBlockedExecution() {
        val context = mock(android.content.Context::class.java)
        val runner = PhysicalTestRunner(context)

        val caseApproved = PhysicalTestCase(
            testId = "TEST-APP-001",
            testName = "Approved Local Check",
            category = PhysicalTestCategory.OBSERVATION,
            requiresUserApproval = false,
            executeBlock = { _ ->
                TestResult(
                    testId = "TEST-APP-001",
                    testName = "Approved Local Check",
                    category = PhysicalTestCategory.OBSERVATION,
                    startTime = 0L,
                    endTime = 100L,
                    status = PhysicalTestStatus.PASS,
                    preconditions = "",
                    deviceStateSummary = "",
                    actionAttempted = "",
                    targetResolution = "",
                    candidateCount = 0,
                    selectedTarget = null,
                    mechanismUsed = "",
                    observationBefore = "",
                    observationAfter = "",
                    expectedOutcome = "",
                    actualOutcome = "",
                    dispatchResult = "",
                    verificationResult = ""
                )
            }
        )

        val caseBlocked = PhysicalTestCase(
            testId = "TEST-BLK-001",
            testName = "Side Effect Interlock",
            category = PhysicalTestCategory.INPUT_INTERACTION,
            requiresUserApproval = true,
            executeBlock = { throw IllegalStateException("Should not be invoked") }
        )

        val results = runner.executeSuite(listOf(caseApproved, caseBlocked), allowUserApprovalTests = false)
        assertEquals(2, results.size)
        assertEquals(PhysicalTestStatus.PASS, results[0].status)
        assertEquals(PhysicalTestStatus.BLOCKED, results[1].status)
    }

    @Test
    fun testPhysicalTestRegistrySuiteStructure() {
        val suite = PhysicalTestRegistry.buildSafeValidationSuite()
        assertFalse(suite.isEmpty())

        assertTrue(suite.any { it.testId == "TEST-OBS-001" })
        assertTrue(suite.any { it.testId == "TEST-RES-001" })
        assertTrue(suite.any { it.testId == "TEST-INP-001" })
        assertTrue(suite.any { it.testId == "TEST-VER-001" })
        assertTrue(suite.any { it.testId == "TEST-REC-001" })
        assertTrue(suite.any { it.testId == "TEST-HW-001" })
        assertTrue(suite.any { it.testId == "TEST-SAF-001" })
    }
}
