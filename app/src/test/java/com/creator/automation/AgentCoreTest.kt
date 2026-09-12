package com.creator.automation

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class AgentCoreTest {

    private lateinit var mockContext: Context
    private lateinit var mockObservationProvider: ObservationProvider
    private lateinit var mockScreenObservationProvider: ObservationProvider
    private lateinit var mockCameraObservationProvider: ObservationProvider
    private lateinit var mockWorkflowEngine: WorkflowEngine
    private lateinit var mockRecoveryManager: RecoveryManager

    @Before
    fun setUp() {
        mockContext = Mockito.mock(Context::class.java)
        mockObservationProvider = Mockito.mock(ObservationProvider::class.java)
        mockScreenObservationProvider = Mockito.mock(ObservationProvider::class.java)
        mockCameraObservationProvider = Mockito.mock(ObservationProvider::class.java)
        mockWorkflowEngine = Mockito.mock(WorkflowEngine::class.java)
        mockRecoveryManager = Mockito.mock(RecoveryManager::class.java)

        Mockito.`when`(mockContext.applicationContext).thenReturn(mockContext)

        val dummyObs = CurrentObservation(
            source = ObservationSource.ACCESSIBILITY,
            packageName = "com.test",
            stateSignature = "test_sig",
            summary = "test summary",
            confidence = 1.0,
            snapshot = UiSnapshot(packageName = "com.test")
        )
        runBlocking {
            Mockito.doReturn(dummyObs).`when`(mockObservationProvider).captureObservation()
            Mockito.doReturn(dummyObs).`when`(mockScreenObservationProvider).captureObservation()
            Mockito.doReturn(dummyObs).`when`(mockCameraObservationProvider).captureObservation()

            val dummyResult = ActionResult(status = ActionResultStatus.SUCCESS, message = "Success")
            Mockito.doReturn(dummyResult).`when`(mockWorkflowEngine).resolveAndExecuteTask(
                taskDescription = Mockito.anyString() ?: "",
                service = Mockito.any(),
                trigger = Mockito.any() ?: ExecutionTrigger.MANUAL,
                globalAutonomousEnabled = Mockito.anyBoolean()
            )
        }
    }

    @Test
    fun testInitialAgentState_IsIdle() {
        val agentCore = AgentCore(
            context = mockContext,
            observationProvider = mockObservationProvider,
            screenObservationProvider = mockScreenObservationProvider,
            cameraObservationProvider = mockCameraObservationProvider,
            workflowEngine = mockWorkflowEngine,
            recoveryManager = mockRecoveryManager
        )
        agentCore.resumeAgent()
        assertEquals(AgentState.IDLE, AgentCore.agentState.value)
    }

    @Test
    fun testPauseResumeCancel_TransitionsStateCorrectly() {
        val agentCore = AgentCore(
            context = mockContext,
            observationProvider = mockObservationProvider,
            screenObservationProvider = mockScreenObservationProvider,
            cameraObservationProvider = mockCameraObservationProvider,
            workflowEngine = mockWorkflowEngine,
            recoveryManager = mockRecoveryManager
        )

        agentCore.pauseAgent()
        assertEquals(AgentState.PAUSED, AgentCore.agentState.value)

        agentCore.resumeAgent()
        assertEquals(AgentState.IDLE, AgentCore.agentState.value)

        agentCore.cancelAgent()
        assertEquals(AgentState.CANCELLED, AgentCore.agentState.value)
    }

    @Test
    fun testExecuteTaskStep_WhenPaused_AutoResumesAndExecutesTask() = runBlocking {
        val agentCore = AgentCore(
            context = mockContext,
            observationProvider = mockObservationProvider,
            screenObservationProvider = mockScreenObservationProvider,
            cameraObservationProvider = mockCameraObservationProvider,
            workflowEngine = mockWorkflowEngine,
            recoveryManager = mockRecoveryManager
        )
        agentCore.pauseAgent()
        assertEquals(AgentState.PAUSED, AgentCore.agentState.value)

        val stepResult = agentCore.executeTaskStep("Find analytics", globalAutonomousEnabled = true)

        // Verifies that submitting a task when paused resets state and attempts execution rather than blocking indefinitely
        assertEquals(AgentState.COMPLETED, stepResult.nextState)
    }

    @Test
    fun testExecuteTaskStep_WhenCancelled_AutoResumesAndExecutesTask() = runBlocking {
        val agentCore = AgentCore(
            context = mockContext,
            observationProvider = mockObservationProvider,
            screenObservationProvider = mockScreenObservationProvider,
            cameraObservationProvider = mockCameraObservationProvider,
            workflowEngine = mockWorkflowEngine,
            recoveryManager = mockRecoveryManager
        )
        agentCore.cancelAgent()
        assertEquals(AgentState.CANCELLED, AgentCore.agentState.value)

        val stepResult = agentCore.executeTaskStep("Find analytics", globalAutonomousEnabled = true)

        // Verifies that submitting a task when cancelled resets state and attempts execution
        assertEquals(AgentState.COMPLETED, stepResult.nextState)
    }
}
