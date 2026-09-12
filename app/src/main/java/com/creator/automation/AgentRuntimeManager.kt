package com.creator.automation

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AgentRuntimeManager(
    private val context: Context,
    private val sessionDao: AgentSessionDao = AppDatabase.getDatabase(context).agentSessionDao(),
    private val auditDao: ActionAuditDao = AppDatabase.getDatabase(context).actionAuditDao(),
    private val agentCore: AgentCore = AgentCore(context),
    private val observationProvider: ObservationProvider = AccessibilityObservationProvider()
) {

    companion object {
        private const val TAG = "AgentRuntimeManager"

        private val _activeSession = MutableStateFlow<AgentSessionRecord?>(null)
        val activeSession: StateFlow<AgentSessionRecord?> = _activeSession.asStateFlow()

        private val _runtimeLogs = MutableStateFlow<List<String>>(emptyList())
        val runtimeLogs: StateFlow<List<String>> = _runtimeLogs.asStateFlow()

        private fun logRuntimeActivity(message: String) {
            Log.i(TAG, message)
            val current = _runtimeLogs.value.toMutableList()
            current.add(0, "[${System.currentTimeMillis() % 100000}] $message")
            _runtimeLogs.value = current.take(25)
        }
    }

    suspend fun startOrResumeTaskSession(
        taskDescription: String,
        globalAutonomousEnabled: Boolean = true,
        trigger: ExecutionTrigger = ExecutionTrigger.MANUAL
    ): AgentStepResult = withContext(Dispatchers.IO) {
        logRuntimeActivity("RUNTIME_START: Request task '$taskDescription'")

        val existing = sessionDao.getActiveSessionByTaskDescription(taskDescription)
        val session = if (existing != null) {
            logRuntimeActivity("RUNTIME_SESSION_EXISTS: Reusing existing session ${existing.sessionId}")
            existing
        } else {
            val newSession = AgentSessionRecord(
                taskDescription = taskDescription,
                currentState = AgentState.IDLE.name
            )
            sessionDao.insertSession(newSession)
            logRuntimeActivity("RUNTIME_SESSION_CREATED: Created new session ${newSession.sessionId}")
            newSession
        }

        _activeSession.value = session

        // Update checkpoint: OBSERVING
        checkpointSession(
            session = session.copy(
                currentState = AgentState.OBSERVING.name,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
        )

        // Execute task step via AgentCore
        val stepResult = agentCore.executeTaskStep(
            taskDescription = taskDescription,
            trigger = trigger,
            globalAutonomousEnabled = globalAutonomousEnabled
        )

        // Update checkpoint post-execution
        val finalState = stepResult.nextState
        val updatedSession = session.copy(
            currentState = finalState.name,
            checkpointStateSignature = stepResult.observation?.stateSignature,
            lastObservationSummary = stepResult.observation?.summary,
            lastActionResultStatus = stepResult.actionResult?.status?.name,
            lastActionResultMessage = stepResult.decisionReason ?: stepResult.actionResult?.message,
            lastVerificationStatus = stepResult.verificationStatus.name,
            isCompleted = (finalState == AgentState.COMPLETED || finalState == AgentState.FAILED),
            isCancelled = (finalState == AgentState.CANCELLED),
            completionTimestamp = if (finalState == AgentState.COMPLETED || finalState == AgentState.FAILED) System.currentTimeMillis() else null,
            lastUpdatedTimestamp = System.currentTimeMillis()
        )

        checkpointSession(updatedSession)
        logRuntimeActivity("RUNTIME_STEP_FINISHED: Session ${session.sessionId} state=${finalState.name}")

        return@withContext stepResult
    }

    suspend fun recoverInterruptedSessions(): Int = withContext(Dispatchers.IO) {
        logRuntimeActivity("RECOVERY_CHECK: Scanning for process-death interrupted sessions")
        val interruptedList = sessionDao.getActiveOrInterruptedSessions()

        if (interruptedList.isEmpty()) {
            logRuntimeActivity("RECOVERY_CHECK: No interrupted agent sessions found")
            return@withContext 0
        }

        var recoveredCount = 0
        for (session in interruptedList) {
            logRuntimeActivity("RECOVERY_START: Recovering session ${session.sessionId} (attempts=${session.recoveryAttemptCount})")

            if (session.recoveryAttemptCount >= 2) {
                logRuntimeActivity("RECOVERY_LIMIT: Session ${session.sessionId} reached max recovery attempts (2). Marking FAILED.")
                val failedSession = session.copy(
                    currentState = AgentState.FAILED.name,
                    failureReason = "Max recovery attempts reached after process death",
                    isCompleted = true,
                    completionTimestamp = System.currentTimeMillis(),
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
                checkpointSession(failedSession)
                continue
            }

            // Mark session as RECOVERING and increment attempt counter
            val recoveringSession = session.copy(
                currentState = AgentState.RECOVERING.name,
                isInterrupted = true,
                recoveryAttemptCount = session.recoveryAttemptCount + 1,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
            checkpointSession(recoveringSession)

            // CRITICAL RULE: Observe current device before acting
            logRuntimeActivity("RECOVERY_OBSERVE: Observing fresh device state post-process-death")
            val currentObs = observationProvider.captureObservation()

            // Compare state signature with checkpoint
            val prevSig = session.checkpointStateSignature
            if (prevSig != null && currentObs.stateSignature != prevSig) {
                logRuntimeActivity("RECOVERY_STATE_CHANGED: State signature changed from '$prevSig' to '${currentObs.stateSignature}'. Safe re-evaluation required.")
            }

            // Inspect last action audit record to check action semantics safety during process death recovery (Case D safety check)
            val lastAudit = auditDao.getLatestAuditRecord()

            if (lastAudit != null) {
                val actionSummary = lastAudit.actionParametersSummary ?: ""
                val isNonIdempotentOrHighRisk = actionSummary.contains("NON_IDEMPOTENT") || actionSummary.contains("HIGH_RISK")
                val isUnverified = lastAudit.verificationStatus != VerificationStatus.SUCCESSFULLY_VERIFIED.name

                if (isNonIdempotentOrHighRisk && isUnverified) {
                    logRuntimeActivity("RECOVERY_SAFETY_HALT: Last action '${lastAudit.actionType}' was NON_IDEMPOTENT/HIGH_RISK with unverified outcome post-process-death. Requiring user intervention.")
                    val needsUserSession = recoveringSession.copy(
                        currentState = AgentState.NEEDS_USER_INPUT.name,
                        failureReason = "Uncertain outcome of NON_IDEMPOTENT/HIGH_RISK action '${lastAudit.actionType}' after process death. User intervention required.",
                        isInterrupted = false,
                        lastUpdatedTimestamp = System.currentTimeMillis()
                    )
                    checkpointSession(needsUserSession)
                    recoveredCount++
                    continue
                }
            }

            // Safely resume task step execution if re-execution is safe
            val stepResult = agentCore.executeTaskStep(
                taskDescription = session.taskDescription,
                trigger = ExecutionTrigger.SCHEDULED,
                globalAutonomousEnabled = true
            )

            val nextState = stepResult.nextState
            val finalRecovered = recoveringSession.copy(
                currentState = nextState.name,
                checkpointStateSignature = currentObs.stateSignature,
                lastObservationSummary = currentObs.summary,
                isCompleted = (nextState == AgentState.COMPLETED || nextState == AgentState.FAILED),
                isInterrupted = false,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )

            checkpointSession(finalRecovered)
            recoveredCount++
            logRuntimeActivity("RECOVERY_SUCCESS: Session ${session.sessionId} recovered to state=${nextState.name}")
        }

        return@withContext recoveredCount
    }

    suspend fun cancelActiveSession(reason: String = "User cancelled session"): Boolean = withContext(Dispatchers.IO) {
        val current = _activeSession.value ?: return@withContext false
        logRuntimeActivity("RUNTIME_CANCEL: Cancelling session ${current.sessionId} ($reason)")

        agentCore.cancelAgent()

        val cancelledSession = current.copy(
            currentState = AgentState.CANCELLED.name,
            isCancelled = true,
            cancellationReason = reason,
            lastUpdatedTimestamp = System.currentTimeMillis()
        )

        checkpointSession(cancelledSession)
        return@withContext true
    }

    suspend fun pauseRuntime() = withContext(Dispatchers.IO) {
        agentCore.pauseAgent()
        val current = _activeSession.value
        if (current != null) {
            val paused = current.copy(
                currentState = AgentState.PAUSED.name,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
            checkpointSession(paused)
        }
        logRuntimeActivity("RUNTIME_PAUSE: Runtime explicitly paused")
    }

    suspend fun resumeRuntime() = withContext(Dispatchers.IO) {
        agentCore.resumeAgent()
        val current = _activeSession.value
        if (current != null) {
            val resumed = current.copy(
                currentState = AgentState.IDLE.name,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
            checkpointSession(resumed)
        }
        logRuntimeActivity("RUNTIME_RESUME: Runtime resumed")
    }

    private suspend fun checkpointSession(session: AgentSessionRecord) {
        sessionDao.insertSession(session)
        _activeSession.value = session
    }
}
