package com.creator.automation

enum class SkillCategory {
    SEARCH,
    NAVIGATE,
    FILL_FORM,
    TOGGLE_SETTING,
    FIND_ITEM,
    SCROLL_AND_FIND,
    OPEN_APP,
    SUBMIT_FORM
}

data class SkillResult(
    val success: Boolean,
    val actionsExecutedCount: Int,
    val lastActionResult: ActionResultStatus,
    val failureReason: String? = null,
    val outputSummary: String? = null
)

interface Skill {
    val name: String
    val category: SkillCategory
    val description: String

    fun isApplicable(worldState: WorldState, goal: String): Boolean

    suspend fun execute(
        worldState: WorldState,
        goal: String,
        targetInput: String?,
        service: AutomationAccessibilityService,
        executor: DeviceActionExecutor
    ): SkillResult
}
