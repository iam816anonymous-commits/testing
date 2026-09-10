package com.creator.automation

import android.content.Context
import android.util.Log

enum class ProposalStatus {
    PROPOSED,
    APPROVED,
    REJECTED,
    TESTED_PASSED,
    TESTED_FAILED
}

data class RDImprovementProposal(
    val id: String = java.util.UUID.randomUUID().toString(),
    val problemDescription: String,
    val failurePattern: String,
    val proposedStrategy: String,
    val requiresUserApproval: Boolean = true,
    val status: ProposalStatus = ProposalStatus.PROPOSED,
    val timestamp: Long = System.currentTimeMillis()
)

class AgentRAndDEngine(private val context: Context) {

    companion object {
        private const val TAG = "AgentRAndDEngine"
    }

    /**
     * Analyzes recent ActionAuditRecord logs from Room DB to identify high-frequency failure patterns.
     */
    suspend fun analyzeAuditFailures(auditDao: ActionAuditDao): List<RDImprovementProposal> {
        val proposals = mutableListOf<RDImprovementProposal>()
        try {
            val records = auditDao.getAllAuditRecords()
            val failedRecords = records.filter { !it.success }

            val targetNotFoundCount = failedRecords.count { it.reason == ExecutionReason.UI_NOT_FOUND.name }
            val ambiguousCount = failedRecords.count { it.reason == ExecutionReason.AMBIGUOUS_TARGET.name }
            val unsupportedSubmitCount = failedRecords.count { it.reason == ExecutionReason.UNSUPPORTED_SUBMISSION_MECHANISM.name }

            if (targetNotFoundCount >= 3) {
                proposals.add(
                    RDImprovementProposal(
                        problemDescription = "High incidence ($targetNotFoundCount) of UI_NOT_FOUND errors during target resolution",
                        failurePattern = "UI_NOT_FOUND",
                        proposedStrategy = "Apply ClosePaw parent container node climbing and AutoDroid candidate scoring to improve target resolution",
                        requiresUserApproval = false
                    )
                )
            }

            if (ambiguousCount >= 2) {
                proposals.add(
                    RDImprovementProposal(
                        problemDescription = "Multiple ambiguous candidates ($ambiguousCount) detected for single text target",
                        failurePattern = "AMBIGUOUS_TARGET",
                        proposedStrategy = "Enforce strict candidate scoring and prompt user for explicit target selection",
                        requiresUserApproval = true
                    )
                )
            }

            if (unsupportedSubmitCount >= 2) {
                proposals.add(
                    RDImprovementProposal(
                        problemDescription = "Input submission ($unsupportedSubmitCount) failed due to missing explicit submit control on API 27 UI",
                        failurePattern = "UNSUPPORTED_SUBMISSION_MECHANISM",
                        proposedStrategy = "Attempt bounds-derived soft keyboard search key tap fallback",
                        requiresUserApproval = true
                    )
                )
            }

            Log.i(TAG, "RD_ANALYSIS_COMPLETED: Generated ${proposals.size} improvement proposal(s) from ${failedRecords.size} failure logs")
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing audit failures for R&D", e)
        }
        return proposals
    }
}
