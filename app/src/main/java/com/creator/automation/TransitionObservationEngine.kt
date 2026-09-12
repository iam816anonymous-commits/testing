package com.creator.automation

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class TransitionObservationEngine(
    private val dao: DiscoveredTransitionDao? = null
) {

    companion object {
        private const val TAG = "TransitionObsEngine"

        private val _lastTransition = MutableStateFlow<DiscoveredTransition?>(null)
        val lastTransition: StateFlow<DiscoveredTransition?> = _lastTransition.asStateFlow()
    }

    /**
     * Observes and computes state transition after executing an action on an interaction surface.
     */
    suspend fun observeTransition(
        packageName: String,
        preSnapshot: UiSnapshot?,
        postSnapshot: UiSnapshot?,
        actionType: ActionType,
        targetSurfaceId: String,
        verificationResult: GoalVerificationResult?
    ): DiscoveredTransition {
        val preSig = if (preSnapshot != null) StateSignatureGenerator.generateSignature(preSnapshot) else "STATE_UNKNOWN_PRE"
        val postSig = if (postSnapshot != null) StateSignatureGenerator.generateSignature(postSnapshot) else "STATE_UNKNOWN_POST"

        val isStateChanged = preSig != postSig && postSig != "STATE_UNKNOWN_POST"
        val verificationStatus = verificationResult?.status ?: GoalVerificationStatus.UNKNOWN

        // Calculate dynamic confidence score based on state change and verification status
        val confidence = when {
            verificationResult?.isVerified == true && isStateChanged -> 0.95
            verificationResult?.isVerified == true -> 0.85
            isStateChanged -> 0.70
            else -> 0.30
        }

        val transition = DiscoveredTransition(
            transitionId = UUID.randomUUID().toString(),
            packageName = packageName,
            sourceStateSignature = preSig,
            actionType = actionType,
            targetSurfaceId = targetSurfaceId,
            targetStateSignature = postSig,
            isStateChanged = isStateChanged,
            verificationStatus = verificationStatus,
            confidence = confidence
        )

        _lastTransition.value = transition

        // Persist transition record
        if (dao != null) {
            try {
                val record = DiscoveredTransitionRecord(
                    transitionId = transition.transitionId,
                    packageName = packageName,
                    sourceStateSignature = preSig,
                    actionType = actionType.name,
                    targetSurfaceId = targetSurfaceId,
                    targetStateSignature = postSig,
                    isStateChanged = isStateChanged,
                    verificationStatus = verificationStatus.name,
                    confidence = confidence,
                    timestamp = transition.timestamp
                )
                dao.insertTransition(record)
                Log.i(TAG, "TRANSITION_PERSISTED: $packageName [$preSig] --($actionType)--> [$postSig] (Changed: $isStateChanged, Conf: $confidence)")
            } catch (e: Throwable) {
                Log.e(TAG, "Error persisting transition: ${e.message}", e)
            }
        }

        return transition
    }

    /**
     * Assembles a dynamic interaction flow graph for an app package.
     */
    fun assembleFlowGraph(
        packageName: String,
        transitions: List<DiscoveredTransition>
    ): InteractionFlowGraph {
        val states = mutableSetOf<String>()
        transitions.forEach {
            states.add(it.sourceStateSignature)
            states.add(it.targetStateSignature)
        }

        val avgConfidence = if (transitions.isNotEmpty()) transitions.map { it.confidence }.average() else 0.5

        return InteractionFlowGraph(
            packageName = packageName,
            knownStates = states.toList(),
            transitions = transitions,
            totalExplorations = transitions.size,
            confidenceScore = avgConfidence
        )
    }
}
