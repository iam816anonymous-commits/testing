package com.creator.automation

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScreenObservationProvider(
    private val context: Context
) : ObservationProvider {

    override fun getSource(): ObservationSource = ObservationSource.SCREEN

    companion object {
        private const val TAG = "ScreenObsProvider"

        private val _isAuthorized = MutableStateFlow(false)
        val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

        private val _lastVisualSignature = MutableStateFlow<String?>(null)
        val lastVisualSignature: StateFlow<String?> = _lastVisualSignature.asStateFlow()

        private val _lastFrameWidth = MutableStateFlow(0)
        val lastFrameWidth: StateFlow<Int> = _lastFrameWidth.asStateFlow()

        private val _lastFrameHeight = MutableStateFlow(0)
        val lastFrameHeight: StateFlow<Int> = _lastFrameHeight.asStateFlow()

        private val _lastVisualChangeState = MutableStateFlow("UNKNOWN")
        val lastVisualChangeState: StateFlow<String> = _lastVisualChangeState.asStateFlow()

        private var mediaProjection: MediaProjection? = null
        private var virtualDisplay: VirtualDisplay? = null
        private var imageReader: ImageReader? = null

        fun setScreenCaptureAuthorization(
            context: Context,
            resultCode: Int,
            data: Intent
        ) {
            try {
                stopProjectionSession()

                val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                if (projectionManager == null) {
                    Log.e(TAG, "MediaProjectionManager service unavailable")
                    _isAuthorized.value = false
                    return
                }

                val mp = projectionManager.getMediaProjection(resultCode, data)
                if (mp == null) {
                    Log.e(TAG, "Failed to obtain MediaProjection instance")
                    _isAuthorized.value = false
                    return
                }

                mediaProjection = mp
                mp.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        Log.w(TAG, "MediaProjection session stopped by system or user")
                        stopProjectionSession()
                    }
                }, Handler(Looper.getMainLooper()))

                setupVirtualDisplay(context, mp)
                _isAuthorized.value = true
                Log.i(TAG, "Screen capture MediaProjection session authorized and active")
            } catch (e: Throwable) {
                Log.e(TAG, "Error initializing MediaProjection session: ${e.message}", e)
                stopProjectionSession()
            }
        }

        private fun setupVirtualDisplay(context: Context, mp: MediaProjection) {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val metrics = DisplayMetrics()
            windowManager?.defaultDisplay?.getRealMetrics(metrics)

            val screenWidth = if (metrics.widthPixels > 0) metrics.widthPixels else 720
            val screenHeight = if (metrics.heightPixels > 0) metrics.heightPixels else 1280
            val densityDpi = if (metrics.densityDpi > 0) metrics.densityDpi else DisplayMetrics.DENSITY_DEFAULT

            // Downsample dimensions for low RAM API 27 hardware
            val captureWidth = screenWidth / 2
            val captureHeight = screenHeight / 2

            val reader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
            imageReader = reader

            virtualDisplay = mp.createVirtualDisplay(
                "CreatorAutomation_ScreenCapture",
                captureWidth,
                captureHeight,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                Handler(Looper.getMainLooper())
            )

            _lastFrameWidth.value = captureWidth
            _lastFrameHeight.value = captureHeight
        }

        fun stopProjectionSession() {
            try {
                virtualDisplay?.release()
                virtualDisplay = null

                imageReader?.close()
                imageReader = null

                mediaProjection?.stop()
                mediaProjection = null

                _isAuthorized.value = false
                Log.i(TAG, "MediaProjection session stopped and resources released")
            } catch (e: Throwable) {
                Log.e(TAG, "Error stopping MediaProjection session: ${e.message}", e)
            }
        }
    }

    override suspend fun captureObservation(): CurrentObservation {
        val activePackage = AutomationAccessibilityService.activePackageName.value ?: "unknown"

        if (!_isAuthorized.value || imageReader == null) {
            Log.w(TAG, "Screen observation requested but screen capture is NOT_GRANTED or session inactive")
            return CurrentObservation(
                source = ObservationSource.SCREEN,
                packageName = activePackage,
                stateSignature = "SCREEN_UNAVAILABLE",
                summary = "Screen capture permission NOT_GRANTED or session inactive",
                confidence = 0.0,
                visualSignature = null,
                width = 0,
                height = 0,
                visualChangeState = "UNKNOWN"
            )
        }

        var acquiredImage: Image? = null
        var frameBitmap: Bitmap? = null

        return try {
            val reader = imageReader
            acquiredImage = reader?.acquireLatestImage()

            if (acquiredImage == null) {
                Log.w(TAG, "No image frame acquired from ImageReader")
                CurrentObservation(
                    source = ObservationSource.SCREEN,
                    packageName = activePackage,
                    stateSignature = _lastVisualSignature.value ?: "SCREEN_NO_FRAME",
                    summary = "No frame available from ImageReader",
                    confidence = 0.5,
                    visualSignature = _lastVisualSignature.value,
                    width = _lastFrameWidth.value,
                    height = _lastFrameHeight.value,
                    visualChangeState = "UNKNOWN"
                )
            } else {
                val planes = acquiredImage.planes
                if (planes.isEmpty()) {
                    CurrentObservation(
                        source = ObservationSource.SCREEN,
                        packageName = activePackage,
                        stateSignature = "SCREEN_EMPTY_PLANES",
                        summary = "Acquired image frame has empty planes",
                        confidence = 0.0
                    )
                } else {
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * acquiredImage.width

                    val bitmap = Bitmap.createBitmap(
                        acquiredImage.width + rowPadding / pixelStride,
                        acquiredImage.height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    frameBitmap = bitmap

                    val analysis = VisualStateAnalyzer.analyzeFrame(bitmap)
                    val currentSignature = analysis?.visualSignature ?: "V_UNKNOWN"

                    val prevSignature = _lastVisualSignature.value
                    val changeResult = VisualStateAnalyzer.compareSignatures(prevSignature, currentSignature)

                    _lastVisualSignature.value = currentSignature
                    _lastVisualChangeState.value = changeResult.name

                    CurrentObservation(
                        source = ObservationSource.SCREEN,
                        packageName = activePackage,
                        stateSignature = currentSignature,
                        summary = "Screen frame (${analysis?.width}x${analysis?.height}), visualChange: ${changeResult.name}",
                        confidence = 1.0,
                        visualSignature = currentSignature,
                        width = analysis?.width ?: 0,
                        height = analysis?.height ?: 0,
                        visualChangeState = changeResult.name
                    )
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error capturing screen frame observation: ${e.message}", e)
            CurrentObservation(
                source = ObservationSource.SCREEN,
                packageName = activePackage,
                stateSignature = "SCREEN_CAPTURE_ERROR",
                summary = "Error capturing screen frame: ${e.message}",
                confidence = 0.0,
                visualChangeState = "UNKNOWN"
            )
        } finally {
            // Instant resource cleanup
            try {
                acquiredImage?.close()
            } catch (e: Throwable) {
                // Ignore
            }

            try {
                if (frameBitmap != null && !frameBitmap.isRecycled) {
                    frameBitmap.recycle()
                }
            } catch (e: Throwable) {
                // Ignore
            }
        }
    }
}
