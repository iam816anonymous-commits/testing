package com.creator.automation

import android.util.Log

class LearnedDecisionResolver(
    private val demonstrationDao: DemonstrationDao
) {

    companion object {
        private const val TAG = "LearnedDecisionResolver"
    }

    suspend fun resolveDecision(
        snapshot: UiSnapshot,
        globalAutonomousEnabled: Boolean = true
    ): ProposedDecision? {
        val stateSig = StateSignatureGenerator.generateSignature(snapshot)
        val records = demonstrationDao.getRecordsForState(snapshot.packageName, stateSig)

        if (records.isEmpty()) {
            Log.d(TAG, "NO_LEARNED_DECISION: No record found for state signature $stateSig")
            return null
        }

        // Check for conflicting decisions
        val distinctTargets = records.map { it.targetText ?: it.targetViewId ?: it.actionType }.distinct()
        if (distinctTargets.size > 1) {
            Log.w(TAG, "AMBIGUOUS_DECISION: Multiple conflicting actions recorded for state $stateSig")
            return ProposedDecision(
                action = null,
                record = records.first(),
                confidence = ConfidenceLevel.LOW,
                isAmbiguous = true,
                reason = "Conflicting user choices detected: ${distinctTargets.joinToString(", ")}"
            )
        }

        val record = records.first()

        // Calculate confidence based on demonstrations and success/failure counts
        val confidence = calculateConfidence(record)

        val action = when (record.actionType) {
            ActionType.CLICK_TEXT.name -> {
                record.targetText?.let {
                    AutomationAction(type = ActionType.CLICK_TEXT, targetValue = it)
                }
            }
            ActionType.GO_BACK.name -> AutomationAction(type = ActionType.GO_BACK)
            ActionType.SCROLL.name -> AutomationAction(type = ActionType.SCROLL)
            else -> null
        }

        if (action == null) return null

        val isAutonomousAllowed = globalAutonomousEnabled &&
                (confidence == ConfidenceLevel.HIGH || confidence == ConfidenceLevel.MEDIUM)

        return ProposedDecision(
            action = action,
            record = record,
            confidence = confidence,
            isAmbiguous = false,
            canAutoExecute = isAutonomousAllowed,
            reason = "Learned action '${record.actionType}' target '${record.targetText}' with $confidence confidence."
        )
    }

    fun calculateConfidence(record: DemonstrationRecord): ConfidenceLevel {
        if (record.isAmbiguous || record.failureCount > record.successCount) {
            return ConfidenceLevel.CANDIDATE
        }

        val totalPositives = record.demonstrationCount + record.successCount
        return when {
            totalPositives >= 5 && record.failureCount == 0 -> ConfidenceLevel.HIGH
            totalPositives >= 3 -> ConfidenceLevel.MEDIUM
            totalPositives >= 2 -> ConfidenceLevel.LOW
            else -> ConfidenceLevel.CANDIDATE
        }
    }
}

data class ProposedDecision(
    val action: AutomationAction?,
    val record: DemonstrationRecord,
    val confidence: ConfidenceLevel,
    val isAmbiguous: Boolean,
    val canAutoExecute: Boolean = false,
    val reason: String
)
