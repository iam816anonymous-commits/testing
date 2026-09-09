package com.creator.automation

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ConfidenceLevel {
    CANDIDATE,
    LOW,
    MEDIUM,
    HIGH
}

enum class LearningMode {
    IDLE,
    TRAINING,
    ASSISTED,
    AUTONOMOUS
}

@Entity(tableName = "demonstration_records")
data class DemonstrationRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val stateSignature: String,
    val actionType: String, // e.g. CLICK_TEXT
    val targetText: String? = null,
    val targetViewId: String? = null,
    val targetContentDesc: String? = null,
    val resultingStateSignature: String? = null,
    val confidenceLevel: String = ConfidenceLevel.CANDIDATE.name,
    val demonstrationCount: Int = 1,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val isAmbiguous: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
