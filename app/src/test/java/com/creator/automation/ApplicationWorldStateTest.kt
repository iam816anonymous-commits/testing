package com.creator.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationWorldStateTest {

    @Test
    fun testWorldStateAndActionGraphDerivation() {
        val snapshot = UiSnapshot(
            packageName = "com.google.android.youtube",
            visibleTexts = listOf("Search YouTube", "Trending", "Subscriptions"),
            clickableNodes = listOf(
                UiNodeInfo(text = "Search YouTube", isClickable = true)
            ),
            editableNodes = listOf(
                UiNodeInfo(text = "Search field", isEditable = true)
            ),
            allNodes = listOf(
                UiNodeInfo(text = "Search YouTube", isClickable = true),
                UiNodeInfo(text = "Search field", isEditable = true)
            )
        )

        val worldState = ApplicationWorldState.fromSnapshot(snapshot)
        assertEquals("com.google.android.youtube", worldState.packageName)
        assertTrue("Editable nodes should be present", worldState.editableNodes.isNotEmpty())
        assertTrue("Available actions should contain TYPE_TEXT", worldState.availableActions.contains(ActionType.TYPE_TEXT))
        assertTrue("Available actions should contain SUBMIT_INPUT", worldState.availableActions.contains(ActionType.SUBMIT_INPUT))

        val actionGraph = ActionGraph.buildFromWorldState(worldState)
        assertEquals("com.google.android.youtube", actionGraph.packageName)
        assertTrue("Action graph should contain transitions", actionGraph.availableTransitions.isNotEmpty())
        val submitTransition = actionGraph.availableTransitions.find { it.actionType == ActionType.SUBMIT_INPUT }
        assertNotNull("Should contain submit transition", submitTransition)
    }
}
