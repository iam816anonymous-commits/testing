# Capability and Permission Control Architecture

## 1. 6-Layer Capability Taxonomy

Every capability in `CreatorAutomation` is classified according to six distinct, non-collapsed state dimensions:

```
          PHYSICAL DEVICE HARDWARE
                     ↓
             HARDWARE_PRESENT
                     ↓
             SUPPORTED_BY_API (API 27+)
                     ↓
             PERMISSION_GRANTED
                     ↓
             SERVICE_AVAILABLE (Accessibility / MediaProjection)
                     ↓
               USER_ENABLED (User Toggle)
                     ↓
                 USABLE
```

---

## 2. Capability State Matrix

| Capability ID | Category | Hardware Dependency | API Support (API 27) | Permission Dependency | User Default | Usable Condition |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `ACCESSIBILITY_AUTOMATION` | Navigation / Control | None | Supported | Accessibility Service | `ON` | `isAccActive && userEnabled` |
| `SYSTEM_NAVIGATION` | Navigation | None | Supported | Accessibility Service | `ON` | `isAccActive && userEnabled` |
| `GENERIC_INPUT_TYPING` | Input | None | Supported | Accessibility Service | `ON` | `isAccActive && userEnabled` |
| `GENERIC_SCROLLING` | Gesture | None | Supported | Accessibility Service | `ON` | `isAccActive && userEnabled` |
| `SCREEN_CAPTURE` | Vision | Display | Supported | MediaProjection | `ON` | `isScreenAuthorized && userEnabled` |
| `CAMERA_VISION` | Sensor | Camera Flash | Supported | `CAMERA` permission | `OFF` | `hasCameraPerm && userEnabled` |
| `HAPTIC_FEEDBACK` | Actuator | Vibrator | Supported | `VIBRATE` permission | `ON` | `hasVibratePerm && userEnabled` |
| `SENSORS` | Motion | Gyroscope | Supported | None | `ON` | `hasSensor && userEnabled` |

---

## 3. On-Demand Permission Policy

1. **No Silent First-Launch Request:** Dangerous runtime permissions (`CAMERA`, `RECORD_AUDIO`, `LOCATION`) are NEVER requested automatically on application launch.
2. **On-Demand Permission Request:** Permissions are requested ONLY when the user explicitly enables or uses the corresponding capability in the Capabilities & Permissions Control Center.
3. **User Enable/Disable Policy Enforcement:** If `userEnabled = false`, the execution gate and executor block all actions requiring that capability, even if the Android permission is technically granted.
