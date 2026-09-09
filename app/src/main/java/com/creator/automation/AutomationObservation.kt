package com.creator.automation

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "automation_observations")
data class AutomationObservation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workflowId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val result: String,
    val reason: String = "NONE",
    val visibleTextSummary: String,
    val screenshotPath: String? = null,
    val errorMessage: String? = null
)
