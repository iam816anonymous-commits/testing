package com.creator.automation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AutomationOverlayState {
    private val _currentState = MutableStateFlow(AutomationVisualizationState())
    val currentState: StateFlow<AutomationVisualizationState> = _currentState.asStateFlow()

    fun updateState(
        actionState: VisualizationActionState,
        targetText: String? = null,
        targetViewId: String? = null,
        targetClassName: String? = null,
        targetBounds: TargetBounds? = null,
        packageName: String? = null
    ) {
        _currentState.value = AutomationVisualizationState(
            actionState = actionState,
            targetText = targetText,
            targetViewId = targetViewId,
            targetClassName = targetClassName,
            targetBounds = targetBounds,
            cursorX = targetBounds?.centerX,
            cursorY = targetBounds?.centerY,
            packageName = packageName,
            timestamp = System.currentTimeMillis()
        )
    }

    fun clearState() {
        _currentState.value = AutomationVisualizationState(
            actionState = VisualizationActionState.OBSERVING
        )
    }
}
