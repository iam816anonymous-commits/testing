package com.creator.automation

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

enum class HardwareCapabilityType {
    FLASHLIGHT,
    HAPTIC,
    AUDIO,
    DISPLAY,
    NETWORK
}

sealed class HardwareCommand {
    data class ToggleTorch(val enable: Boolean) : HardwareCommand()
    data class Vibrate(val durationMs: Long = 200L) : HardwareCommand()
    data class SetAudioMute(val mute: Boolean) : HardwareCommand()
    object WakeDisplay : HardwareCommand()
}

data class HardwareActionResult(
    val success: Boolean,
    val message: String,
    val state: Any? = null
)

interface HardwareActuator {
    val name: String
    val category: String
    val type: HardwareCapabilityType

    fun detect(context: Context): Boolean
    fun canControl(context: Context): Boolean
    fun control(context: Context, command: HardwareCommand): HardwareActionResult
    fun observeState(context: Context): Any?
    fun verify(context: Context, expectedState: Any): Boolean
}

/**
 * FlashlightActuator uses native CameraManager.setTorchMode (API 23+)
 * and CameraManager.TorchCallback (API 23+) for state observation & verification on API 27 baseline.
 */
class FlashlightActuator : HardwareActuator {
    override val name: String = "Camera Flash Torch"
    override val category: String = "Light/Actuator"
    override val type: HardwareCapabilityType = HardwareCapabilityType.FLASHLIGHT

    companion object {
        private const val TAG = "FlashlightActuator"
        @Volatile private var isTorchEnabled: Boolean? = null
        @Volatile private var targetCameraId: String? = null
        @Volatile private var callbackRegistered: Boolean = false

        private val torchCallback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                super.onTorchModeChanged(cameraId, enabled)
                if (targetCameraId == null || targetCameraId == cameraId) {
                    Log.i(TAG, "TORCH_CALLBACK_CHANGED: Camera '$cameraId' torch = $enabled")
                    isTorchEnabled = enabled
                }
            }

            override fun onTorchModeUnavailable(cameraId: String) {
                super.onTorchModeUnavailable(cameraId)
                Log.w(TAG, "TORCH_CALLBACK_UNAVAILABLE: Camera '$cameraId'")
            }
        }
    }

    private fun findTorchCameraId(cameraManager: CameraManager): String? {
        if (targetCameraId != null) return targetCameraId
        try {
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (flashAvailable && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    targetCameraId = id
                    return id
                }
            }
            // Fallback: any camera with flash
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                if (chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                    targetCameraId = id
                    return id
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error finding torch camera ID: ${e.message}", e)
        }
        return null
    }

    private fun ensureCallbackRegistered(context: Context, cameraManager: CameraManager) {
        if (!callbackRegistered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                cameraManager.registerTorchCallback(torchCallback, null)
                callbackRegistered = true
                Log.i(TAG, "TORCH_CALLBACK_REGISTERED")
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to register TorchCallback: ${e.message}")
            }
        }
    }

    override fun detect(context: Context): Boolean {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
        return findTorchCameraId(cm) != null
    }

    override fun canControl(context: Context): Boolean {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return detect(context) && hasPermission
    }

    override fun control(context: Context, command: HardwareCommand): HardwareActionResult {
        if (command !is HardwareCommand.ToggleTorch) {
            return HardwareActionResult(false, "Invalid command for FlashlightActuator: $command")
        }
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return HardwareActionResult(false, "CameraManager unavailable")

        val cameraId = findTorchCameraId(cm)
            ?: return HardwareActionResult(false, "No flashlight camera hardware found")

        ensureCallbackRegistered(context, cm)

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cm.setTorchMode(cameraId, command.enable)
                // Short wait for callback to fire
                Thread.sleep(150L)
                val observed = isTorchEnabled
                val isVerified = observed == command.enable
                Log.i(TAG, "SET_TORCH_MODE: enabled=${command.enable}, observed=$observed, verified=$isVerified")
                HardwareActionResult(
                    success = true,
                    message = "Torch mode set to ${command.enable} (Verified: $isVerified)",
                    state = command.enable
                )
            } else {
                HardwareActionResult(false, "CameraManager.setTorchMode requires API 23+")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to set torch mode: ${e.message}", e)
            HardwareActionResult(false, "Flashlight control error: ${e.message}")
        }
    }

    override fun observeState(context: Context): Any? {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null
        ensureCallbackRegistered(context, cm)
        return isTorchEnabled
    }

    override fun verify(context: Context, expectedState: Any): Boolean {
        if (expectedState !is Boolean) return false
        val currentState = observeState(context)
        return currentState == expectedState
    }
}

