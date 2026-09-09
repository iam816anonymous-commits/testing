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
        val url = inputData.getString("url")

        Log.i(TAG, "AutomationWorker started for workflowId: $workflowId, url: $url")

        val workflow = DefaultWorkflows.getAllWorkflows().firstOrNull { it.id == workflowId }
            ?: if (!url.isNullOrBlank()) {
                Workflow(
                    id = "custom_url_workflow",
                    name = "Custom URL Workflow",
                    steps = listOf(
                        WorkflowStep(
                            id = "open_custom_url",
                            action = AutomationAction(type = ActionType.OPEN_URL, targetValue = url)
                        )
                    )
                )
            } else {
                DefaultWorkflows.youtubeStudioReadOnlyWorkflow
            }

        val service = AutomationAccessibilityService.instance
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.observationDao()

        if (service == null) {
            Log.e(TAG, "AccessibilityService is disabled. Recording BLOCKED state.")
            val observation = AutomationObservation(
                workflowId = workflow.id,
                packageName = workflow.targetPackage ?: "unknown",
                result = ActionResultStatus.BLOCKED.name,
                visibleTextSummary = "AccessibilityService disabled",
                errorMessage = "AccessibilityService is disabled. Automation cannot proceed."
            )
            dao.insertObservation(observation)
            return Result.failure()
        }

        val engine = WorkflowEngine(applicationContext)
        val result = engine.executeWorkflow(workflow, service)

        val textSummary = result.snapshot?.visibleTexts?.take(10)?.joinToString("; ") ?: "No UI text captured"
        val observation = AutomationObservation(
            workflowId = workflow.id,
            packageName = result.snapshot?.packageName ?: workflow.targetPackage ?: "unknown",
            result = result.status.name,
            visibleTextSummary = textSummary,
            screenshotPath = result.screenshotPath,
            errorMessage = if (result.status != ActionResultStatus.SUCCESS) result.message else null
        )

        dao.insertObservation(observation)

        return when (result.status) {
            ActionResultStatus.SUCCESS -> Result.success()
            ActionResultStatus.BLOCKED -> Result.failure()
            else -> Result.retry()
        }
    }
}
