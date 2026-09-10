package com.creator.automation

import android.content.Context
import android.util.Log

enum class TaskPriority {
    P0_CORE_USER_TASK,
    P1_IMPORTANT_USER_BRIEFING,
    P2_DAILY_RESEARCH,
    P3_AGENT_RD,
    P4_OPTIONAL_EXPLORATION
}

data class ScheduledPersonalTask(
    val id: String,
    val name: String,
    val domain: ResearchDomain,
    val priority: TaskPriority = TaskPriority.P2_DAILY_RESEARCH,
    val intervalMinutes: Long = 1440L, // 24 hours
    val isEnabled: Boolean = true,
    val lastRunTimestamp: Long = 0L,
    val lastResult: String? = null
)

class DailyTaskOrchestrator(
    private val context: Context,
    private val resourceGuard: ResourceGuard = ResourceGuard(context)
) {
    companion object {
        private const val TAG = "DailyTaskOrchestrator"
    }

    private val scheduledTasks = mutableListOf<ScheduledPersonalTask>()

    init {
        scheduledTasks.add(
            ScheduledPersonalTask(
                id = "task_youtube_daily",
                name = "Daily YouTube Channel Intelligence",
                domain = ResearchDomain.YOUTUBE_INTELLIGENCE,
                priority = TaskPriority.P2_DAILY_RESEARCH
            )
        )
        scheduledTasks.add(
            ScheduledPersonalTask(
                id = "task_news_daily",
                name = "Daily News Intelligence",
                domain = ResearchDomain.NEWS_INTELLIGENCE,
                priority = TaskPriority.P2_DAILY_RESEARCH
            )
        )
        scheduledTasks.add(
            ScheduledPersonalTask(
                id = "task_farm_daily",
                name = "Daily Farm Research",
                domain = ResearchDomain.FARM_RESEARCH,
                priority = TaskPriority.P2_DAILY_RESEARCH
            )
        )
        scheduledTasks.add(
            ScheduledPersonalTask(
                id = "task_agent_rd_daily",
                name = "Daily Agent R&D & Failure Analytics",
                domain = ResearchDomain.GENERAL_RESEARCH,
                priority = TaskPriority.P3_AGENT_RD
            )
        )
    }

    fun getTasks(): List<ScheduledPersonalTask> = scheduledTasks.toList()

    fun evaluateRunnableTasks(): List<ScheduledPersonalTask> {
        val resourceMetrics = resourceGuard.checkResourceState()

        // Filter tasks based on resource mode priority cutoff
        val maxAllowedPriority = when (resourceMetrics.mode) {
            ResourceMode.EMERGENCY_CLEANUP -> TaskPriority.P0_CORE_USER_TASK
            ResourceMode.REDUCED_RESOURCE_MODE -> TaskPriority.P1_IMPORTANT_USER_BRIEFING
            ResourceMode.NORMAL -> TaskPriority.P4_OPTIONAL_EXPLORATION
        }

        val runnable = scheduledTasks.filter { it.isEnabled && it.priority <= maxAllowedPriority }
        Log.i(TAG, "ORCHESTRATOR_EVALUATED: ${runnable.size}/${scheduledTasks.size} tasks runnable under mode ${resourceMetrics.mode}")
        return runnable
    }
}
