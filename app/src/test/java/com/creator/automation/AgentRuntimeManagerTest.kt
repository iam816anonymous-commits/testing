package com.creator.automation

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class AgentRuntimeManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockSessionDao: AgentSessionDao
    private lateinit var mockAuditDao: ActionAuditDao
    private lateinit var mockAgentCore: AgentCore
    private lateinit var mockObservationProvider: ObservationProvider

    @Before
    fun setUp() {
        mockContext = Mockito.mock(Context::class.java)
        mockSessionDao = Mockito.mock(AgentSessionDao::class.java)
        mockAuditDao = Mockito.mock(ActionAuditDao::class.java)
        mockAgentCore = Mockito.mock(AgentCore::class.java)
        mockObservationProvider = Mockito.mock(ObservationProvider::class.java)

        Mockito.`when`(mockContext.applicationContext).thenReturn(mockContext)
    }

    @Test
    fun testStartOrResumeTaskSession_CreatesNewSession_WhenNoneExists() = runBlocking {
        val manager = AgentRuntimeManager(
            context = mockContext,
            sessionDao = mockSessionDao,
            auditDao = mockAuditDao,
            agentCore = mockAgentCore,
            observationProvider = mockObservationProvider
        )

        val mockResult = AgentStepResult(
            stateBefore = AgentState.IDLE,
            observation = null,
            decisionReason = "Task step finished",
            actionExecuted = null,
            actionResult = null,
            verificationStatus = VerificationStatus.SUCCESSFULLY_VERIFIED,
            nextState = AgentState.COMPLETED
        )

        Mockito.`when`(mockSessionDao.getActiveSessionByTaskDescription("Find analytics")).thenReturn(null)
        Mockito.`when`(mockAgentCore.executeTaskStep(
            taskDescription = "Find analytics",
            trigger = ExecutionTrigger.MANUAL,
            globalAutonomousEnabled = true
        )).thenReturn(mockResult)

        val result = manager.startOrResumeTaskSession("Find analytics")

        assertEquals(AgentState.COMPLETED, result.nextState)
        assertNotNull(AgentRuntimeManager.activeSession.value)
        assertEquals("Find analytics", AgentRuntimeManager.activeSession.value?.taskDescription)
        assertTrue(AgentRuntimeManager.activeSession.value?.isCompleted == true)
    }

    @Test
    fun testRecoverInterruptedSessions_EnforcesMaxRecoveryAttemptLimit() = runBlocking {
        val manager = AgentRuntimeManager(
            context = mockContext,
            sessionDao = mockSessionDao,
            auditDao = mockAuditDao,
            agentCore = mockAgentCore,
            observationProvider = mockObservationProvider
        )

        val maxAttemptsSession = AgentSessionRecord(
            sessionId = "sess_max",
            taskDescription = "Find analytics",
            currentState = AgentState.EXECUTING.name,
            recoveryAttemptCount = 2
        )

        Mockito.`when`(mockSessionDao.getActiveOrInterruptedSessions()).thenReturn(listOf(maxAttemptsSession))

        val recoveredCount = manager.recoverInterruptedSessions()

        assertEquals(0, recoveredCount)
        Mockito.verify(mockSessionDao).insertSession(org.mockito.kotlin.check {
            assertEquals("sess_max", it.sessionId)
            assertEquals(AgentState.FAILED.name, it.currentState)
            assertTrue(it.isCompleted)
        })
    }

    @Test
    fun testRecoverInterruptedSessions_CaseD_NonIdempotentUnverified_TransitionsToNeedsUserInput() = runBlocking {
        val manager = AgentRuntimeManager(
            context = mockContext,
            sessionDao = mockSessionDao,
            auditDao = mockAuditDao,
            agentCore = mockAgentCore,
            observationProvider = mockObservationProvider
        )

        val interruptedSession = AgentSessionRecord(
            sessionId = "sess_non_idempotent",
            taskDescription = "Submit content form",
            currentState = AgentState.EXECUTING.name,
            recoveryAttemptCount = 0
        )

        val nonIdempotentAudit = ActionAuditRecord(
            timestamp = System.currentTimeMillis(),
            workflowId = "wf_submit",
            trigger = "MANUAL",
            activePackage = "studio.youtube.com",
            beforeStateSignature = "sig_before",
            actionType = "CLICK_TEXT",
            targetIdentifier = "Publish",
            actionParametersSummary = "Type=CLICK_TEXT, Timeout=10000ms, Semantics=NON_IDEMPOTENT",
            dispatchResult = "SUCCESS",
            verificationStatus = "FAILED"
        )

        Mockito.`when`(mockSessionDao.getActiveOrInterruptedSessions()).thenReturn(listOf(interruptedSession))
        Mockito.`when`(mockAuditDao.getLatestAuditRecord()).thenReturn(nonIdempotentAudit)
        Mockito.`when`(mockObservationProvider.captureObservation()).thenReturn(
            CurrentObservation(
                stateSignature = "sig_current",
                packageName = "studio.youtube.com",
                summary = "Studio Dashboard"
            )
        )

        val recoveredCount = manager.recoverInterruptedSessions()

        assertEquals(1, recoveredCount)
        // Verify that agentCore.executeTaskStep was NOT called because of Case D safety halt!
        Mockito.verifyNoInteractions(mockAgentCore)
        // Verify session was transitioned to NEEDS_USER_INPUT
        Mockito.verify(mockSessionDao).insertSession(org.mockito.kotlin.check {
            assertEquals("sess_non_idempotent", it.sessionId)
            assertEquals(AgentState.NEEDS_USER_INPUT.name, it.currentState)
            assertTrue(it.failureReason?.contains("NON_IDEMPOTENT") == true)
        })
    }

    @Test
    fun testCancelActiveSession_MarksSessionCancelled() = runBlocking {
        val manager = AgentRuntimeManager(
            context = mockContext,
            sessionDao = mockSessionDao,
            auditDao = mockAuditDao,
            agentCore = mockAgentCore,
            observationProvider = mockObservationProvider
        )

        Mockito.`when`(mockSessionDao.getActiveSessionByTaskDescription("Find analytics")).thenReturn(null)
        Mockito.`when`(mockAgentCore.executeTaskStep(
            taskDescription = "Find analytics",
            trigger = ExecutionTrigger.MANUAL,
            globalAutonomousEnabled = true
        )).thenReturn(
            AgentStepResult(
                stateBefore = AgentState.IDLE,
                observation = null,
                decisionReason = "Running",
                actionExecuted = null,
                actionResult = null,
                verificationStatus = VerificationStatus.FAILED,
                nextState = AgentState.EXECUTING
            )
        )

        manager.startOrResumeTaskSession("Find analytics")
        val cancelSuccess = manager.cancelActiveSession("User explicitly cancelled")

        assertTrue(cancelSuccess)
        Mockito.verify(mockAgentCore).cancelAgent()
        assertEquals(AgentState.CANCELLED.name, AgentRuntimeManager.activeSession.value?.currentState)
        assertTrue(AgentRuntimeManager.activeSession.value?.isCancelled == true)
    }
}
