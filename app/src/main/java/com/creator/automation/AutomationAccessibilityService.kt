package com.creator.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class AccessibilityDiagnosticState(
    val serviceCreated: Boolean = false,
    val serviceConnected: Boolean = false,
    val connectedTimestamp: Long = 0L,
    val lastEventTimestamp: Long = 0L,
    val lastEventType: String = "None",
    val eventCount: Long = 0L,
    val activePackage: String = "unknown",
    val rootAvailable: Boolean = false,
    val rootNodeClass: String = "unknown",
    val rootNodeChildCount: Int = 0,
    val observationTimestamp: Long = 0L,
    val serviceDisconnected: Boolean = false,
    val disconnectTimestamp: Long = 0L
)

data class CrossAppObservationState(
    val observationActive: Boolean = false,
    val foregroundPackage: String = "unknown",
    val foregroundClass: String = "unknown",
    val rootAvailable: Boolean = false,
    val nodeCount: Int = 0,
    val textNodeCount: Int = 0,
    val clickableCount: Int = 0,
    val editableCount: Int = 0,
    val scrollableCount: Int = 0,
    val focusedCount: Int = 0,
    val observationTimestamp: Long = 0L,
    val eventCount: Long = 0L,
    val lastEventType: String = "None"
)

class AutomationAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutomationAccService"
        private const val AGENT_PACKAGE_NAME = "com.creator.automation"
        private const val OVERLAY_UPDATE_THROTTLE_MS = 300L

        private val _isServiceEnabled = MutableStateFlow(false)
        val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

        private val _activePackageName = MutableStateFlow<String?>("")
        val activePackageName: StateFlow<String?> = _activePackageName.asStateFlow()

        private val _lastAccessibilityEvent = MutableStateFlow<String>("None")
        val lastAccessibilityEvent: StateFlow<String> = _lastAccessibilityEvent.asStateFlow()

        private val _lastEventTimestamp = MutableStateFlow<Long>(0L)
        val lastEventTimestamp: StateFlow<Long> = _lastEventTimestamp.asStateFlow()

        private val _diagnosticState = MutableStateFlow(AccessibilityDiagnosticState())
        val diagnosticState: StateFlow<AccessibilityDiagnosticState> = _diagnosticState.asStateFlow()

        private val _crossAppObservationState = MutableStateFlow(CrossAppObservationState())
        val crossAppObservationState: StateFlow<CrossAppObservationState> = _crossAppObservationState.asStateFlow()

        private val _currentLearningMode = MutableStateFlow(LearningMode.IDLE)
        val currentLearningMode: StateFlow<LearningMode> = _currentLearningMode.asStateFlow()

        fun setLearningMode(mode: LearningMode) {
            _currentLearningMode.value = mode
            Log.i(TAG, "LEARNING_MODE_CHANGED: $mode")
        }

        var instance: AutomationAccessibilityService? = null
            private set

        fun resetDiagnosticsForTesting() {
            _diagnosticState.value = AccessibilityDiagnosticState()
            _crossAppObservationState.value = CrossAppObservationState()
            _isServiceEnabled.value = false
            _activePackageName.value = ""
            _lastAccessibilityEvent.value = "None"
            _lastEventTimestamp.value = 0L
            instance = null
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var overlayTextView: TextView? = null
    private var lastOverlayUpdateTimestamp: Long = 0L

    override fun onCreate() {
        super.onCreate()
        _diagnosticState.value = _diagnosticState.value.copy(serviceCreated = true)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceEnabled.value = true
        val now = System.currentTimeMillis()
        _diagnosticState.value = _diagnosticState.value.copy(
            serviceCreated = true,
            serviceConnected = true,
            connectedTimestamp = now,
            serviceDisconnected = false
        )
        Log.i(TAG, "AutomationAccessibilityService connected")
    }

    fun setCrossAppObservationActive(active: Boolean) {
        val current = _crossAppObservationState.value
        if (current.observationActive == active) return

        _crossAppObservationState.value = current.copy(observationActive = active)

        mainHandler.post {
            if (active) {
                showDiagnosticOverlay()
            } else {
                hideDiagnosticOverlay()
            }
        }
    }

    private fun showDiagnosticOverlay() {
        if (overlayView != null) return
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (windowManager == null) return

            val layoutParams = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                format = PixelFormat.TRANSLUCENT
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.TOP or Gravity.START
                x = 20
                y = 50
            }

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.argb(230, 15, 23, 42))
                setPadding(16, 12, 16, 12)
            }

            val tv = TextView(this).apply {
                setTextColor(Color.GREEN)
                textSize = 9f
                text = "CREATOR AGENT DIAGNOSTIC\nLayer 1: ACTIVE\nLayer 2: ACTIVE\nLayer 3: ACTIVE\nLayer 4-9: LOCKED\nApp: unknown"
            }

            container.addView(tv)
            overlayView = container
            overlayTextView = tv

            windowManager?.addView(overlayView, layoutParams)
            Log.i(TAG, "DIAGNOSTIC_OVERLAY_SHOW")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing diagnostic overlay", e)
        }
    }

    private fun hideDiagnosticOverlay() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager?.removeView(overlayView)
                Log.i(TAG, "DIAGNOSTIC_OVERLAY_HIDE")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing diagnostic overlay", e)
            } finally {
                overlayView = null
                overlayTextView = null
            }
        }
    }

    private fun updateDiagnosticOverlayText(state: CrossAppObservationState) {
        if (overlayTextView == null) return
        val now = System.currentTimeMillis()
        if (now - lastOverlayUpdateTimestamp < OVERLAY_UPDATE_THROTTLE_MS) {
            return
        }
        lastOverlayUpdateTimestamp = now

        mainHandler.post {
            overlayTextView?.text = "CREATOR AGENT DIAGNOSTIC\n" +
                    "App: ${state.foregroundPackage}\n" +
                    "Root: ${if (state.rootAvailable) "AVAILABLE" else "UNAVAILABLE"}\n" +
                    "Nodes: ${state.nodeCount} | Text: ${state.textNodeCount}\n" +
                    "Click: ${state.clickableCount} | Edit: ${state.editableCount}\n" +
                    "Scroll: ${state.scrollableCount} | Focus: ${state.focusedCount}\n" +
                    "Layer: 1-3 ACTIVE | 4-9 LOCKED\n" +
                    "Action Dispatched: FALSE"
        }
    }

    /**
     * Resolves the true currently visible foreground package name.
     * Evaluates live root, application windows, or last non-SystemUI event package.
     */
    fun resolveCurrentForegroundPackage(root: AccessibilityNodeInfo?): String {
        val rootPkg = root?.packageName?.toString()
        if (!rootPkg.isNullOrBlank() && rootPkg != "com.android.systemui" && rootPkg != AGENT_PACKAGE_NAME) {
            return rootPkg
        }

        // Search active application windows if root is null, SystemUI, or Agent Overlay
        try {
            val appWindow = windows?.firstOrNull {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                        it.root?.packageName != null &&
                        it.root?.packageName != "com.android.systemui" &&
                        it.root?.packageName != AGENT_PACKAGE_NAME
            }
            val appPkg = appWindow?.root?.packageName?.toString()
            if (!appPkg.isNullOrBlank()) {
                return appPkg
            }
        } catch (e: Exception) {
            // Window access might be restricted or unsupported on mock
        }

        if (!rootPkg.isNullOrBlank() && rootPkg != AGENT_PACKAGE_NAME) {
            return rootPkg
        }

        val lastPkg = _activePackageName.value
        if (!lastPkg.isNullOrBlank()) {
            return lastPkg
        }

        return "unknown"
    }

    /**
     * Obtains an explicit, fresh, structured UI snapshot of the currently visible screen on demand,
     * independent of whether a new accessibility event has arrived.
     */
    fun refreshCurrentScreenObservation(): UiSnapshot {
        val root = rootInActiveWindow
        val resolvedPkg = resolveCurrentForegroundPackage(root)
        val snapshot = ActionResolver.captureSnapshot(root, resolvedPkg)
        val now = System.currentTimeMillis()

        _diagnosticState.value = _diagnosticState.value.copy(
            activePackage = snapshot.packageName,
            rootAvailable = snapshot.isRootAvailable,
            rootNodeClass = root?.className?.toString() ?: "unknown",
            rootNodeChildCount = root?.childCount ?: 0,
            observationTimestamp = now
        )

        val crossApp = _crossAppObservationState.value
        if (crossApp.observationActive) {
            val updatedCrossApp = crossApp.copy(
                foregroundPackage = snapshot.packageName,
                rootAvailable = snapshot.isRootAvailable,
                nodeCount = snapshot.totalNodeCount,
                textNodeCount = snapshot.textNodeCount,
                clickableCount = snapshot.clickableNodeCount,
                editableCount = snapshot.editableNodeCount,
                scrollableCount = snapshot.scrollableNodeCount,
                focusedCount = snapshot.focusedNodeCount,
                observationTimestamp = now
            )
            _crossAppObservationState.value = updatedCrossApp
            updateDiagnosticOverlayText(updatedCrossApp)
        }

        Log.i(TAG, "REFRESH_SCREEN_OBSERVATION: pkg=${snapshot.packageName}, nodes=${snapshot.totalNodeCount}, root=${snapshot.isRootAvailable}")
        return snapshot
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventTypeName = AccessibilityEvent.eventTypeToString(event.eventType)
        val pkg = event.packageName?.toString() ?: "unknown"
        val now = System.currentTimeMillis()

        _lastEventTimestamp.value = now
        _lastAccessibilityEvent.value = "$eventTypeName ($pkg)"

        // Break overlay feedback loop: ignore events caused by agent overlay itself
        if (pkg == AGENT_PACKAGE_NAME) {
            return
        }

        if (pkg.isNotBlank() && pkg != "com.android.systemui" && pkg != "unknown") {
            _activePackageName.value = pkg
            Log.d(TAG, "PACKAGE_CHANGED: $pkg (event: $eventTypeName)")
        }

        val currentDiag = _diagnosticState.value
        val root = rootInActiveWindow
        val hasRoot = root != null
        val resolvedPkg = resolveCurrentForegroundPackage(root)

        _diagnosticState.value = currentDiag.copy(
            lastEventTimestamp = now,
            lastEventType = eventTypeName,
            eventCount = currentDiag.eventCount + 1,
            activePackage = resolvedPkg,
            rootAvailable = hasRoot,
            rootNodeClass = root?.className?.toString() ?: "unknown",
            rootNodeChildCount = root?.childCount ?: 0,
            observationTimestamp = if (hasRoot) now else currentDiag.observationTimestamp
        )

        val crossApp = _crossAppObservationState.value
        if (crossApp.observationActive) {
            val snapshot = ActionResolver.captureSnapshot(root, resolvedPkg)
            val updatedCrossApp = crossApp.copy(
                foregroundPackage = resolvedPkg,
                foregroundClass = event.className?.toString() ?: crossApp.foregroundClass,
                rootAvailable = snapshot.isRootAvailable,
                nodeCount = snapshot.totalNodeCount,
                textNodeCount = snapshot.textNodeCount,
                clickableCount = snapshot.clickableNodeCount,
                editableCount = snapshot.editableNodeCount,
                scrollableCount = snapshot.scrollableNodeCount,
                focusedCount = snapshot.focusedNodeCount,
                observationTimestamp = snapshot.timestamp,
                eventCount = crossApp.eventCount + 1,
                lastEventType = eventTypeName
            )
            _crossAppObservationState.value = updatedCrossApp
            updateDiagnosticOverlayText(updatedCrossApp)
        }

        // Capture user interactions when in TRAINING mode
        if (_currentLearningMode.value == LearningMode.TRAINING && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val clickedText = event.text.firstOrNull()?.toString()?.trim()
                ?: event.contentDescription?.toString()?.trim()

            if (!clickedText.isNullOrBlank() && pkg != "unknown") {
                Log.i(TAG, "USER_DEMONSTRATION_DETECTED: Package '$pkg', Clicked Text '$clickedText'")
                recordUserDemonstration(pkg, clickedText)
            }
        }
    }

    private fun recordUserDemonstration(packageName: String, clickedText: String) {
        serviceScope.launch {
            try {
                val root = rootInActiveWindow
                val snapshot = ActionResolver.captureSnapshot(root, packageName)
                val stateSig = StateSignatureGenerator.generateSignature(snapshot)

                val db = AppDatabase.getDatabase(applicationContext)
                val dao = db.demonstrationDao()

                val existingRecords = dao.getRecordsForState(packageName, stateSig)
                val existing = existingRecords.firstOrNull { it.targetText == clickedText }

                if (existing != null) {
                    val updated = existing.copy(
                        demonstrationCount = existing.demonstrationCount + 1,
                        timestamp = System.currentTimeMillis()
                    )
                    dao.insertRecord(updated)
                    Log.i(TAG, "DEMONSTRATION_UPDATED: Updated '${clickedText}' count to ${updated.demonstrationCount}")
                } else {
                    val hasConflict = existingRecords.any { it.targetText != clickedText }
                    val newRecord = DemonstrationRecord(
                        packageName = packageName,
                        stateSignature = stateSig,
                        actionType = ActionType.CLICK_TEXT.name,
                        targetText = clickedText,
                        confidenceLevel = ConfidenceLevel.CANDIDATE.name,
                        demonstrationCount = 1,
                        isAmbiguous = hasConflict
                    )
                    dao.insertRecord(newRecord)
                    Log.i(TAG, "DEMONSTRATION_RECORDED: Saved new transition for '${clickedText}' (Ambiguous: $hasConflict)")
                }

                // Delegate to TrainingSessionManager if an active session exists
                val sessionManager = TrainingSessionManager.instance
                    ?: TrainingSessionManager(db.learnedWorkflowDao()).also { TrainingSessionManager.instance }

                if (TrainingSessionManager.isTrainingActive.value) {
                    sessionManager.recordObservedAction(snapshot, ActionType.CLICK_TEXT.name, clickedText)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error recording user demonstration", e)
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AutomationAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        hideDiagnosticOverlay()
        if (instance == this) {
            instance = null
        }
        _isServiceEnabled.value = false
        val now = System.currentTimeMillis()
        _diagnosticState.value = _diagnosticState.value.copy(
            serviceConnected = false,
            serviceDisconnected = true,
            disconnectTimestamp = now
        )
        Log.i(TAG, "AutomationAccessibilityService destroyed")
    }

    /**
     * Diagnostic representation of actual AccessibilityService runtime state.
     */
    fun getDiagnosticsSummary(): String {
        val root = rootInActiveWindow
        val info = serviceInfo
        val state = _diagnosticState.value
        return "AccessibilityService:\n" +
                "serviceCreated = ${state.serviceCreated}\n" +
                "serviceConnected = ${state.serviceConnected}\n" +
                "connectedTimestamp = ${state.connectedTimestamp}\n" +
                "serviceInstance = ${this.javaClass.simpleName}\n" +
                "canRetrieveWindowContent = ${info?.canRetrieveWindowContent ?: true}\n" +
                "canPerformGestures = ${Build.VERSION.SDK_INT >= Build.VERSION_CODES.N}\n" +
                "rootAvailable = ${root != null}\n" +
                "rootNodeClass = ${root?.className ?: state.rootNodeClass}\n" +
                "rootNodeChildCount = ${root?.childCount ?: state.rootNodeChildCount}\n" +
                "activePackage = ${root?.packageName ?: _activePackageName.value ?: state.activePackage}\n" +
                "eventCount = ${state.eventCount}\n" +
                "lastEventType = ${state.lastEventType}\n" +
                "lastEventTimestamp = ${state.lastEventTimestamp}\n" +
                "serviceDisconnected = ${state.serviceDisconnected}\n" +
                "disconnectTimestamp = ${state.disconnectTimestamp}"
    }

    /**
     * Retrieves the current root AccessibilityNodeInfo.
     */
    fun getRootNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow
        val now = System.currentTimeMillis()
        if (root != null) {
            _diagnosticState.value = _diagnosticState.value.copy(
                rootAvailable = true,
                rootNodeClass = root.className?.toString() ?: "unknown",
                rootNodeChildCount = root.childCount,
                observationTimestamp = now
            )
        } else {
            _diagnosticState.value = _diagnosticState.value.copy(rootAvailable = false)
        }
        return root
    }

    /**
     * Performs a global back action.
     */
    fun performGoBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Generic gesture tap fallback dispatched to (x, y) bounds center.
     */
    fun dispatchGestureTap(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val path = android.graphics.Path().apply {
                moveTo(x, y)
            }
            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50)
            val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error dispatching gesture tap at ($x, $y)", e)
            false
        }
    }

    /**
     * Takes a screenshot of the current active screen and saves it locally.
     * Returns the absolute path of the saved screenshot or null if failed.
     */
    suspend fun captureScreenshot(): String? = suspendCoroutine { continuation ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                applicationContext.mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        try {
                            val bitmap = Bitmap.wrapHardwareBuffer(
                                screenshotResult.hardwareBuffer,
                                screenshotResult.colorSpace
                            )
                            if (bitmap == null) {
                                continuation.resume(null)
                                return
                            }
                            val file = File(
                                applicationContext.cacheDir,
                                "screenshot_${System.currentTimeMillis()}.png"
                            )
                            FileOutputStream(file).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                            }
                            screenshotResult.hardwareBuffer.close()
                            Log.i(TAG, "SCREENSHOT_CAPTURED: ${file.absolutePath}")
                            continuation.resume(file.absolutePath)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving screenshot", e)
                            continuation.resume(null)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "Screenshot failed with error code: $errorCode")
                        continuation.resume(null)
                    }
                }
            )
        } else {
            Log.w(TAG, "Screenshot API requires Android R (API 30)+")
            continuation.resume(null)
        }
    }
}
