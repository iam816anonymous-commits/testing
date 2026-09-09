package com.creator.automation

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class AutomationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "AutomationWorker"
    }

    override suspend fun doWork(): Result {
        val workflowId = inputData.getString("workflowId") ?: "yt_studio_read_only"
        val triggerName = inputData.getString("executionTrigger") ?: ExecutionTrigger.SCHEDULED.name
        val executionTrigger = try {
            ExecutionTrigger.valueOf(triggerName)
        } catch (e: Exception) {
            ExecutionTrigger.SCHEDULED
        }

        val taskDesc = inputData.getString("taskDescription") ?: "Check YouTube Studio Analytics"
        val url = inputData.getString("url")

        Log.i(TAG, "AutomationWorker started for workflowId: $workflowId, trigger: $executionTrigger, url: $url")

        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.observationDao()

        // 1. Process-Death Recovery Check
        val runtimeManager = AgentRuntimeManager(applicationContext)
        val recoveredSessions = runtimeManager.recoverInterruptedSessions()
        if (recoveredSessions > 0) {
            Log.i(TAG, "AutomationWorker recovered $recoveredSessions interrupted agent session(s) post-process-death.")
        }

        val service = AutomationAccessibilityService.instance
        if (service == null) {
            Log.e(TAG, "AccessibilityService is disabled. Recording BLOCKED state.")
            val observation = AutomationObservation(
                workflowId = workflowId,
                packageName = "unknown",
                result = ActionResultStatus.BLOCKED.name,
                reason = ExecutionReason.ACCESSIBILITY_DISABLED.name,
                executionTrigger = executionTrigger.name,
                visibleTextSummary = "AccessibilityService disabled",
                errorMessage = "AccessibilityService is disabled. Automation cannot proceed."
            )
            dao.insertObservation(observation)
            return Result.failure()
        }

        // 2. Delegate execution via Persistent AgentRuntimeManager
        val stepResult = runtimeManager.startOrResumeTaskSession(
            taskDescription = taskDesc,
            globalAutonomousEnabled = true,
            trigger = executionTrigger
        )

        return when (stepResult.nextState) {
            AgentState.COMPLETED -> Result.success()
            AgentState.PAUSED, AgentState.CANCELLED -> Result.failure()
            else -> Result.retry()
        }
    }
}
