package com.creator.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitySnapshotTest {

    @Test
    fun testCreateCapabilitySnapshot_GeneratesActiveCapabilities() {
        val snapshot = UiSnapshot(
            packageName = "com.android.chrome",
            clickableNodes = listOf(
                UiNodeInfo(text = "Search or type web address", isClickable = true)
            ),
            editableNodes = listOf(
                UiNodeInfo(text = "Search or type web address", isEditable = true)
            ),
            scrollableNodes = listOf(
                UiNodeInfo(isScrollable = true)
            ),
            visibleTexts = listOf("Search or type web address", "New tab")
        )

        val capSnapshot = CapabilitySnapshotProvider.createCapabilitySnapshot(snapshot)

        assertEquals("com.android.chrome", capSnapshot.packageName)
        assertTrue(capSnapshot.availableCapabilities.contains("CAN_LAUNCH_APP"))
        assertTrue(capSnapshot.availableCapabilities.contains("CAN_CLICK_TARGET"))
        assertTrue(capSnapshot.availableCapabilities.contains("CAN_TYPE_TEXT"))
        assertTrue(capSnapshot.availableCapabilities.contains("CAN_SCROLL"))
        assertEquals(1, capSnapshot.scrollableContainerCount)
        assertTrue(capSnapshot.clickableTargets.contains("Search or type web address"))
    }
}
