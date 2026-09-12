package com.creator.automation

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AgentCore(
    private val context: Context,
    private val observationProvider: ObservationProvider = AccessibilityObservationProvider(),
    private val screenObservationProvider: ObservationProvider = ScreenObservationProvider(context),
    private val cameraObservationProvider: ObservationProvider = CameraObservationProvider(context),
    private val workflowEngine: WorkflowEngine = WorkflowEngine(context),
    private val recoveryManager: RecoveryManager = RecoveryManager()
) {

    companion object {
        private const val TAG = "AgentCore"

        private val _agentState = MutableStateFlow(AgentState.IDLE)
        val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

        private val _currentTaskRecord = MutableStateFlow<TaskRecord?>(null)
        val currentTaskRecord: StateFlow<TaskRecord?> = _currentTaskRecord.asStateFlow()

        private val _recentAgentLogs = MutableStateFlow<List<String>>(emptyList())
        val recentAgentLogs: StateFlow<List<String>> = _recentAgentLogs.asStateFlow()

        private val _lastDiscoveredSurfaces = MutableStateFlow<List<InteractionSurface>>(emptyList())
        val lastDiscoveredSurfaces: StateFlow<List<InteractionSurface>> = _lastDiscoveredSurfaces.asStateFlow()

        private val _lastDiscoveredCandidates = MutableStateFlow<List<InteractionCandidate>>(emptyList())
        val lastDiscoveredCandidates: StateFlow<List<InteractionCandidate>> = _lastDiscoveredCandidates.asStateFlow()

        private fun logAgentActivity(message: String) {
            Log.i(TAG, message)
            val current = _recentAgentLogs.value.toMutableList()
            current.add(0, "[${System.currentTimeMillis() % 100000}] $message")
            _recentAgentLogs.value = current.take(20)
        }

        fun pauseAgent() {
            _agentState.value = AgentState.PAUSED
            logAgentActivity("AGENT_PAUSED: Agent explicitly paused by user")
        }

        fun resumeAgent() {
            _agentState.value = AgentState.IDLE
            logAgentActivity("AGENT_RESUMED: Agent resumed from pause")
        }

        fun cancelAgent() {
            _agentState.value = AgentState.CANCELLED
            logAgentActivity("AGENT_CANCELLED: Agent task execution cancelled")
        }
    }

    fun pauseAgent() {
        Companion.pauseAgent()
    }

    fun resumeAgent() {
        Companion.resumeAgent()
    }

    fun cancelAgent() {
        Companion.cancelAgent()
    }

    suspend fun executeTaskStep(
        taskDescription: String,
        trigger: ExecutionTrigger = ExecutionTrigger.MANUAL,
        globalAutonomousEnabled: Boolean = true
    ): AgentStepResult {
        if (_agentState.value == AgentState.CANCELLED) {
            logAgentActivity("AGENT_CANCELLED: Execution aborted by user cancellation")
            return AgentStepResult(
                stateBefore = AgentState.CANCELLED,
                observation = null,
                decisionReason = "Agent task cancelled",
                actionExecuted = null,
                actionResult = null,
                verificationStatus = VerificationStatus.FAILED,
                nextState = AgentState.CANCELLED
            )
        }

        if (_agentState.value == AgentState.PAUSED) {
            logAgentActivity("AGENT_PAUSED: Execution halted due to pause state")
            return AgentStepResult(
                stateBefore = AgentState.PAUSED,
                observation = null,
                decisionReason = "Agent loop is currently PAUSED",
                actionExecuted = null,
                actionResult = null,
                verificationStatus = VerificationStatus.FAILED,
                nextState = AgentState.PAUSED
            )
        }

        // 1. OBSERVING (Observe before action - Multi-Source Hierarchy: Accessibility -> Screen -> Camera)
        _agentState.value = AgentState.OBSERVING
        AutomationOverlayState.updateState(VisualizationActionState.OBSERVING)
        logAgentActivity("AGENT_OBSERVING: Capturing current device observation (Primary: Accessibility)")
        var primaryObservation = observationProvider.captureObservation()

        var screenObservation: CurrentObservation? = null
        val isScreenAuthorized = ScreenObservationProvider.isAuthorized.value

        var cameraObservation: CurrentObservation? = null
        val isCameraRunning = CameraObservationProvider.isCameraRunning.value

        if (isScreenAuthorized) {
            logAgentActivity("AGENT_OBSERVING: Capturing Screen perception observation")
            screenObservation = screenObservationProvider.captureObservation()
        }

        if (isCameraRunning) {
            logAgentActivity("AGENT_OBSERVING: Capturing Camera perception observation")
            cameraObservation = cameraObservationProvider.captureObservation()
        }

        // Multi-Source Fallback Hierarchy
        if (primaryObservation.confidence < 0.5 || primaryObservation.snapshot?.totalNodeCount == 0) {
            if (screenObservation != null && screenObservation.confidence >= 0.5) {
                logAgentActivity("AGENT_OBSERVING: Accessibility insufficient. Falling back to Screen Observation (${screenObservation.visualSignature}).")
                primaryObservation = screenObservation
            } else if (cameraObservation != null && cameraObservation.confidence >= 0.5) {
                logAgentActivity("AGENT_OBSERVING: Accessibility & Screen insufficient. Falling back to Camera Observation (${cameraObservation.visualSignature}).")
                primaryObservation = cameraObservation
            }
        }

        // Build ApplicationWorldState, ActionGraph & Generic App Interaction Discovery
        val snap = primaryObservation.snapshot
        if (snap != null) {
            val appWorldState = ApplicationWorldState.fromSnapshot(snap)
            val actionGraph = ActionGraph.buildFromWorldState(appWorldState)

            val surfaces = InteractionDiscoveryEngine.discoverSurfaces(snap)
            val candidates = InteractionDiscoveryEngine.generateCandidates(surfaces)

            _lastDiscoveredSurfaces.value = surfaces
            _lastDiscoveredCandidates.value = candidates

            logAgentActivity("WORLD_STATE_BUILT: Pkg=${appWorldState.packageName}, Interactive=${appWorldState.interactiveNodes.size}, ActionGraphTransitions=${actionGraph.availableTransitions.size}, DiscoveredSurfaces=${surfaces.size}, SafeCandidates=${candidates.count { it.safetyLevel == InteractionSafetyLevel.SAFE_TO_EXPLORE }}")
        }

        if (_agentState.value == AgentState.CANCELLED) {
            logAgentActivity("AGENT_CANCELLED: Aborting execution prior to resolving step")
            return AgentStepResult(AgentState.CANCELLED, primaryObservation, "Cancelled by user", null, null, VerificationStatus.FAILED, AgentState.CANCELLED)
        }

        // 2. RESOLVING & PLANNING & EXECUTING (One bounded cycle)
        _agentState.value = AgentState.RESOLVING
        AutomationOverlayState.updateState(VisualizationActionState.TARGET_FOUND, targetText = taskDescription, packageName = primaryObservation.packageName)
        logAgentActivity("AGENT_RESOLVING: Resolving task '$taskDescription' against state ${primaryObservation.stateSignature}")

        if (_agentState.value == AgentState.CANCELLED) {
            logAgentActivity("AGENT_CANCELLED: Aborting execution prior to action dispatch")
            return AgentStepResult(AgentState.CANCELLED, primaryObservation, "Cancelled by user", null, null, VerificationStatus.FAILED, AgentState.CANCELLED)
        }

        _agentState.value = AgentState.EXECUTING
        logAgentActivity("AGENT_EXECUTING: Executing next step for task '$taskDescription'")

        val result = workflowEngine.resolveAndExecuteTask(
            taskDescription = taskDescription,
            trigger = trigger,
            globalAutonomousEnabled = globalAutonomousEnabled
        )

        if (_agentState.value == AgentState.CANCELLED) {
            logAgentActivity("AGENT_CANCELLED: Aborting step verification following user cancellation")
            return AgentStepResult(AgentState.CANCELLED, primaryObservation, "Cancelled by user", null, result, VerificationStatus.FAILED, AgentState.CANCELLED)
        }

        // 3. VERIFYING (Observe after action - Semantic + Visual Screen + Camera verification)
        _agentState.value = AgentState.VERIFYING
        AutomationOverlayState.updateState(VisualizationActionState.VERIFYING, targetText = taskDescription, packageName = primaryObservation.packageName)
        val postObs = observationProvider.captureObservation()

        var visualVerificationDetails = ""
        if (isScreenAuthorized) {
            val postScreenObs = screenObservationProvider.captureObservation()
            visualVerificationDetails += ", screenChange=${postScreenObs.visualChangeState}"
        }
        if (isCameraRunning) {
            val postCameraObs = cameraObservationProvider.captureObservation()
            visualVerificationDetails += ", cameraChange=${postCameraObs.visualChangeState}"
        }

        val goalVerifier = GoalVerifier()
        val isHardwareTask = HardwareActuatorRegistry.findActuatorForGoal(taskDescription) != null
        val hwVerifyRes = if (isHardwareTask) goalVerifier.verifyHardwareGoal(taskDescription, context) else null

        if (hwVerifyRes != null) {
            logAgentActivity("HARDWARE_GOAL_VERIFICATION: Verified=${hwVerifyRes.isVerified}, Details='${hwVerifyRes.explanation}'")
        }

        val verificationStatus = if (result.status == ActionResultStatus.SUCCESS && (hwVerifyRes == null || hwVerifyRes.isVerified)) {
            VerificationStatus.SUCCESSFULLY_VERIFIED
        } else {
            VerificationStatus.FAILED
        }

        // Record Transition Observation
        try {
            val db = AppDatabase.getDatabase(context)
            val transitionEngine = TransitionObservationEngine(db.discoveredTransitionDao())

            val resolvedActionType = when {
                taskDescription.contains("home", ignoreCase = true) -> ActionType.PRESS_HOME
                taskDescription.contains("back", ignoreCase = true) -> ActionType.GO_BACK
                taskDescription.contains("recents", ignoreCase = true) -> ActionType.PRESS_RECENTS
                taskDescription.contains("scroll", ignoreCase = true) -> ActionType.SCROLL_DOWN
                taskDescription.contains("type", ignoreCase = true) -> ActionType.TYPE_TEXT
                taskDescription.contains("flash", ignoreCase = true) || taskDescription.contains("vibrate", ignoreCase = true) || taskDescription.contains("mute", ignoreCase = true) -> ActionType.TOGGLE_HARDWARE
                taskDescription.contains("search", ignoreCase = true) || taskDescription.contains("submit", ignoreCase = true) -> ActionType.SUBMIT_INPUT
                else -> ActionType.CLICK_TEXT
            }

            val targetSurfaceId = result.matchedNode?.viewIdResourceName
                ?: result.matchedNode?.text
                ?: result.matchedNode?.contentDescription
                ?: taskDescription

            val transitionRes = transitionEngine.observeTransition(
                packageName = primaryObservation.packageName,
                preSnapshot = snap,
                postSnapshot = postObs.snapshot,
                actionType = resolvedActionType,
                targetSurfaceId = targetSurfaceId,
                verificationResult = hwVerifyRes ?: GoalVerificationResult(
                    status = if (verificationStatus == VerificationStatus.SUCCESSFULLY_VERIFIED) GoalVerificationStatus.GOAL_VERIFIED else GoalVerificationStatus.GOAL_NOT_REACHED,
                    isVerified = verificationStatus == VerificationStatus.SUCCESSFULLY_VERIFIED,
                    explanation = result.message ?: "Step execution result"
                )
            )
            logAgentActivity("TRANSITION_OBSERVED: Action=$resolvedActionType, Surface='$targetSurfaceId', Changed=${transitionRes.isStateChanged}, Conf=${transitionRes.confidence}")
        } catch (e: Throwable) {
            Log.e(TAG, "Error recording transition observation: ${e.message}", e)
        }

        logAgentActivity("AGENT_VERIFYING: Post-action state = ${postObs.stateSignature}$visualVerificationDetails, Verification = $verificationStatus")

        // 4. LEARNING & STATE EVALUATION
        _agentState.value = AgentState.LEARNING
        logAgentActivity("AGENT_LEARNING: Recording step metrics and memory updates")

        val nextState = when {
            result.status == ActionResultStatus.SUCCESS -> {
                logAgentActivity("AGENT_COMPLETED: Step completed successfully")
                AutomationOverlayState.updateState(VisualizationActionState.SUCCESS, targetText = taskDescription, packageName = primaryObservation.packageName)
                AgentState.COMPLETED
            }
            result.status == ActionResultStatus.BLOCKED -> {
                logAgentActivity("AGENT_PAUSED: Execution blocked (${result.reason})")
                AutomationOverlayState.updateState(VisualizationActionState.FAILED, targetText = taskDescription, packageName = primaryObservation.packageName)
                AgentState.PAUSED
            }
            else -> {
                _agentState.value = AgentState.RECOVERING
                AutomationOverlayState.updateState(VisualizationActionState.RECOVERING, targetText = taskDescription, packageName = primaryObservation.packageName)
                val recoveryOutcome = recoveryManager.evaluateRecovery(1, result, _currentTaskRecord.value)
                logAgentActivity("AGENT_RECOVERING: Failure recovery outcome = $recoveryOutcome")
                when (recoveryOutcome) {
                    RecoveryOutcome.RETRY -> AgentState.EXECUTING
                    RecoveryOutcome.PAUSE -> AgentState.PAUSED
                    else -> AgentState.FAILED
                }
            }
        }

        _agentState.value = nextState
        return AgentStepResult(
            stateBefore = AgentState.IDLE,
            observation = primaryObservation,
            decisionReason = result.message,
            actionExecuted = null,
            actionResult = result,
            verificationStatus = verificationStatus,
            nextState = nextState
        )
    }

}
