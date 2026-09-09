package com.creator.automation

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

class AutomationScheduler(
    private val context: Context,
    private val scheduleDao: WorkflowScheduleDao = AppDatabase.getDatabase(context).scheduleDao()
) {

    companion object {
        private const val TAG = "AutomationScheduler"

        fun getUniqueWorkName(workflowId: String): String {
            return "automation_workflow_$workflowId"
        }
    }

    suspend fun scheduleWorkflow(
        schedule: WorkflowSchedule
    ) {
        val uniqueWorkName = getUniqueWorkName(schedule.workflowId)
        val workManager = WorkManager.getInstance(context)

        if (!schedule.enabled) {
            Log.i(TAG, "CANCEL_SCHEDULE: Cancelling WorkManager task for $uniqueWorkName")
            workManager.cancelUniqueWork(uniqueWorkName)
            scheduleDao.insertOrUpdateSchedule(
                schedule.copy(
                    enabled = false,
                    nextExpectedRunTimestamp = 0L
                )
            )
            return
        }

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(schedule.requiresBatteryNotLow)
            .setRequiresCharging(schedule.requiresCharging)
            .build()

        val inputData = workDataOf(
            "workflowId" to schedule.workflowId,
            "executionTrigger" to ExecutionTrigger.SCHEDULED.name
        )

        val intervalMinutes = maxOf(15L, schedule.intervalMinutes) // WorkManager minimum interval is 15 mins
        val periodicRequest = PeriodicWorkRequestBuilder<AutomationWorker>(
            intervalMinutes, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()

        // Enqueue unique periodic work with UPDATE policy to prevent duplicates
        workManager.enqueueUniquePeriodicWork(
            uniqueWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )

        val now = System.currentTimeMillis()
        val nextExpected = now + (intervalMinutes * 60 * 1000L)

        val updatedSchedule = schedule.copy(
            enabled = true,
            lastScheduledTimestamp = now,
            nextExpectedRunTimestamp = nextExpected
        )

        scheduleDao.insertOrUpdateSchedule(updatedSchedule)
        Log.i(TAG, "ENQUEUED_SCHEDULE: Workflow ${schedule.workflowId} scheduled every $intervalMinutes min. Next expected run at: $nextExpected")
    }

    suspend fun cancelSchedule(workflowId: String) {
        val uniqueWorkName = getUniqueWorkName(workflowId)
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName)

        val existing = scheduleDao.getSchedule(workflowId)
        if (existing != null) {
            scheduleDao.insertOrUpdateSchedule(
                existing.copy(
                    enabled = false,
                    nextExpectedRunTimestamp = 0L
                )
            )
        }
        Log.i(TAG, "CANCELLED_SCHEDULE: Workflow $workflowId")
    }

    suspend fun restoreAllSchedules() {
        val enabledSchedules = scheduleDao.getEnabledSchedules()
        Log.i(TAG, "RESTORE_SCHEDULES: Restoring ${enabledSchedules.size} enabled schedules on boot/startup")
        for (schedule in enabledSchedules) {
            scheduleWorkflow(schedule)
        }
    }
}
