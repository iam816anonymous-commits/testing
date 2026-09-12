package com.creator.automation

data class ApplicationWorldState(
    val packageName: String,
    val windowTitle: String? = null,
    val visibleTexts: List<String> = emptyList(),
    val interactiveNodes: List<UiNodeInfo> = emptyList(),
    val editableNodes: List<UiNodeInfo> = emptyList(),
    val scrollableNodes: List<UiNodeInfo> = emptyList(),
    val focusedNode: UiNodeInfo? = null,
    val availableActions: List<ActionType> = emptyList(),
    val visualConfidence: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromSnapshot(snapshot: UiSnapshot): ApplicationWorldState {
            val interactive = snapshot.allNodes.filter { it.isClickable || it.isLongClickable || it.isFocusable }
            val focused = snapshot.focusedNodes.firstOrNull() ?: snapshot.allNodes.firstOrNull { it.isFocused }

            val actions = mutableListOf<ActionType>()
            actions.add(ActionType.READ_VISIBLE_UI)
            actions.add(ActionType.GO_BACK)
            actions.add(ActionType.PRESS_HOME)

            if (snapshot.editableNodes.isNotEmpty()) {
                actions.add(ActionType.TYPE_TEXT)
                actions.add(ActionType.CLEAR_TEXT)
                actions.add(ActionType.SUBMIT_INPUT)
            }
            if (snapshot.scrollableNodes.isNotEmpty()) {
                actions.add(ActionType.SCROLL_DOWN)
                actions.add(ActionType.SCROLL_UP)
            }
            if (interactive.isNotEmpty()) {
                actions.add(ActionType.CLICK_TEXT)
            }

            return ApplicationWorldState(
                packageName = snapshot.packageName,
                visibleTexts = snapshot.visibleTexts,
                interactiveNodes = interactive,
                editableNodes = snapshot.editableNodes,
                scrollableNodes = snapshot.scrollableNodes,
                focusedNode = focused,
                availableActions = actions,
                timestamp = snapshot.timestamp
            )
        }
    }
}

data class ActionGraphNode(
    val targetDescription: String,
    val actionType: ActionType,
    val targetBounds: TargetBounds? = null,
    val expectedStateTransition: String
)

data class ActionGraph(
    val packageName: String,
    val availableTransitions: List<ActionGraphNode> = emptyList()
) {
    companion object {
        fun buildFromWorldState(worldState: ApplicationWorldState): ActionGraph {
            val transitions = mutableListOf<ActionGraphNode>()

            // Clickable controls
            worldState.interactiveNodes.take(10).forEach { node ->
                val label = node.text ?: node.contentDescription ?: node.viewIdResourceName ?: node.className ?: "Control"
                transitions.add(
                    ActionGraphNode(
                        targetDescription = label,
                        actionType = ActionType.CLICK_TEXT,
                        expectedStateTransition = "UI Transition on clicking '$label'"
                    )
                )
            }

            // Editable controls
            if (worldState.editableNodes.isNotEmpty()) {
                transitions.add(
                    ActionGraphNode(
                        targetDescription = "Active Editable Input Field",
                        actionType = ActionType.TYPE_TEXT,
                        expectedStateTransition = "Text Injected into field"
                    )
                )
                transitions.add(
                    ActionGraphNode(
                        targetDescription = "Submit Current Input",
                        actionType = ActionType.SUBMIT_INPUT,
                        expectedStateTransition = "Search / Form Submission Result Screen"
                    )
                )
            }

            // Scrollable controls
            if (worldState.scrollableNodes.isNotEmpty()) {
                transitions.add(
                    ActionGraphNode(
                        targetDescription = "Scroll Container",
                        actionType = ActionType.SCROLL_DOWN,
                        expectedStateTransition = "New Content Exposed below"
                    )
                )
            }

            return ActionGraph(
                packageName = worldState.packageName,
                availableTransitions = transitions
            )
        }
    }
}
