package com.creator.automation

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class ObservedTransition(
    val sequenceNumber: Int,
    val beforeStateSignature: String,
    val packageName: String,
    val actionType: String,
    val targetText: String?,
    var afterStateSignature: String? = null
)

class TrainingSessionManager(
    private val learnedWorkflowDao: LearnedWorkflowDao
) {

    companion object {
        private const val TAG = "TrainingSessionManager"

        private val _activeSessionId = MutableStateFlow<String?>(null)
        val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

        private val _isTrainingActive = MutableStateFlow(false)
        val isTrainingActive: StateFlow<Boolean> = _isTrainingActive.asStateFlow()

        var instance: TrainingSessionManager? = null
            private set
    }

    private val currentTransitions = mutableListOf<ObservedTransition>()

    fun startSession(): String {
        val sessionId = UUID.randomUUID().toString()
        _activeSessionId.value = sessionId
        _isTrainingActive.value = true
        currentTransitions.clear()
        AutomationAccessibilityService.setLearningMode(LearningMode.TRAINING)
        Log.i(TAG, "TRAINING_SESSION_STARTED: Session $sessionId")
        return sessionId
    }

    fun recordObservedAction(
        beforeSnapshot: UiSnapshot,
        actionType: String,
        targetText: String?
    ) {
        if (!_isTrainingActive.value) return

        val beforeSig = StateSignatureGenerator.generateSignature(beforeSnapshot)
        val seq = currentTransitions.size + 1

        val transition = ObservedTransition(
            sequenceNumber = seq,
            beforeStateSignature = beforeSig,
            packageName = beforeSnapshot.packageName,
            actionType = actionType,
            targetText = targetText
        )

        currentTransitions.add(transition)
        Log.i(TAG, "TRAINING_STEP_RECORDED: Step $seq -> $actionType ('$targetText')")
    }

    fun recordResultingState(afterSnapshot: UiSnapshot) {
        if (!_isTrainingActive.value || currentTransitions.isEmpty()) return

        val afterSig = StateSignatureGenerator.generateSignature(afterSnapshot)
        val lastIndex = currentTransitions.size - 1
        val last = currentTransitions[lastIndex]
        currentTransitions[lastIndex] = last.copy(afterStateSignature = afterSig)
        Log.i(TAG, "TRAINING_STATE_UPDATED: Step ${last.sequenceNumber} resulting state set to $afterSig")
    }

    suspend fun stopSessionAndAssembleWorkflow(
        workflowName: String = "Learned Workflow ${System.currentTimeMillis()}"
    ): LearnedWorkflow? {
        val sessionId = _activeSessionId.value
        _isTrainingActive.value = false
        _activeSessionId.value = null
        AutomationAccessibilityService.setLearningMode(LearningMode.IDLE)

        if (currentTransitions.isEmpty()) {
            Log.w(TAG, "TRAINING_SESSION_EMPTY: No transitions observed during session $sessionId")
            return null
        }

        val startingStateSig = currentTransitions.first().beforeStateSignature
        val workflowId = UUID.randomUUID().toString()

        // Check if a workflow starting from the same state already exists
        val existingWorkflows = learnedWorkflowDao.getWorkflowsForStartingState(startingStateSig)
        val hasConflicts = detectConflicts(currentTransitions)

        val workflow = LearnedWorkflow(
            id = workflowId,
            name = workflowName,
            description = "Learned from session $sessionId (${currentTransitions.size} steps)",
            startingStateSignature = startingStateSig,
            status = if (hasConflicts) LearnedWorkflowStatus.AMBIGUOUS.name else LearnedWorkflowStatus.LEARNED.name,
            confidence = if (hasConflicts) ConfidenceLevel.CANDIDATE.name else ConfidenceLevel.MEDIUM.name,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val steps = currentTransitions.map { trans ->
            LearnedWorkflowStep(
                workflowId = workflowId,
                sequenceNumber = trans.sequenceNumber,
                beforeStateSignature = trans.beforeStateSignature,
                actionType = trans.actionType,
                targetIdentifier = trans.targetText,
                afterStateSignature = trans.afterStateSignature,
                verificationExpectation = trans.targetText,
                confidence = ConfidenceLevel.MEDIUM.name
            )
        }

        learnedWorkflowDao.insertWorkflow(workflow)
        learnedWorkflowDao.insertSteps(steps)

        Log.i(TAG, "LEARNED_WORKFLOW_ASSEMBLED: Assembled '${workflow.name}' with ${steps.size} steps (Ambiguous: $hasConflicts)")
        currentTransitions.clear()
        return workflow
    }

    fun detectConflicts(transitions: List<ObservedTransition>): Boolean {
        // Detect if any identical beforeStateSignature maps to different action targets
        val groupedByState = transitions.groupBy { it.beforeStateSignature }
        for ((_, group) in groupedByState) {
            val distinctTargets = group.map { it.targetText }.distinct()
            if (distinctTargets.size > 1) {
                return true
            }
        }
        return false
    }
}
