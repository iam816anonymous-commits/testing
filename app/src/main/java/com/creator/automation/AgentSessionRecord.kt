package com.creator.automation

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "agent_session_records")
data class AgentSessionRecord(
    @PrimaryKey
    val sessionId: String = UUID.randomUUID().toString(),
    val taskId: String? = null,
    val taskDescription: String,
    val currentState: String = AgentState.IDLE.name,
    val currentStepIndex: Int = 0,
    val checkpointStateSignature: String? = null,
    val lastObservationSummary: String? = null,
    val lastActionResultStatus: String? = null,
    val lastActionResultMessage: String? = null,
    val lastVerificationStatus: String? = null,
    val recoveryAttemptCount: Int = 0,
    val cancellationReason: String? = null,
    val failureReason: String? = null,
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false,
    val isInterrupted: Boolean = false,
    val startTimestamp: Long = System.currentTimeMillis(),
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
    val completionTimestamp: Long? = null
)
