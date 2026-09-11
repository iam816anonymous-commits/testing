package com.creator.automation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.Vibrator
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Physical capability state classification without state collapsing.
 */
enum class PhysicalCapabilityState {
    PRESENT,
    AVAILABLE,
    PERMISSION_GRANTED,
    USABLE,
    UNSUPPORTED,
    BLOCKED,
    UNKNOWN
}

enum class SensorCategory {
    MOTION,
    POSITION,
    ENVIRONMENT,
    PROXIMITY,
    ORIENTATION,
    OTHER
}

data class DiscoveredSensor(
    val type: Int,
    val name: String,
    val vendor: String,
    val version: Int,
    val power: Float,
    val resolution: Float,
    val maximumRange: Float,
    val minimumDelay: Int,
    val category: SensorCategory,
    val availability: PhysicalCapabilityState
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        put("name", name)
        put("vendor", vendor)
        put("version", version)
        put("power", power.toDouble())
        put("resolution", resolution.toDouble())
        put("maximumRange", maximumRange.toDouble())
        put("minimumDelay", minimumDelay)
        put("category", category.name)
        put("availability", availability.name)
    }
}

data class DiscoveredActuator(
    val name: String,
    val category: String,
    val isPresent: Boolean,
    val isObservable: Boolean,
    val isControllable: Boolean,
    val isVerifiable: Boolean,
    val permissionRequired: String?,
    val isCurrentlyAllowed: Boolean,
    val availabilityState: PhysicalCapabilityState,
    val details: Map<String, Any> = emptyMap()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("category", category)
        put("isPresent", isPresent)
        put("isObservable", isObservable)
        put("isControllable", isControllable)
        put("isVerifiable", isVerifiable)
        put("permissionRequired", permissionRequired ?: JSONObject.NULL)
        put("isCurrentlyAllowed", isCurrentlyAllowed)
        put("availabilityState", availabilityState.name)
        put("details", JSONObject(details.mapValues { it.value.toString() }))
    }
}

data class PermissionState(
    val permission: String,
    val isDeclared: Boolean,
    val isGranted: Boolean,
    val isRequired: Boolean,
    val isRuntimeApplicable: Boolean,
    val isUsable: Boolean
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("permission", permission)
        put("isDeclared", isDeclared)
        put("isGranted", isGranted)
        put("isRequired", isRequired)
        put("isRuntimeApplicable", isRuntimeApplicable)
        put("isUsable", isUsable)
    }
}

data class CapabilityMapping(
    val name: String,
    val hardwareState: PhysicalCapabilityState,
    val permissionState: PhysicalCapabilityState,
    val operationState: PhysicalCapabilityState,
    val verificationState: PhysicalCapabilityState
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("hardwareState", hardwareState.name)
        put("permissionState", permissionState.name)
        put("operationState", operationState.name)
        put("verificationState", verificationState.name)
    }
}

data class DeviceProfile(
    val manufacturer: String,
    val model: String,
    val device: String,
    val androidVersion: String,
    val apiLevel: Int,
    val buildFingerprint: String,
    val availableMemoryMb: Long,
    val totalMemoryMb: Long,
    val availableStorageMb: Long,
    val totalStorageMb: Long,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val screenDensityDpi: Int,
    val batteryLevel: Int,
    val isBatteryCharging: Boolean,
    val isAccessibilityActive: Boolean,
    val isMediaProjectionAuthorized: Boolean,
    val sensors: List<DiscoveredSensor>,
    val actuators: List<DiscoveredActuator>,
    val permissions: List<PermissionState>,
    val capabilityMappings: List<CapabilityMapping>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("manufacturer", manufacturer)
        put("model", model)
        put("device", device)
        put("androidVersion", androidVersion)
        put("apiLevel", apiLevel)
        put("fingerprint", buildFingerprint)
        put("availableMemoryMb", availableMemoryMb)
        put("totalMemoryMb", totalMemoryMb)
        put("availableStorageMb", availableStorageMb)
        put("totalStorageMb", totalStorageMb)
        put("screenWidthPx", screenWidthPx)
        put("screenHeightPx", screenHeightPx)
        put("screenDensityDpi", screenDensityDpi)
        put("batteryLevel", batteryLevel)
        put("isBatteryCharging", isBatteryCharging)
        put("isAccessibilityActive", isAccessibilityActive)
        put("isMediaProjectionAuthorized", isMediaProjectionAuthorized)
        put("sensors", JSONArray(sensors.map { it.toJson() }))
        put("actuators", JSONArray(actuators.map { it.toJson() }))
        put("permissions", JSONArray(permissions.map { it.toJson() }))
        put("capabilities", JSONArray(capabilityMappings.map { it.toJson() }))
    }
}

