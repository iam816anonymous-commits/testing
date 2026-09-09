package com.creator.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AutomationAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutomationAccService"

        private val _isServiceEnabled = MutableStateFlow(false)
        val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

        private val _activePackageName = MutableStateFlow<String?>("")
        val activePackageName: StateFlow<String?> = _activePackageName.asStateFlow()

        var instance: AutomationAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceEnabled.value = true
        Log.i(TAG, "AutomationAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString()
        if (!pkg.isNullOrBlank() && pkg != "com.creator.automation") {
            _activePackageName.value = pkg
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
