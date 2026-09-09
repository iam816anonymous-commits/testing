package com.creator.automation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

class CameraObservationProvider(
    private val context: Context
) : ObservationProvider {

    override fun getSource(): ObservationSource = ObservationSource.CAMERA

    companion object {
        private const val TAG = "CameraObsProvider"

        private val _isPermissionGranted = MutableStateFlow(false)
        val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted.asStateFlow()

        private val _isCameraEnabled = MutableStateFlow(false)
        val isCameraEnabled: StateFlow<Boolean> = _isCameraEnabled.asStateFlow()

        private val _isCameraRunning = MutableStateFlow(false)
        val isCameraRunning: StateFlow<Boolean> = _isCameraRunning.asStateFlow()

        private val _lastCameraSignature = MutableStateFlow<String?>(null)
        val lastCameraSignature: StateFlow<String?> = _lastCameraSignature.asStateFlow()

        private val _lastCameraWidth = MutableStateFlow(0)
        val lastCameraWidth: StateFlow<Int> = _lastCameraWidth.asStateFlow()

        private val _lastCameraHeight = MutableStateFlow(0)
        val lastCameraHeight: StateFlow<Int> = _lastCameraHeight.asStateFlow()

        private val _lastCameraChangeState = MutableStateFlow("UNKNOWN")
        val lastCameraChangeState: StateFlow<String> = _lastCameraChangeState.asStateFlow()

        private val _frameCount = MutableStateFlow(0L)
        val frameCount: StateFlow<Long> = _frameCount.asStateFlow()

        private val _lastAnalysisDurationMs = MutableStateFlow(0L)
        val lastAnalysisDurationMs: StateFlow<Long> = _lastAnalysisDurationMs.asStateFlow()

        private val _averageAnalysisDurationMs = MutableStateFlow(0L)
        val averageAnalysisDurationMs: StateFlow<Long> = _averageAnalysisDurationMs.asStateFlow()

        private var cameraProvider: ProcessCameraProvider? = null
        private val cameraExecutor = Executors.newSingleThreadExecutor()

        fun updatePermissionStatus(context: Context) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            _isPermissionGranted.value = granted
        }

        fun enableCameraPerception(context: Context, lifecycleOwner: LifecycleOwner) {
            updatePermissionStatus(context)

            if (!_isPermissionGranted.value) {
                Log.w(TAG, "Cannot enable camera perception: CAMERA permission NOT_GRANTED")
                _isCameraEnabled.value = false
                _isCameraRunning.value = false
                return
            }

            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    try {
                        val provider = cameraProviderFuture.get()
                        cameraProvider = provider
                        provider.unbindAll()

                        val cameraSelector = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        } else if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            Log.e(TAG, "No suitable camera hardware available")
                            _isCameraRunning.value = false
                            return@addListener
                        }

                        @Suppress("DEPRECATION")
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setTargetResolution(Size(640, 480))
                            .build()

                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            processCameraFrame(imageProxy)
                        }

                        provider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)
                        _isCameraEnabled.value = true
                        _isCameraRunning.value = true
                        Log.i(TAG, "Camera perception pipeline successfully initialized and bound to lifecycle")
                    } catch (e: Throwable) {
                        Log.e(TAG, "Error binding camera perception pipeline: ${e.message}", e)
                        _isCameraRunning.value = false
                    }
                }, ContextCompat.getMainExecutor(context))
            } catch (e: Throwable) {
                Log.e(TAG, "Error initializing CameraX provider: ${e.message}", e)
                _isCameraRunning.value = false
            }
        }

        fun disableCameraPerception() {
            try {
                cameraProvider?.unbindAll()
                _isCameraEnabled.value = false
                _isCameraRunning.value = false
                Log.i(TAG, "Camera perception disabled and camera resources released")
            } catch (e: Throwable) {
                Log.e(TAG, "Error disabling camera perception: ${e.message}", e)
            }
        }

        private fun processCameraFrame(imageProxy: ImageProxy) {
            val startTime = System.currentTimeMillis()
            try {
                val analysis = VisualStateAnalyzer.analyzeCameraFrame(imageProxy)

                val duration = System.currentTimeMillis() - startTime
                val currentCount = _frameCount.value + 1
                val totalDuration = _averageAnalysisDurationMs.value * _frameCount.value + duration

                _lastCameraWidth.value = analysis.width
                _lastCameraHeight.value = analysis.height

                val prevSig = _lastCameraSignature.value
                val changeResult = VisualStateAnalyzer.compareSignatures(prevSig, analysis.visualSignature)

                _lastCameraSignature.value = analysis.visualSignature
                _lastCameraChangeState.value = changeResult.name

                _frameCount.value = currentCount
                _lastAnalysisDurationMs.value = duration
                _averageAnalysisDurationMs.value = if (currentCount > 0) totalDuration / currentCount else duration
            } catch (e: Throwable) {
                Log.e(TAG, "Error in processCameraFrame: ${e.message}", e)
            }
        }
    }

    override suspend fun captureObservation(): CurrentObservation {
        updatePermissionStatus(context)

        if (!_isPermissionGranted.value || !_isCameraEnabled.value || !_isCameraRunning.value) {
            val reason = when {
                !_isPermissionGranted.value -> "CAMERA permission NOT_GRANTED"
                !_isCameraEnabled.value -> "Camera perception disabled by user"
                else -> "Camera pipeline inactive or initializing"
            }

            return CurrentObservation(
                source = ObservationSource.CAMERA,
                packageName = "physical_world",
                stateSignature = "CAMERA_UNAVAILABLE",
                summary = "Camera observation unavailable: $reason",
                confidence = 0.0,
                visualSignature = null,
                width = 0,
                height = 0,
                visualChangeState = "UNKNOWN"
            )
        }

        val signature = _lastCameraSignature.value ?: "CAMERA_NO_FRAME"
        val changeState = _lastCameraChangeState.value

        return CurrentObservation(
            source = ObservationSource.CAMERA,
            packageName = "physical_world",
            stateSignature = signature,
            summary = "Camera frame (${_lastCameraWidth.value}x${_lastCameraHeight.value}), visualChange: $changeState, avgTime: ${_averageAnalysisDurationMs.value}ms",
            confidence = 1.0,
            visualSignature = signature,
            width = _lastCameraWidth.value,
            height = _lastCameraHeight.value,
            visualChangeState = changeState
        )
    }
}
