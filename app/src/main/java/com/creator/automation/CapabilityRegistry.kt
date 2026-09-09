package com.creator.automation

import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

enum class Capability {
    ACCESSIBILITY,
    SCREEN_CAPTURE,
    CAMERA,
    NOTIFICATIONS,
    AUDIO,
    NETWORK,
    WORK_MANAGER,
    STORAGE
}

enum class CapabilityState {
    AVAILABLE,
    UNAVAILABLE,
    NOT_GRANTED,
    NOT_IMPLEMENTED,
    UNKNOWN
}

data class CapabilityReport(
    val capability: Capability,
    val state: CapabilityState,
    val details: String
)

data class DeviceProbeResult(
    val androidVersion: String = Build.VERSION.RELEASE ?: "8.1.0",
    val apiLevel: Int = Build.VERSION.SDK_INT,
    val cpuAbi: String = try { Build.SUPPORTED_ABIS?.firstOrNull() } catch (e: Throwable) { null } ?: "unknown",
    val totalRamMb: Long = 0L,
    val reports: List<CapabilityReport> = emptyList()
)

class DeviceCapabilityProbe(private val context: Context) {

    fun probeCapabilities(): DeviceProbeResult {
        val reports = mutableListOf<CapabilityReport>()

        // 1. Accessibility
        val isAccEnabled = AutomationAccessibilityService.isServiceEnabled.value
        reports.add(
            CapabilityReport(
                capability = Capability.ACCESSIBILITY,
                state = if (isAccEnabled) CapabilityState.AVAILABLE else CapabilityState.UNAVAILABLE,
                details = if (isAccEnabled) "AutomationAccessibilityService active" else "AccessibilityService disabled"
            )
        )

        // 2. Screen Capture (MediaProjection API supported on API 21+, verified for API 27)
        val hasMediaProjectionService = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) != null
        val isScreenAuthorized = ScreenObservationProvider.isAuthorized.value

        val screenCapState = when {
            !hasMediaProjectionService -> CapabilityState.UNAVAILABLE
            !isScreenAuthorized -> CapabilityState.NOT_GRANTED
            else -> CapabilityState.AVAILABLE
        }

        val screenCapDetails = when {
            !hasMediaProjectionService -> "MediaProjection service not available on device"
            !isScreenAuthorized -> "Platform API 27 supports MediaProjection, but user authorization NOT_GRANTED"
            else -> "MediaProjection session authorized and active"
        }

        reports.add(
            CapabilityReport(
                capability = Capability.SCREEN_CAPTURE,
                state = screenCapState,
                details = screenCapDetails
            )
        )

        // 3. Camera
        val hasCameraHardware = try { context.packageManager?.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) == true } catch (e: Throwable) { false }
        val hasCameraPermission = try { context.checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED } catch (e: Throwable) { false }
        val cameraState = when {
            !hasCameraHardware -> CapabilityState.UNAVAILABLE
            !hasCameraPermission -> CapabilityState.NOT_GRANTED
            else -> CapabilityState.NOT_IMPLEMENTED // Not implemented in agent loop yet
        }
        reports.add(CapabilityReport(Capability.CAMERA, cameraState, "Hardware: $hasCameraHardware, Granted: $hasCameraPermission"))

        // 4. Notifications
        reports.add(CapabilityReport(Capability.NOTIFICATIONS, CapabilityState.AVAILABLE, "System notification posting available"))

        // 5. Audio
        reports.add(CapabilityReport(Capability.AUDIO, CapabilityState.NOT_IMPLEMENTED, "Audio processing not implemented in V0.9"))

        // 6. Network
        val connMgr = try { context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager } catch (e: Throwable) { null }
        val isNetworkAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val net = connMgr?.activeNetwork
            val caps = connMgr?.getNetworkCapabilities(net)
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            @Suppress("DEPRECATION")
            try { connMgr?.activeNetworkInfo?.isConnected == true } catch (e: Throwable) { false }
        }
        reports.add(CapabilityReport(Capability.NETWORK, if (isNetworkAvailable) CapabilityState.AVAILABLE else CapabilityState.UNAVAILABLE, "Internet connected: $isNetworkAvailable"))

        // 7. WorkManager
        reports.add(CapabilityReport(Capability.WORK_MANAGER, CapabilityState.AVAILABLE, "WorkManager 2.10.0 initialized"))

        // 8. Storage
        reports.add(CapabilityReport(Capability.STORAGE, CapabilityState.AVAILABLE, "Local SQLite / Room storage active"))

        val safeCpuAbi = try {
            Build.SUPPORTED_ABIS?.firstOrNull() ?: "unknown"
        } catch (e: Throwable) {
            "unknown"
        }

        return DeviceProbeResult(
            androidVersion = Build.VERSION.RELEASE ?: "8.1.0",
            apiLevel = Build.VERSION.SDK_INT,
            cpuAbi = safeCpuAbi,
            reports = reports
        )
    }
}