class DeviceCapabilityScanner(private val context: Context) {

    companion object {
        private val DANGEROUS_SYSTEM_PERMISSIONS = setOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    fun scanDeviceProfile(): DeviceProfile {
        val sensors = scanSensors()
        val actuators = scanActuators()
        val permissions = scanPermissions()
        val capabilities = buildCapabilityMap(sensors, actuators, permissions)

        val memInfo = android.app.ActivityManager.MemoryInfo()
        val actMgr = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        actMgr?.getMemoryInfo(memInfo)

        val totalMemMb = (memInfo?.totalMem ?: 0L) / (1024 * 1024)
        val availMemMb = (memInfo?.availMem ?: 0L) / (1024 * 1024)

        var totalStorageMb = 0L
        var availStorageMb = 0L
        try {
            val statFs = StatFs(Environment.getDataDirectory().path)
            totalStorageMb = (statFs.blockCountLong * statFs.blockSizeLong) / (1024 * 1024)
            availStorageMb = (statFs.availableBlocksLong * statFs.blockSizeLong) / (1024 * 1024)
        } catch (e: Throwable) {
            // Default 0 in test mocks
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val metrics = DisplayMetrics()
        wm?.defaultDisplay?.getRealMetrics(metrics)

        val batteryStatus: Intent? = try {
            context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Throwable) { null }

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val safeManufacturer = try { Build.MANUFACTURER ?: "Unknown" } catch (e: Throwable) { "Unknown" }
        val safeModel = try { Build.MODEL ?: "Unknown" } catch (e: Throwable) { "Unknown" }
        val safeDevice = try { Build.DEVICE ?: "Unknown" } catch (e: Throwable) { "Unknown" }
        val safeRelease = try { Build.VERSION.RELEASE ?: "8.1.0" } catch (e: Throwable) { "8.1.0" }
        val safeFingerprint = try { Build.FINGERPRINT ?: "Unknown" } catch (e: Throwable) { "Unknown" }

        return DeviceProfile(
            manufacturer = safeManufacturer,
            model = safeModel,
            device = safeDevice,
            androidVersion = safeRelease,
            apiLevel = Build.VERSION.SDK_INT,
            buildFingerprint = safeFingerprint,
            availableMemoryMb = availMemMb,
            totalMemoryMb = totalMemMb,
            availableStorageMb = availStorageMb,
            totalStorageMb = totalStorageMb,
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            screenDensityDpi = metrics.densityDpi,
            batteryLevel = batteryPct,
            isBatteryCharging = isCharging,
            isAccessibilityActive = AutomationAccessibilityService.isServiceEnabled.value,
            isMediaProjectionAuthorized = ScreenObservationProvider.isAuthorized.value,
            sensors = sensors,
            actuators = actuators,
            permissions = permissions,
            capabilityMappings = capabilities
        )
    }

    fun scanSensors(): List<DiscoveredSensor> {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return emptyList()

        val sensorList = try { sensorManager.getSensorList(Sensor.TYPE_ALL) } catch (e: Throwable) { emptyList() }
        return sensorList.map { sensor ->
            val category = when (sensor.type) {
                Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GRAVITY,
                Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ROTATION_VECTOR -> SensorCategory.MOTION
                Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_ORIENTATION -> SensorCategory.ORIENTATION
                Sensor.TYPE_PROXIMITY -> SensorCategory.PROXIMITY
                Sensor.TYPE_LIGHT, Sensor.TYPE_PRESSURE, Sensor.TYPE_AMBIENT_TEMPERATURE -> SensorCategory.ENVIRONMENT
                Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, Sensor.TYPE_SIGNIFICANT_MOTION -> SensorCategory.POSITION
                else -> SensorCategory.OTHER
            }

            DiscoveredSensor(
                type = sensor.type,
                name = sensor.name ?: "Sensor_${sensor.type}",
                vendor = sensor.vendor ?: "Unknown",
                version = sensor.version,
                power = sensor.power,
                resolution = sensor.resolution,
                maximumRange = sensor.maximumRange,
                minimumDelay = sensor.minDelay,
                category = category,
                availability = PhysicalCapabilityState.PRESENT
            )
        }
    }

    fun scanActuators(): List<DiscoveredActuator> {
        val actuators = mutableListOf<DiscoveredActuator>()

        // 1. Vibrator / Haptics
        val vibrator = try { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator } catch (e: Throwable) { null }
        val hasVibrator = vibrator?.hasVibrator() == true
        val hasVibratePermission = try { context.checkSelfPermission(android.Manifest.permission.VIBRATE) == PackageManager.PERMISSION_GRANTED } catch (e: Throwable) { false }
        actuators.add(
            DiscoveredActuator(
                name = "Haptic Vibrator",
                category = "Haptics",
                isPresent = hasVibrator,
                isObservable = false,
                isControllable = hasVibrator,
                isVerifiable = false,
                permissionRequired = android.Manifest.permission.VIBRATE,
                isCurrentlyAllowed = hasVibratePermission,
                availabilityState = if (hasVibrator) PhysicalCapabilityState.USABLE else PhysicalCapabilityState.UNSUPPORTED,
                details = mapOf("hasVibrator" to hasVibrator)
            )
        )

        // 2. Camera Flash / Torch
        val cameraManager = try { context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager } catch (e: Throwable) { null }
        var hasFlash = false
        var cameraCount = 0
        try {
            val cameraIds = cameraManager?.cameraIdList ?: emptyArray()
            cameraCount = cameraIds.size
            for (id in cameraIds) {
                val chars = cameraManager?.getCameraCharacteristics(id)
                if (chars?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                    hasFlash = true
                    break
                }
            }
        } catch (e: Throwable) {
            hasFlash = false
        }

        val hasCameraPerm = try { context.checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED } catch (e: Throwable) { false }

        actuators.add(
            DiscoveredActuator(
                name = "Camera Flash Torch",
                category = "Light/Actuator",
                isPresent = hasFlash,
                isObservable = true,
                isControllable = hasFlash,
                isVerifiable = true,
                permissionRequired = android.Manifest.permission.CAMERA,
                isCurrentlyAllowed = hasCameraPerm,
                availabilityState = when {
                    !hasFlash -> PhysicalCapabilityState.UNSUPPORTED
                    !hasCameraPerm -> PhysicalCapabilityState.BLOCKED
                    else -> PhysicalCapabilityState.USABLE
                },
                details = mapOf("cameraCount" to cameraCount, "hasFlash" to hasFlash)
            )
        )

        // 3. Display / Screen Control
        val powerManager = try { context.getSystemService(Context.POWER_SERVICE) as? PowerManager } catch (e: Throwable) { null }
        val isScreenOn = powerManager?.isInteractive == true
        val hasWakeLockPerm = try { context.checkSelfPermission(android.Manifest.permission.WAKE_LOCK) == PackageManager.PERMISSION_GRANTED } catch (e: Throwable) { false }

        actuators.add(
            DiscoveredActuator(
                name = "Display Control",
                category = "Display",
                isPresent = true,
                isObservable = true,
                isControllable = true,
                isVerifiable = true,
                permissionRequired = android.Manifest.permission.WAKE_LOCK,
                isCurrentlyAllowed = hasWakeLockPerm,
                availabilityState = PhysicalCapabilityState.USABLE,
                details = mapOf("isInteractive" to isScreenOn)
            )
        )

        // 4. Audio Actuator
        val audioManager = try { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager } catch (e: Throwable) { null }
        val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        actuators.add(
            DiscoveredActuator(
                name = "Audio Manager",
                category = "Audio",
                isPresent = audioManager != null,
                isObservable = true,
                isControllable = true,
                isVerifiable = true,
                permissionRequired = null,
                isCurrentlyAllowed = true,
                availabilityState = if (audioManager != null) PhysicalCapabilityState.USABLE else PhysicalCapabilityState.UNSUPPORTED,
                details = mapOf("ringerMode" to ringerMode)
            )
        )

        // 5. Global Navigation System Inputs (Back, Home, Recents, Notifications)
        val isAccActive = AutomationAccessibilityService.isServiceEnabled.value
        actuators.add(
            DiscoveredActuator(
                name = "System Navigation Controls (Back/Home/Recents)",
                category = "System Input",
                isPresent = true,
                isObservable = true,
                isControllable = isAccActive,
                isVerifiable = true,
                permissionRequired = android.Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
                isCurrentlyAllowed = isAccActive,
                availabilityState = if (isAccActive) PhysicalCapabilityState.USABLE else PhysicalCapabilityState.BLOCKED,
                details = mapOf("accessibilityActive" to isAccActive)
            )
        )

        return actuators
    }

    fun scanPermissions(): List<PermissionState> {
        val permissionsToCheck = listOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.VIBRATE,
            android.Manifest.permission.WAKE_LOCK,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.INTERNET
        )

        val pm = context.packageManager
        val pkgInfo = try {
            pm?.getPackageInfo(context.packageName ?: "", PackageManager.GET_PERMISSIONS)
        } catch (e: Throwable) {
            null
        }
        val declaredPermissions = pkgInfo?.requestedPermissions?.toSet() ?: emptySet()

        return permissionsToCheck.map { perm ->
            val isDeclared = declaredPermissions.contains(perm)
            val isGranted = try { context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED } catch (e: Throwable) { false }
            val isRuntime = try {
                val info = pm?.getPermissionInfo(perm, 0)
                if (info != null) {
                    (info.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_DANGEROUS) != 0
                } else {
                    DANGEROUS_SYSTEM_PERMISSIONS.contains(perm)
                }
            } catch (e: Throwable) {
                DANGEROUS_SYSTEM_PERMISSIONS.contains(perm)
            }

            PermissionState(
                permission = perm,
                isDeclared = isDeclared,
                isGranted = isGranted,
                isRequired = true,
                isRuntimeApplicable = isRuntime,
                isUsable = isDeclared && isGranted
            )
        }
    }

    private fun buildCapabilityMap(
        sensors: List<DiscoveredSensor>,
        actuators: List<DiscoveredActuator>,
        permissions: List<PermissionState>
    ): List<CapabilityMapping> {
        val mappings = mutableListOf<CapabilityMapping>()

        // Accessibility
        val isAcc = AutomationAccessibilityService.isServiceEnabled.value
        mappings.add(
            CapabilityMapping(
                name = "ACCESSIBILITY_AUTOMATION",
                hardwareState = PhysicalCapabilityState.PRESENT,
                permissionState = if (isAcc) PhysicalCapabilityState.PERMISSION_GRANTED else PhysicalCapabilityState.BLOCKED,
                operationState = if (isAcc) PhysicalCapabilityState.USABLE else PhysicalCapabilityState.BLOCKED,
                verificationState = if (isAcc) PhysicalCapabilityState.USABLE else PhysicalCapabilityState.BLOCKED
            )
        )

        // Camera
        val camActuator = actuators.find { it.name.contains("Torch") || it.category == "Light/Actuator" }
        val camPerm = permissions.find { it.permission == android.Manifest.permission.CAMERA }
        mappings.add(
            CapabilityMapping(
                name = "CAMERA_VISION",
                hardwareState = if (camActuator?.isPresent == true) PhysicalCapabilityState.PRESENT else PhysicalCapabilityState.UNSUPPORTED,
                permissionState = if (camPerm?.isGranted == true) PhysicalCapabilityState.PERMISSION_GRANTED else PhysicalCapabilityState.BLOCKED,
                operationState = if (camActuator?.isPresent == true && camPerm?.isGranted == true) PhysicalCapabilityState.USABLE else PhysicalCapabilityState.BLOCKED,
                verificationState = PhysicalCapabilityState.USABLE
            )
        )

        // Vibrator
        val vibActuator = actuators.find { it.name.contains("Vibrator") }
        val vibPerm = permissions.find { it.permission == android.Manifest.permission.VIBRATE }
        mappings.add(
            CapabilityMapping(
                name = "HAPTIC_FEEDBACK",
                hardwareState = if (vibActuator?.isPresent == true) PhysicalCapabilityState.PRESENT else PhysicalCapabilityState.UNSUPPORTED,
                permissionState = if (vibPerm?.isGranted == true) PhysicalCapabilityState.PERMISSION_GRANTED else PhysicalCapabilityState.BLOCKED,
                operationState = if (vibActuator?.isPresent == true && vibPerm?.isGranted == true) PhysicalCapabilityState.USABLE else PhysicalCapabilityState.BLOCKED,
                verificationState = PhysicalCapabilityState.UNSUPPORTED
            )
        )

        // Gyroscope / Motion
        val hasGyro = sensors.any { it.type == Sensor.TYPE_GYROSCOPE }
        mappings.add(
            CapabilityMapping(
                name = "GYROSCOPE_MOTION",
                hardwareState = if (hasGyro) PhysicalCapabilityState.PRESENT else PhysicalCapabilityState.UNSUPPORTED,
                permissionState = PhysicalCapabilityState.PERMISSION_GRANTED,
                operationState = if (hasGyro) PhysicalCapabilityState.USABLE else PhysicalCapabilityState.UNSUPPORTED,
                verificationState = if (hasGyro) PhysicalCapabilityState.USABLE else PhysicalCapabilityState.UNSUPPORTED
            )
        )

        return mappings
    }
}
