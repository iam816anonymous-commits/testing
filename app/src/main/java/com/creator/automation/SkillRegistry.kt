package com.creator.automation

import android.content.Context

class OpenAppSkill : Skill {
    override val name: String = "OpenApp"
    override val category: SkillCategory = SkillCategory.OPEN_APP
    override val description: String = "Launches an application by name or package"

    override fun isApplicable(worldState: WorldState, goal: String): Boolean {
        val lower = goal.lowercase()
        return lower.contains("open") || lower.contains("launch") || lower.contains("start")
    }

    override suspend fun execute(
        worldState: WorldState,
        goal: String,
        targetInput: String?,
        service: AutomationAccessibilityService,
        executor: DeviceActionExecutor
    ): SkillResult {
        val appQuery = targetInput ?: goal.replace(Regex("(?i)open|launch|start|app"), "").trim()
        val action = AutomationAction(type = ActionType.LAUNCH_APP, targetValue = appQuery)
        val res = executor.executeAndAudit("skill_open_app", action, service)

        return SkillResult(
            success = res.status == ActionResultStatus.SUCCESS,
            actionsExecutedCount = 1,
            lastActionResult = res.status,
            failureReason = res.message,
            outputSummary = "Launched application '$appQuery'"
        )
    }
}

class SearchSkill : Skill {
    override val name: String = "Search"
    override val category: SkillCategory = SkillCategory.SEARCH
    override val description: String = "Locates search input, enters query text, and submits"

    override fun isApplicable(worldState: WorldState, goal: String): Boolean {
        val lower = goal.lowercase()
        return lower.contains("search") || lower.contains("query") || lower.contains("find")
    }

    override suspend fun execute(
        worldState: WorldState,
        goal: String,
        targetInput: String?,
        service: AutomationAccessibilityService,
        executor: DeviceActionExecutor
    ): SkillResult {
        val query = targetInput ?: goal.replace(Regex("(?i)search|query|find|for"), "").trim()

        // Step 1: Type query text
        val typeAction = AutomationAction(type = ActionType.TYPE_TEXT, inputData = query)
        val typeRes = executor.executeAndAudit("skill_search_type", typeAction, service)
        if (typeRes.status != ActionResultStatus.SUCCESS) {
            return SkillResult(
                success = false,
                actionsExecutedCount = 1,
                lastActionResult = typeRes.status,
                failureReason = typeRes.message ?: "Failed to enter search query"
            )
        }

        // Step 2: Submit input
        val submitAction = AutomationAction(type = ActionType.SUBMIT_INPUT)
        val submitRes = executor.executeAndAudit("skill_search_submit", submitAction, service)

        return SkillResult(
            success = submitRes.status == ActionResultStatus.SUCCESS,
            actionsExecutedCount = 2,
            lastActionResult = submitRes.status,
            failureReason = submitRes.message,
            outputSummary = "Executed search query '$query'"
        )
    }
}

class ScrollAndFindSkill : Skill {
    override val name: String = "ScrollAndFind"
    override val category: SkillCategory = SkillCategory.SCROLL_AND_FIND
    override val description: String = "Scrolls active screen container until target text is found"

    override fun isApplicable(worldState: WorldState, goal: String): Boolean {
        return worldState.scrollableContainers.isNotEmpty()
    }

    override suspend fun execute(
        worldState: WorldState,
        goal: String,
        targetInput: String?,
        service: AutomationAccessibilityService,
        executor: DeviceActionExecutor
    ): SkillResult {
        val targetText = targetInput ?: goal
        var attempts = 0
        val maxAttempts = 5

        while (attempts < maxAttempts) {
            val waitAction = AutomationAction(type = ActionType.WAIT_FOR_TEXT, targetValue = targetText, timeoutMs = 1000L)
            val waitRes = executor.executeAndAudit("skill_scroll_find_check", waitAction, service)
            if (waitRes.status == ActionResultStatus.SUCCESS) {
                return SkillResult(
                    success = true,
                    actionsExecutedCount = attempts + 1,
                    lastActionResult = ActionResultStatus.SUCCESS,
                    outputSummary = "Found target '$targetText' after $attempts scroll attempts"
                )
            }

            val scrollAction = AutomationAction(type = ActionType.SCROLL_DOWN)
            val scrollRes = executor.executeAndAudit("skill_scroll_find_step", scrollAction, service)
            if (scrollRes.status != ActionResultStatus.SUCCESS) {
                return SkillResult(
                    success = false,
                    actionsExecutedCount = attempts + 1,
                    lastActionResult = scrollRes.status,
                    failureReason = "Scroll failed or reached end of container"
                )
            }
            attempts++
        }

        return SkillResult(
            success = false,
            actionsExecutedCount = maxAttempts,
            lastActionResult = ActionResultStatus.FAILED,
            failureReason = "Reached max scroll attempts ($maxAttempts) without finding '$targetText'"
        )
    }
}

class SkillRegistry(private val context: Context) {
    private val skills = mutableListOf<Skill>()

    init {
        skills.add(OpenAppSkill())
        skills.add(SearchSkill())
        skills.add(ScrollAndFindSkill())
    }

    fun getSkills(): List<Skill> = skills.toList()

    fun findApplicableSkill(worldState: WorldState, goal: String): Skill? {
        return skills.firstOrNull { it.isApplicable(worldState, goal) }
    }
}
