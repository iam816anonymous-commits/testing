package com.creator.automation

import android.util.Log
import kotlinx.coroutines.delay

class WaitEngine(
    private val actionResolver: ActionResolver = ActionResolver()
) {

    companion object {
        private const val TAG = "WaitEngine"
    }

    suspend fun waitUntil(
        condition: WaitCondition,
        service: AutomationAccessibilityService?,
        screenProvider: ObservationProvider? = null,
        initialSignature: String? = null
    ): WaitResult {
        val startTime = System.currentTimeMillis()
        val timeoutMs = condition.timeoutMs
        val pollInterval = maxOf(100L, condition.pollIntervalMs)

        Log.i(TAG, "WAIT_STARTED: ConditionType=${condition.type}, Expected='${condition.expectedValue}', Timeout=${timeoutMs}ms")

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val root = service?.getRootNode()
            val currentSnapshot = if (service != null && root != null) {
                ActionResolver.captureSnapshot(root, service.packageName ?: "")
            } else null

            val currentSig = currentSnapshot?.let { StateSignatureGenerator.generateSignature(it) }

            val satisfied = when (condition.type) {
                WaitConditionType.WAIT_FOR_TEXT -> {
                    val target = condition.expectedValue ?: ""
                    currentSnapshot != null && currentSnapshot.visibleTexts.any { it.contains(target, ignoreCase = true) }
                }
                WaitConditionType.WAIT_FOR_VIEW_ID -> {
                    val viewId = condition.expectedValue ?: ""
                    currentSnapshot != null && currentSnapshot.viewIds.any { it.contains(viewId, ignoreCase = true) }
                }
                WaitConditionType.WAIT_FOR_PACKAGE -> {
                    val pkg = condition.expectedValue ?: ""
                    currentSnapshot != null && currentSnapshot.packageName.equals(pkg, ignoreCase = true)
                }
                WaitConditionType.WAIT_FOR_STATE_SIGNATURE -> {
                    val expectedSig = condition.expectedValue ?: ""
                    currentSig == expectedSig
                }
                WaitConditionType.WAIT_FOR_STATE_CHANGE -> {
                    initialSignature != null && currentSig != null && currentSig != initialSignature
                }
                WaitConditionType.WAIT_FOR_VISUAL_CHANGE -> {
                    if (screenProvider != null) {
                        val screenObs = screenProvider.captureObservation()
                        screenObs.visualChangeState == VisualChangeResult.VISUAL_CHANGE.name
                    } else false
                }
            }

            if (satisfied) {
                val duration = System.currentTimeMillis() - startTime
                Log.i(TAG, "WAIT_SATISFIED: Condition satisfied in ${duration}ms")
                return WaitResult(
                    success = true,
                    durationMs = duration,
                    matchedValue = condition.expectedValue
                )
            }

            delay(pollInterval)
        }

        val duration = System.currentTimeMillis() - startTime
        Log.w(TAG, "WAIT_TIMEOUT: Condition '${condition.type}' not met after ${duration}ms")
        return WaitResult(
            success = false,
            durationMs = duration,
            failureReason = "Timeout waiting for ${condition.type} ('${condition.expectedValue}')"
        )
    }
}
