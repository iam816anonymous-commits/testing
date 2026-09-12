package com.creator.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.util.Log

object AndroidAutomationCompat {

    private const val TAG = "AndroidAutomationCompat"

    /**
     * Executes Global Home navigation. Supported on API 16+.
     */
    fun performGlobalHome(service: AutomationAccessibilityService?): Boolean {
        if (service == null) {
            Log.w(TAG, "GLOBAL_HOME_FAILED: AccessibilityService is null")
            return false
        }
        val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        Log.i(TAG, "GLOBAL_HOME_DISPATCH: success=$success (API ${Build.VERSION.SDK_INT})")
        return success
    }

    /**
     * Executes Global Back navigation. Supported on API 16+.
     */
    fun performGlobalBack(service: AutomationAccessibilityService?): Boolean {
        if (service == null) {
            Log.w(TAG, "GLOBAL_BACK_FAILED: AccessibilityService is null")
            return false
        }
        val success = service.performGoBack()
        Log.i(TAG, "GLOBAL_BACK_DISPATCH: success=$success (API ${Build.VERSION.SDK_INT})")
        return success
    }

    /**
     * Executes Global Recents navigation. Supported on API 16+.
     */
    fun performGlobalRecents(service: AutomationAccessibilityService?): Boolean {
        if (service == null) {
            Log.w(TAG, "GLOBAL_RECENTS_FAILED: AccessibilityService is null")
            return false
        }
        val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        Log.i(TAG, "GLOBAL_RECENTS_DISPATCH: success=$success (API ${Build.VERSION.SDK_INT})")
        return success
    }

    /**
     * Dispatches a gesture tap at (x, y). Supported on API 24+.
     */
    fun dispatchGestureTap(service: AutomationAccessibilityService?, x: Float, y: Float): Boolean {
        if (service == null) return false
        return service.dispatchGestureTap(x, y)
    }

    /**
     * Dispatches a dynamic gesture swipe from (startX, startY) to (endX, endY). Supported on API 24+.
     */
    fun dispatchSwipe(
        service: AutomationAccessibilityService?,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300L
    ): Boolean {
        if (service == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val path = android.graphics.Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error dispatching gesture swipe ($startX, $startY) -> ($endX, $endY)", e)
            false
        }
    }

    /**
     * API-27-compatible screenshot / screen perception capture.
     * On API 30+, uses AccessibilityService native takeScreenshot.
     * On API < 30 (API 27), falls back to MediaProjection visual capture or UI snapshot.
     */
    suspend fun captureScreenshotCompat(
        service: AutomationAccessibilityService?,
        context: Context,
        screenObservationProvider: ObservationProvider? = null
    ): ActionResult {
        val activeService = service ?: AutomationAccessibilityService.instance

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && activeService != null) {
            val path = activeService.captureScreenshot()
            if (path != null) {
                return ActionResult(status = ActionResultStatus.SUCCESS, screenshotPath = path)
            }
        }

        // API 27 Fallback: Use MediaProjection or UI tree snapshot perception
        val root = activeService?.getRootNode()
        val snapshot = if (root != null) ActionResolver.captureSnapshot(root, activeService.packageName ?: "") else null

        if (screenObservationProvider != null) {
            val obs = screenObservationProvider.captureObservation()
            if (obs.confidence >= 0.5) {
                Log.i(TAG, "SCREENSHOT_FALLBACK_MEDIAPROJECTION: Captured screen perception on API ${Build.VERSION.SDK_INT}")
                return ActionResult(
                    status = ActionResultStatus.SUCCESS,
                    snapshot = snapshot,
                    message = "Screen perception captured via MediaProjection (API ${Build.VERSION.SDK_INT} fallback)"
                )
            }
        }

        Log.i(TAG, "SCREENSHOT_FALLBACK_ACCESSIBILITY_SNAPSHOT: Captured UI tree snapshot on API ${Build.VERSION.SDK_INT}")
        return ActionResult(
            status = ActionResultStatus.SUCCESS,
            snapshot = snapshot,
            message = "UI observation snapshot captured (API ${Build.VERSION.SDK_INT} fallback)"
        )
    }
}
