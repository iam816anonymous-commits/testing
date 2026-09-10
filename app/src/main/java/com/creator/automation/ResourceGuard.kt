package com.creator.automation

import android.content.Context
import android.util.Log

enum class ResourceMode {
    NORMAL,
    REDUCED_RESOURCE_MODE,
    EMERGENCY_CLEANUP
}

data class ResourceMetrics(
    val usedHeapMb: Long,
    val maxHeapMb: Long,
    val heapUsagePercent: Double,
    val mode: ResourceMode
)

class ResourceGuard(private val context: Context) {

    companion object {
        private const val TAG = "ResourceGuard"
        private const val REDUCED_THRESHOLD = 0.80 // 80% heap usage
        private const val EMERGENCY_THRESHOLD = 0.90 // 90% heap usage
    }

    fun checkResourceState(): ResourceMetrics {
        val runtime = Runtime.getRuntime()
        val totalHeap = runtime.totalMemory()
        val freeHeap = runtime.freeMemory()
        val usedHeapBytes = totalHeap - freeHeap
        val maxHeapBytes = runtime.maxMemory()

        val usedMb = usedHeapBytes / (1024 * 1024)
        val maxMb = maxHeapBytes / (1024 * 1024)
        val usageRatio = usedHeapBytes.toDouble() / maxHeapBytes.toDouble()

        val mode = when {
            usageRatio >= EMERGENCY_THRESHOLD -> {
                Log.w(TAG, "RESOURCE_CRITICAL: Heap usage at ${"%.1f".format(usageRatio * 100)}% ($usedMb MB / $maxMb MB). Executing EMERGENCY_CLEANUP.")
                executeEmergencyCleanup()
                ResourceMode.EMERGENCY_CLEANUP
            }
            usageRatio >= REDUCED_THRESHOLD -> {
                Log.w(TAG, "RESOURCE_PRESSURE: Heap usage at ${"%.1f".format(usageRatio * 100)}% ($usedMb MB / $maxMb MB). Switching to REDUCED_RESOURCE_MODE.")
                ResourceMode.REDUCED_RESOURCE_MODE
            }
            else -> ResourceMode.NORMAL
        }

        return ResourceMetrics(
            usedHeapMb = usedMb,
            maxHeapMb = maxMb,
            heapUsagePercent = usageRatio * 100,
            mode = mode
        )
    }

    private fun executeEmergencyCleanup() {
        try {
            // 1. Force GC to reclaim short-lived accessibility node references
            System.gc()

            // 2. Clear temporary screenshot cache files
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("screenshot_") || file.name.endsWith(".tmp")) {
                    file.delete()
                }
            }

            // 3. Disable background perception tasks if running
            CameraObservationProvider.disableCameraPerception()
            ScreenObservationProvider.stopProjectionSession()

            Log.i(TAG, "EMERGENCY_CLEANUP_COMPLETED: Cleared screenshot cache and halted background perception providers")
        } catch (e: Exception) {
            Log.e(TAG, "Error executing emergency resource cleanup", e)
        }
    }
}
