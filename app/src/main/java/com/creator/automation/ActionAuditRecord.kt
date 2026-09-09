package com.creator.automation

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class VerificationStatus {
    DISPATCHED,
    SUCCESSFULLY_VERIFIED,
    FAILED,
    UNKNOWN
}

enum class StateChangeResult {
    NO_CHANGE,
    STATE_CHANGED,
    EXPECTED_STATE_REACHED,
    UNEXPECTED_STATE,
    UNKNOWN
}

@Entity(tableName = "action_audit_records")
data class ActionAuditRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val workflowId: String,
    val trigger: String = "MANUAL",
    val activePackage: String,
    val beforeStateSignature: String,
    val actionType: String,
    val targetIdentifier: String? = null,
    val actionParametersSummary: String? = null,
    val reason: String = "NONE",
    val confidence: String = "HIGH",
    val dispatchResult: String, // e.g. SUCCESS / FAILED
    val afterStateSignature: String? = null,
    val stateChangeResult: String = StateChangeResult.UNKNOWN.name,
    val verificationStatus: String = VerificationStatus.UNKNOWN.name,
    val success: Boolean = false,
    val failureReason: String? = null,
    val durationMs: Long = 0L
)
