package com.creator.automation

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CapabilityPreferenceStore {

    private const val PREF_NAME = "creator_automation_capability_prefs"
    private const val PREFIX = "cap_enabled_"

    private val _enabledCapabilities = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val enabledCapabilities: StateFlow<Map<String, Boolean>> = _enabledCapabilities.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Initializes capability preferences from SharedPreferences.
     * Default state: All capabilities enabled except high-risk hardware (Camera/Microphone) default to FALSE until explicitly toggled by user.
     */
    fun init(context: Context) {
        val prefs = getPrefs(context)
        val allMap = mutableMapOf<String, Boolean>()

        val defaultEnabledList = listOf(
            "ACCESSIBILITY_AUTOMATION",
            "SYSTEM_NAVIGATION",
            "GENERIC_INPUT_TYPING",
            "GENERIC_SCROLLING",
            "SCREEN_CAPTURE",
            "HAPTIC_FEEDBACK",
            "AUDIO_MANAGER",
            "NETWORK_ACCESS",
            "SENSORS"
        )

        val defaultDisabledList = listOf(
            "CAMERA_VISION",
            "MICROPHONE_AUDIO",
            "LOCATION_SERVICES"
        )

        for (cap in defaultEnabledList) {
            allMap[cap] = prefs.getBoolean(PREFIX + cap, true)
        }
        for (cap in defaultDisabledList) {
            allMap[cap] = prefs.getBoolean(PREFIX + cap, false)
        }

        _enabledCapabilities.value = allMap
    }

    fun isUserEnabled(context: Context, capabilityKey: String, defaultValue: Boolean = true): Boolean {
        val current = _enabledCapabilities.value[capabilityKey]
        if (current != null) return current
        val prefs = getPrefs(context)
        val value = prefs.getBoolean(PREFIX + capabilityKey, defaultValue)
        val updated = _enabledCapabilities.value.toMutableMap()
        updated[capabilityKey] = value
        _enabledCapabilities.value = updated
        return value
    }

    fun setUserEnabled(context: Context, capabilityKey: String, enabled: Boolean) {
        val prefs = getPrefs(context)
        prefs.edit().putBoolean(PREFIX + capabilityKey, enabled).apply()
        val updated = _enabledCapabilities.value.toMutableMap()
        updated[capabilityKey] = enabled
        _enabledCapabilities.value = updated
    }
}
