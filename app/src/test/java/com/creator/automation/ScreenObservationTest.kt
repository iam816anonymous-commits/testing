package com.creator.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class ScreenObservationTest {

    @Test
    fun testNullRootHandling() {
        val snapshot = ActionResolver.captureSnapshot(null, "com.example.app")
        assertFalse("isRootAvailable should be false for null root", snapshot.isRootAvailable)
        assertEquals("com.example.app", snapshot.packageName)
        assertEquals(0, snapshot.totalNodeCount)
        assertEquals(0, snapshot.textNodeCount)
        assertEquals(0, snapshot.clickableNodeCount)
    }

    @Test
    fun testEmptyTreeSnapshot() {
        val snapshot = UiSnapshot(
            packageName = "com.test.empty",
            isRootAvailable = true,
            allNodes = emptyList()
        )
        assertTrue(snapshot.isRootAvailable)
        assertEquals(0, snapshot.totalNodeCount)
        assertEquals(0, snapshot.clickableNodeCount)
        assertEquals(0, snapshot.editableNodeCount)
        assertEquals(0, snapshot.scrollableNodeCount)
        assertEquals(0, snapshot.focusedNodeCount)
    }

    @Test
    fun testNodePropertyCounts() {
        val node1 = UiNodeInfo(text = "Hello", isClickable = true, isVisibleToUser = true)
        val node2 = UiNodeInfo(text = "World", isEditable = true, isFocused = true)
        val node3 = UiNodeInfo(contentDescription = "Scroll List", isScrollable = true)

        val snapshot = UiSnapshot(
            packageName = "com.test.app",
            isRootAvailable = true,
            visibleTexts = listOf("Hello", "World"),
            clickableNodes = listOf(node1),
            editableNodes = listOf(node2),
            focusedNodes = listOf(node2),
            scrollableNodes = listOf(node3),
            allNodes = listOf(node1, node2, node3)
        )

        assertEquals(3, snapshot.totalNodeCount)
        assertEquals(2, snapshot.textNodeCount)
        assertEquals(1, snapshot.clickableNodeCount)
        assertEquals(1, snapshot.editableNodeCount)
        assertEquals(1, snapshot.focusedNodeCount)
        assertEquals(1, snapshot.scrollableNodeCount)
    }

    @Test
    fun testSnapshotTimestampAndDuration() {
        val snapshot = ActionResolver.captureSnapshot(null, "com.test.app")
        assertTrue("Timestamp should be greater than 0", snapshot.timestamp > 0)
        assertTrue("Traversal duration should be >= 0", snapshot.traversalDurationMs >= 0)
    }

    @Test
    fun testScreenSignatureGeneration() {
        val snapshotA = UiSnapshot(
            packageName = "com.test.app",
            visibleTexts = listOf("Home", "Search")
        )
        val snapshotB = UiSnapshot(
            packageName = "com.test.app",
            visibleTexts = listOf("Settings", "Account")
        )

        val sigA = StateSignatureGenerator.generateSignature(snapshotA)
        val sigB = StateSignatureGenerator.generateSignature(snapshotB)

        assertNotNull(sigA)
        assertNotNull(sigB)
        assertTrue("Different visible texts should generate different state signatures", sigA != sigB)
    }
}
