package com.creator.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneralityEngineTest {

    @Test
    fun testGenerality_FictionalAppTask_NoAppSpecificRulesRequired() {
        val resolver = ActionResolver()

        // Fictional application UI snapshot (e.g. com.fictional.app)
        val fictionalAppSnapshot = UiSnapshot(
            packageName = "com.fictional.app",
            allNodes = listOf(
                UiNodeInfo(text = "Fictional Search Bar", isEditable = true, isEnabled = true),
                UiNodeInfo(text = "Execute Search", isClickable = true, isEnabled = true),
                UiNodeInfo(text = "Fictional Item 1", isClickable = true, isEnabled = true)
            ),
            visibleTexts = listOf("Fictional Search Bar", "Execute Search", "Fictional Item 1")
        )

        // 1. Resolve editable target on fictional UI
        val editableRes = resolver.resolveEditableTarget(fictionalAppSnapshot, "Fictional Search Bar")
        assertNotNull(editableRes.match)
        assertEquals(TargetResolutionStatus.FOUND_UNIQUE, editableRes.status)

        // 2. Resolve button target on fictional UI
        val buttonRes = resolver.resolveTargetWithAmbiguity(fictionalAppSnapshot, "Execute Search")
        assertNotNull(buttonRes.match)
        assertEquals(TargetResolutionStatus.FOUND_UNIQUE, buttonRes.status)
        assertFalse(buttonRes.isAmbiguous)
    }

    @Test
    fun testGenerality_TargetAmbiguity_StrictlyBlocked() {
        val resolver = ActionResolver()

        val ambiguousSnapshot = UiSnapshot(
            packageName = "com.fictional.app",
            allNodes = listOf(
                UiNodeInfo(text = "Confirm Action", isClickable = true),
                UiNodeInfo(text = "Confirm Action", isClickable = true)
            )
        )

        val result = resolver.resolveTargetWithAmbiguity(ambiguousSnapshot, "Confirm Action")

        assertTrue(result.isAmbiguous)
        assertEquals(2, result.candidateCount)
        assertEquals(TargetResolutionStatus.AMBIGUOUS, result.status)
    }

    @Test
    fun testGenerality_DisabledNode_ClassifiedNotActionable() {
        val resolver = ActionResolver()

        val disabledNodeSnapshot = UiSnapshot(
            packageName = "com.fictional.app",
            allNodes = listOf(
                UiNodeInfo(text = "Disabled Button", isClickable = true, isEnabled = false)
            )
        )

        val result = resolver.resolveTargetWithAmbiguity(disabledNodeSnapshot, "Disabled Button")

        assertNotNull(result.match)
        assertEquals(TargetResolutionStatus.NOT_ACTIONABLE, result.status)
        assertFalse(result.match!!.node.isEnabled)
    }

    @Test
    fun testGenerality_MissingTarget_ClassifiedNotFound() {
        val resolver = ActionResolver()

        val emptySnapshot = UiSnapshot(
            packageName = "com.fictional.app",
            visibleTexts = listOf("Header", "Footer")
        )

        val result = resolver.resolveTargetWithAmbiguity(emptySnapshot, "MissingButton")

        assertEquals(TargetResolutionStatus.NOT_FOUND, result.status)
        assertEquals(0, result.candidateCount)
    }
}
