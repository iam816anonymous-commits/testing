package com.creator.automation

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class LearnedWorkflowStatus {
    CANDIDATE,
    LEARNED,
    ACTIVE,
    PAUSED,
    AMBIGUOUS,
    FAILED
}

@Entity(tableName = "learned_workflows")
data class LearnedWorkflow(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val startingStateSignature: String,
    val status: String = LearnedWorkflowStatus.CANDIDATE.name,
    val confidence: String = ConfidenceLevel.CANDIDATE.name,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "learned_workflow_steps")
data class LearnedWorkflowStep(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val workflowId: String,
    val sequenceNumber: Int,
    val beforeStateSignature: String,
    val actionType: String,
    val targetIdentifier: String? = null,
    val afterStateSignature: String? = null,
    val verificationExpectation: String? = null,
    val confidence: String = ConfidenceLevel.CANDIDATE.name,
    val successCount: Int = 0,
    val failureCount: Int = 0
)
