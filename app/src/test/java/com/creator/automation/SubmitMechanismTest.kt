package com.creator.automation

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SubmitMechanismTest {

    private lateinit var actionResolver: ActionResolver

    @Before
    fun setUp() {
        actionResolver = ActionResolver()
    }

    @Test
    fun testFocusedEditableWithSearchButton() {
        val snapshot = UiSnapshot(
            packageName = "com.example.search",
            allNodes = listOf(
                UiNodeInfo(
                    text = "query",
                    isEditable = true,
                    isFocused = true,
                    isEnabled = true,
                    isVisibleToUser = true
                ),
                UiNodeInfo(
                    text = "Search",
                    isClickable = true,
                    isEnabled = true,
                    isVisibleToUser = true
                )
            )
        )

        val focusedEditable = actionResolver.resolveEditableTarget(snapshot)
        assertNotNull(focusedEditable.match)
        assertEquals("FOCUSED_EDITABLE", focusedEditable.match?.matchMethod)

        val submitTarget = actionResolver.resolveTargetWithAmbiguity(snapshot, "Search")
        assertNotNull(submitTarget.match)
        assertFalse(submitTarget.isAmbiguous)
        assertEquals(TargetResolutionStatus.FOUND_UNIQUE, submitTarget.status)
        assertEquals("EXACT_TEXT", submitTarget.match?.matchMethod)
    }

    @Test
    fun testFocusedEditableWithGoButton() {
        val snapshot = UiSnapshot(
            packageName = "com.example.go",
            allNodes = listOf(
                UiNodeInfo(
                    text = "url",
                    isEditable = true,
                    isFocused = true,
                    isEnabled = true,
                    isVisibleToUser = true
                ),
                UiNodeInfo(
                    text = "Go",
                    isClickable = true,
                    isEnabled = true,
                    isVisibleToUser = true
                )
            )
        )

        val submitTarget = actionResolver.resolveTargetWithAmbiguity(snapshot, "Go")
        assertNotNull(submitTarget.match)
        assertFalse(submitTarget.isAmbiguous)
        assertEquals("EXACT_TEXT", submitTarget.match?.matchMethod)
    }

    @Test
    fun testFocusedEditableWithDoneButton() {
        val snapshot = UiSnapshot(
            packageName = "com.example.done",
            allNodes = listOf(
                UiNodeInfo(
                    text = "input",
                    isEditable = true,
                    isFocused = true,
                    isEnabled = true
                ),
                UiNodeInfo(
                    text = "Done",
                    isClickable = true,
                    isEnabled = true
                )
            )
        )

        val submitTarget = actionResolver.resolveTargetWithAmbiguity(snapshot, "Done")
        assertNotNull(submitTarget.match)
        assertEquals("EXACT_TEXT", submitTarget.match?.matchMethod)
    }

    @Test
    fun testFocusedEditableWithSubmitButton() {
        val snapshot = UiSnapshot(
            packageName = "com.example.submit",
            allNodes = listOf(
                UiNodeInfo(
                    text = "Submit",
                    isClickable = true,
                    isEnabled = true
                )
            )
        )

        val submitTarget = actionResolver.resolveTargetWithAmbiguity(snapshot, "Submit")
        assertNotNull(submitTarget.match)
        assertEquals(TargetResolutionStatus.FOUND_UNIQUE, submitTarget.status)
    }

    @Test
    fun testMultiplePossibleSubmitControlsTriggersAmbiguity() {
        val snapshot = UiSnapshot(
            packageName = "com.example.ambiguous",
            allNodes = listOf(
                UiNodeInfo(
                    text = "Search",
                    isClickable = true,
                    isEnabled = true,
                    boundsInScreen = "[10,10][100,50]"
                ),
                UiNodeInfo(
                    text = "Search",
                    isClickable = true,
                    isEnabled = true,
                    boundsInScreen = "[10,60][100,100]"
                )
            )
        )

        val submitTarget = actionResolver.resolveTargetWithAmbiguity(snapshot, "Search")
        assertTrue(submitTarget.isAmbiguous)
        assertEquals(2, submitTarget.candidateCount)
        assertEquals(TargetResolutionStatus.AMBIGUOUS, submitTarget.status)
    }

    @Test
    fun testNoSubmitMechanismAvailable() {
        val snapshot = UiSnapshot(
            packageName = "com.example.nosubmit",
            allNodes = listOf(
                UiNodeInfo(
                    text = "Random Content",
                    isClickable = false
                )
            )
        )

        val submitTarget = actionResolver.resolveTargetWithAmbiguity(snapshot, "Search")
        assertNull(submitTarget.match)
        assertEquals(TargetResolutionStatus.NOT_FOUND, submitTarget.status)
    }

    @Test
    fun testDisabledSubmitControlIsMarkedNotActionable() {
        val snapshot = UiSnapshot(
            packageName = "com.example.disabled",
            allNodes = listOf(
                UiNodeInfo(
                    text = "Submit",
                    isClickable = true,
                    isEnabled = false,
                    isVisibleToUser = true
                )
            )
        )

        val submitTarget = actionResolver.resolveTargetWithAmbiguity(snapshot, "Submit")
        assertNotNull(submitTarget.match)
        assertEquals(TargetResolutionStatus.NOT_ACTIONABLE, submitTarget.status)
    }

    @Test
    fun testInvisibleSubmitControlIsMarkedNotActionable() {
        val snapshot = UiSnapshot(
            packageName = "com.example.invisible",
            allNodes = listOf(
                UiNodeInfo(
                    text = "Submit",
                    isClickable = true,
                    isEnabled = true,
                    isVisibleToUser = false
                )
            )
        )

        val submitTarget = actionResolver.resolveTargetWithAmbiguity(snapshot, "Submit")
        assertNotNull(submitTarget.match)
        assertEquals(TargetResolutionStatus.NOT_ACTIONABLE, submitTarget.status)
    }

    @Test
    fun testStaleTargetEvaluation() {
        val snapshot = UiSnapshot(
            packageName = "com.example.stale",
            allNodes = emptyList()
        )

        val submitTarget = actionResolver.resolveTargetWithAmbiguity(snapshot, "Search")
        assertNull(submitTarget.match)
        assertEquals(TargetResolutionStatus.NOT_FOUND, submitTarget.status)
    }

    @Test
    fun testAmbiguousSubmitCandidatesExposed() {
        val snapshot = UiSnapshot(
            packageName = "com.example.candidates",
            allNodes = listOf(
                UiNodeInfo(viewIdResourceName = "com.example:id/search_button_1", isClickable = true),
                UiNodeInfo(viewIdResourceName = "com.example:id/search_button_2", isClickable = true)
            )
        )

        val res = actionResolver.resolveTargetWithAmbiguity(snapshot, "search_button")
        assertTrue(res.isAmbiguous)
        assertEquals(2, res.candidateCount)
    }

    @Test
    fun testExactViewIdSearchMatch() {
        val snapshot = UiSnapshot(
            packageName = "com.example.viewid",
            allNodes = listOf(
                UiNodeInfo(viewIdResourceName = "com.example:id/btn_search", isClickable = true, isEnabled = true)
            )
        )

        val res = actionResolver.resolveTargetWithAmbiguity(snapshot, "btn_search")
        assertNotNull(res.match)
        assertEquals("VIEW_ID", res.match?.matchMethod)
        assertFalse(res.isAmbiguous)
    }
}
