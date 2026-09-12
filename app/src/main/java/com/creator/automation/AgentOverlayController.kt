package com.creator.automation

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class AgentOverlayController(private val context: Context) {

    companion object {
        private const val TAG = "AgentOverlayController"
        @Volatile var instance: AgentOverlayController? = null
            private set
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private var overlayView: LinearLayout? = null
    private var cursorView: View? = null
    private var statusTextView: TextView? = null
    private var stopButton: Button? = null
    private var isOverlayShowing = false
    private var isCursorShowing = false

    init {
        instance = this
    }

    fun showOverlay() {
        if (isOverlayShowing || windowManager == null) return

        try {
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(24, 16, 24, 16)
                gravity = Gravity.CENTER_VERTICAL

                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#CC121212")) // Dark translucent background
                    cornerRadius = 32f
                    setStroke(2, Color.parseColor("#443388FF"))
                }
                background = bg
            }

            // Agent Status Icon + Text
            val statusText = TextView(context).apply {
                text = "● Agent Working..."
                setTextColor(Color.parseColor("#00E676"))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 16, 0)
            }
            statusTextView = statusText
            root.addView(statusText)

            // Stop Button
            val stopBtn = Button(context).apply {
                text = "STOP"
                setTextColor(Color.WHITE)
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(12, 4, 12, 4)

                val btnBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#D32F2F"))
                    cornerRadius = 16f
                }
                background = btnBg

                setOnClickListener {
                    Log.i(TAG, "OVERLAY_STOP_CLICKED: User requested task cancellation from overlay")
                    AgentCore.cancelAgent()
                    hideOverlay()
                }
            }
            stopButton = stopBtn
            root.addView(stopBtn)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 80 // Offset below status bar
            }

            windowManager.addView(root, params)
            overlayView = root
            isOverlayShowing = true

            // Create target click cursor highlight overlay
            setupCursorView()

            Log.i(TAG, "AGENT_OVERLAY_SHOWING: TYPE_ACCESSIBILITY_OVERLAY attached successfully")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to attach TYPE_ACCESSIBILITY_OVERLAY: ${e.message}", e)
        }
    }

    private fun setupCursorView() {
        if (windowManager == null || cursorView != null) return
        try {
            val cursor = View(context).apply {
                val circle = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#663388FF")) // Semi-transparent blue
                    setStroke(4, Color.parseColor("#FF00E676")) // Bright green border
                }
                background = circle
            }

            val cursorParams = WindowManager.LayoutParams(
                72, // 72px diameter ring
                72,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }

            cursor.visibility = View.GONE
            windowManager.addView(cursor, cursorParams)
            cursorView = cursor
            isCursorShowing = false
        } catch (e: Throwable) {
            Log.w(TAG, "Could not setup cursor overlay view: ${e.message}")
        }
    }

    fun updateOverlayState(state: AutomationVisualizationState) {
        if (!isOverlayShowing) {
            showOverlay()
        }

        val actionMsg = when (state.actionState) {
            VisualizationActionState.OBSERVING -> "Looking at screen..."
            VisualizationActionState.TARGET_FOUND -> "Target found: ${state.targetText ?: state.targetViewId ?: "UI element"}"
            VisualizationActionState.CLICKING -> "Clicking ${state.targetText ?: "target"}..."
            VisualizationActionState.TYPING -> "Typing text..."
            VisualizationActionState.WAITING -> "Waiting for screen..."
            VisualizationActionState.VERIFYING -> "Verifying result..."
            VisualizationActionState.SUCCESS -> "Action verified!"
            VisualizationActionState.FAILED -> "Action failed"
            VisualizationActionState.RECOVERING -> "Recovering..."
        }

        statusTextView?.text = "● $actionMsg"

        // Position target cursor highlight at actual target bounds center (cursorX, cursorY)
        val cx = state.cursorX
        val cy = state.cursorY
        val cursor = cursorView
        if (cursor != null && windowManager != null && cx != null && cy != null && cx > 0 && cy > 0) {
            try {
                val params = cursor.layoutParams as? WindowManager.LayoutParams
                if (params != null) {
                    params.x = cx - 36
                    params.y = cy - 36
                    windowManager.updateViewLayout(cursor, params)
                    cursor.visibility = View.VISIBLE
                    isCursorShowing = true
                    Log.i(TAG, "CURSOR_OVERLAY_MOVED: Target centered at ($cx, $cy)")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed updating cursor layout: ${e.message}")
            }
        } else if (cursor != null) {
            cursor.visibility = View.GONE
            isCursorShowing = false
        }
    }

    fun hideOverlay() {
        if (windowManager == null) return
        try {
            if (cursorView != null) {
                windowManager.removeView(cursorView)
                cursorView = null
                isCursorShowing = false
            }
            if (overlayView != null) {
                windowManager.removeView(overlayView)
                overlayView = null
                statusTextView = null
                stopButton = null
                isOverlayShowing = false
            }
            Log.i(TAG, "AGENT_OVERLAY_HIDDEN")
        } catch (e: Throwable) {
            Log.e(TAG, "Error removing overlay view: ${e.message}", e)
        }
    }
}