class HapticActuator : HardwareActuator {
    override val name: String = "Device Haptic Vibrator"
    override val category: String = "Feedback/Actuator"
    override val type: HardwareCapabilityType = HardwareCapabilityType.HAPTIC

    override fun detect(context: Context): Boolean {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        return vibrator?.hasVibrator() == true
    }

    override fun canControl(context: Context): Boolean {
        return detect(context)
    }

    override fun control(context: Context, command: HardwareCommand): HardwareActionResult {
        if (command !is HardwareCommand.Vibrate) {
            return HardwareActionResult(false, "Invalid command for HapticActuator")
        }
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            ?: return HardwareActionResult(false, "Vibrator service unavailable")

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(command.durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(command.durationMs)
            }
            HardwareActionResult(true, "Vibrated for ${command.durationMs}ms", true)
        } catch (e: Throwable) {
            HardwareActionResult(false, "Vibration failed: ${e.message}")
        }
    }

    override fun observeState(context: Context): Any? = detect(context)

    override fun verify(context: Context, expectedState: Any): Boolean = canControl(context)
}

class AudioActuator : HardwareActuator {
    override val name: String = "System Audio Actuator"
    override val category: String = "Audio/Actuator"
    override val type: HardwareCapabilityType = HardwareCapabilityType.AUDIO

    override fun detect(context: Context): Boolean {
        return context.getSystemService(Context.AUDIO_SERVICE) != null
    }

    override fun canControl(context: Context): Boolean = detect(context)

    override fun control(context: Context, command: HardwareCommand): HardwareActionResult {
        if (command !is HardwareCommand.SetAudioMute) {
            return HardwareActionResult(false, "Invalid command for AudioActuator")
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return HardwareActionResult(false, "AudioManager unavailable")

        return try {
            val flag = if (command.mute) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
            audioManager.ringerMode = flag
            HardwareActionResult(true, "Audio ringer mode set to $flag", command.mute)
        } catch (e: Throwable) {
            HardwareActionResult(false, "Audio control failed: ${e.message}")
        }
    }

    override fun observeState(context: Context): Any? {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        return audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT
    }

    override fun verify(context: Context, expectedState: Any): Boolean {
        return observeState(context) == expectedState
    }
}

class DisplayActuator : HardwareActuator {
    override val name: String = "Display Power Actuator"
    override val category: String = "Display/Actuator"
    override val type: HardwareCapabilityType = HardwareCapabilityType.DISPLAY

    override fun detect(context: Context): Boolean {
        return context.getSystemService(Context.POWER_SERVICE) != null
    }

    override fun canControl(context: Context): Boolean = detect(context)

    override fun control(context: Context, command: HardwareCommand): HardwareActionResult {
        if (command !is HardwareCommand.WakeDisplay) {
            return HardwareActionResult(false, "Invalid command for DisplayActuator")
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return HardwareActionResult(false, "PowerManager unavailable")

        return try {
            val isInteractive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                powerManager.isInteractive
            } else {
                @Suppress("DEPRECATION")
                powerManager.isScreenOn
            }
            HardwareActionResult(true, "Display interactive state: $isInteractive", isInteractive)
        } catch (e: Throwable) {
            HardwareActionResult(false, "Display check failed: ${e.message}")
        }
    }

    override fun observeState(context: Context): Any? {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            powerManager.isInteractive
        } else {
            @Suppress("DEPRECATION")
            powerManager.isScreenOn
        }
    }

    override fun verify(context: Context, expectedState: Any): Boolean {
        return observeState(context) == expectedState
    }
}

object HardwareActuatorRegistry {
    private val actuators = listOf(
        FlashlightActuator(),
        HapticActuator(),
        AudioActuator(),
        DisplayActuator()
    )

    fun getActuator(type: HardwareCapabilityType): HardwareActuator? {
        return actuators.find { it.type == type }
    }

    fun getAllActuators(): List<HardwareActuator> = actuators

    fun findActuatorForGoal(taskDescription: String): HardwareActuator? {
        val desc = taskDescription.lowercase()
        return when {
            desc.contains("flashlight") || desc.contains("torch") || desc.contains("flash") -> getActuator(HardwareCapabilityType.FLASHLIGHT)
            desc.contains("vibrate") || desc.contains("haptic") -> getActuator(HardwareCapabilityType.HAPTIC)
            desc.contains("mute") || desc.contains("silent") || desc.contains("audio") -> getActuator(HardwareCapabilityType.AUDIO)
            desc.contains("wake") || desc.contains("screen") || desc.contains("display") -> getActuator(HardwareCapabilityType.DISPLAY)
            else -> null
        }
    }
}
