package com.creator.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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

class AutomationAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutomationAccService"

        private val _isServiceEnabled = MutableStateFlow(false)
        val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

        private val _activePackageName = MutableStateFlow<String?>("")
        val activePackageName: StateFlow<String?> = _activePackageName.asStateFlow()

        private val _lastAccessibilityEvent = MutableStateFlow<String>("None")
        val lastAccessibilityEvent: StateFlow<String> = _lastAccessibilityEvent.asStateFlow()

        private val _currentLearningMode = MutableStateFlow(LearningMode.IDLE)
        val currentLearningMode: StateFlow<LearningMode> = _currentLearningMode.asStateFlow()

        fun setLearningMode(mode: LearningMode) {
            _currentLearningMode.value = mode
            Log.i(TAG, "LEARNING_MODE_CHANGED: $mode")
        }

        var instance: AutomationAccessibilityService? = null
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceEnabled.value = true
        Log.i(TAG, "AutomationAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventTypeName = AccessibilityEvent.eventTypeToString(event.eventType)
        val pkg = event.packageName?.toString()

        _lastAccessibilityEvent.value = "$eventTypeName ($pkg)"

        if (!pkg.isNullOrBlank() && pkg != "com.creator.automation") {
            _activePackageName.value = pkg
            Log.d(TAG, "PACKAGE_CHANGED: $pkg (event: $eventTypeName)")
        }

        // Capture user interactions when in TRAINING mode
        if (_currentLearningMode.value == LearningMode.TRAINING && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val clickedText = event.text.firstOrNull()?.toString()?.trim()
                ?: event.contentDescription?.toString()?.trim()

            if (!clickedText.isNullOrBlank() && !pkg.isNullOrBlank()) {
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
        if (instance == this) {
            instance = null
        }
        _isServiceEnabled.value = false
        Log.i(TAG, "AutomationAccessibilityService destroyed")
    }

    /**
     * Retrieves the current root AccessibilityNodeInfo.
     */
    fun getRootNode(): AccessibilityNodeInfo? {
        return rootInActiveWindow
    }

    /**
     * Performs a global back action.
     */
    fun performGoBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
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
