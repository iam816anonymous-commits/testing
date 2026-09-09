package com.creator.automation

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class TaskStatus {
    PENDING,
    PLANNING,
    EXECUTING,
    WAITING_FOR_REASONING,
    PAUSED,
    SUCCESS,
    FAILED,
    CANCELLED
}

enum class TaskSource {
    USER,
    LEARNED_WORKFLOW,
    LOCAL_RULE,
    CHATGPT
}

enum class ResolutionReason {
    LEARNED_WORKFLOW_MATCH,
    LOCAL_WORKFLOW_MATCH,
    CHATGPT_FALLBACK,
    NO_RESOLUTION,
    CHATGPT_UNAVAILABLE
}

@Entity(tableName = "task_records")
data class TaskRecord(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val status: String = TaskStatus.PENDING.name,
    val source: String = TaskSource.USER.name,
    val resolutionReason: String = ResolutionReason.NO_RESOLUTION.name,
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val lastKnownStateSignature: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ReasoningRequest(
    val taskDescription: String,
    val currentApp: String,
    val currentStateSignature: String,
    val visibleUISummary: String,
    val availableActionTypes: List<String>,
    val knownRelevantWorkflows: List<String> = emptyList()
)

data class ReasoningStep(
    val sequenceNumber: Int,
    val actionType: String,
    val targetHint: String?,
    val expectedStateHint: String? = null
)

data class ReasoningPlan(
    val taskId: String,
    val taskDescription: String,
    val steps: List<ReasoningStep>,
    val isValid: Boolean = true,
    val rejectionReason: String? = null
)
