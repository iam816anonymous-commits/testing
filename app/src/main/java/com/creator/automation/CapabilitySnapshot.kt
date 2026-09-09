package com.creator.automation

data class AutomationCapabilitySnapshot(
    val packageName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val availableCapabilities: List<String> = emptyList(),
    val clickableTargets: List<String> = emptyList(),
    val editableTargets: List<String> = emptyList(),
    val scrollableContainerCount: Int = 0,
    val visibleTextSummary: List<String> = emptyList()
)

class CapabilitySnapshotProvider {

    companion object {
        fun createCapabilitySnapshot(snapshot: UiSnapshot): AutomationCapabilitySnapshot {
            val capabilities = mutableListOf<String>()

            capabilities.add("CAN_LAUNCH_APP")
            capabilities.add("CAN_OPEN_URL")
            capabilities.add("CAN_GO_BACK")
            capabilities.add("CAN_PRESS_HOME")
            capabilities.add("CAN_PRESS_RECENTS")
            capabilities.add("CAN_READ_UI")
            capabilities.add("CAN_REFRESH_OBSERVATION")

            val clickables = snapshot.clickableNodes.mapNotNull { it.text ?: it.contentDescription ?: it.viewIdResourceName }.distinct()
            if (clickables.isNotEmpty()) {
                capabilities.add("CAN_CLICK_TARGET")
            }

            val editables = snapshot.editableNodes.mapNotNull { it.text ?: it.contentDescription ?: it.viewIdResourceName ?: "Editable Field" }.distinct()
            if (editables.isNotEmpty() || snapshot.allNodes.any { it.isEditable }) {
                capabilities.add("CAN_TYPE_TEXT")
                capabilities.add("CAN_CLEAR_TEXT")
                capabilities.add("CAN_PRESS_ENTER")
            }

            if (snapshot.scrollableNodes.isNotEmpty()) {
                capabilities.add("CAN_SCROLL")
                capabilities.add("CAN_SCROLL_UP")
                capabilities.add("CAN_SCROLL_DOWN")
            }

            return AutomationCapabilitySnapshot(
                packageName = snapshot.packageName,
                timestamp = System.currentTimeMillis(),
                availableCapabilities = capabilities.distinct(),
                clickableTargets = clickables.take(15),
                editableTargets = editables.take(5),
                scrollableContainerCount = snapshot.scrollableNodes.size,
                visibleTextSummary = snapshot.visibleTexts.take(10)
            )
        }
    }
}
