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
    private lateinit var mockWorkflowEngine: WorkflowEngine
    private lateinit var mockRecoveryManager: RecoveryManager

    @Before
    fun setUp() {
        mockContext = Mockito.mock(Context::class.java)
        mockObservationProvider = Mockito.mock(ObservationProvider::class.java)
        mockWorkflowEngine = Mockito.mock(WorkflowEngine::class.java)
        mockRecoveryManager = Mockito.mock(RecoveryManager::class.java)

        Mockito.`when`(mockContext.applicationContext).thenReturn(mockContext)
    }

    @Test
    fun testInitialAgentState_IsIdle() {
        val agentCore = AgentCore(mockContext, mockObservationProvider, mockWorkflowEngine, mockRecoveryManager)
        agentCore.resumeAgent()
        assertEquals(AgentState.IDLE, AgentCore.agentState.value)
    }

    @Test
    fun testPauseResumeCancel_TransitionsStateCorrectly() {
        val agentCore = AgentCore(mockContext, mockObservationProvider, mockWorkflowEngine, mockRecoveryManager)

        agentCore.pauseAgent()
        assertEquals(AgentState.PAUSED, AgentCore.agentState.value)

        agentCore.resumeAgent()
        assertEquals(AgentState.IDLE, AgentCore.agentState.value)

        agentCore.cancelAgent()
        assertEquals(AgentState.CANCELLED, AgentCore.agentState.value)
    }

    @Test
    fun testExecuteTaskStep_WhenPaused_ReturnsPausedResult() = runBlocking {
        val agentCore = AgentCore(mockContext, mockObservationProvider, mockWorkflowEngine, mockRecoveryManager)
        agentCore.pauseAgent()

        val stepResult = agentCore.executeTaskStep("Find analytics", globalAutonomousEnabled = true)

        assertEquals(AgentState.PAUSED, stepResult.nextState)
        assertEquals("Agent loop is currently PAUSED", stepResult.decisionReason)
    }

    @Test
    fun testExecuteTaskStep_WhenCancelled_ReturnsCancelledResult() = runBlocking {
        val agentCore = AgentCore(mockContext, mockObservationProvider, mockWorkflowEngine, mockRecoveryManager)
        agentCore.cancelAgent()

        val stepResult = agentCore.executeTaskStep("Find analytics", globalAutonomousEnabled = true)

        assertEquals(AgentState.CANCELLED, stepResult.nextState)
        assertEquals("Agent task cancelled", stepResult.decisionReason)
    }
}
