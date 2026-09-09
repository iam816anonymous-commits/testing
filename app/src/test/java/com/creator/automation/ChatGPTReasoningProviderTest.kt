package com.creator.automation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ChatGPTReasoningProviderTest {

    @Test
    fun testRequestReasoning_ReturnsValidPlanWhenOnline() = runTest {
        val provider = ChatGPTReasoningProvider(isNetworkAvailable = true)
        val req = ReasoningRequest(
            taskDescription = "Find analytics for my latest Short",
            currentApp = "com.google.android.apps.youtube.creator",
            currentStateSignature = "sig123",
            visibleUISummary = "Dashboard; Analytics",
            availableActionTypes = ActionType.values().map { it.name }
        )

        val plan = provider.requestReasoning(req)

        assertNotNull(plan)
        assertTrue(plan?.isValid == true)
        assertEquals("Find analytics for my latest Short", plan?.taskDescription)
        assertTrue(plan?.steps?.isNotEmpty() == true)
    }

    @Test
    fun testRequestReasoning_ReturnsInvalidPlanWhenOffline() = runTest {
        val provider = ChatGPTReasoningProvider(isNetworkAvailable = false)
        val req = ReasoningRequest(
            taskDescription = "Find analytics for my latest Short",
            currentApp = "com.google.android.apps.youtube.creator",
            currentStateSignature = "sig123",
            visibleUISummary = "Dashboard; Analytics",
            availableActionTypes = ActionType.values().map { it.name }
        )

        val plan = provider.requestReasoning(req)

        assertNotNull(plan)
        assertFalse(plan?.isValid == true)
        assertEquals("Network unavailable", plan?.rejectionReason)
    }
}
