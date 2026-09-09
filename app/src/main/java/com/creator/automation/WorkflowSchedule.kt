package com.creator.automation

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workflow_schedules")
data class WorkflowSchedule(
    @PrimaryKey
    val workflowId: String,
    val enabled: Boolean = false,
    val intervalMinutes: Long = 1440L, // Default 24 hours
    val requiresCharging: Boolean = false,
    val requiresBatteryNotLow: Boolean = true,
    val lastScheduledTimestamp: Long = 0L,
    val lastExecutionResult: String = "NEVER",
    val nextExpectedRunTimestamp: Long = 0L
)
