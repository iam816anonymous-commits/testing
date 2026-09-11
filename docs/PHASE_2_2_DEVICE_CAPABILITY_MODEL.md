# PHASE 2.2 DEVICE CAPABILITY MODEL

## Capability State Taxonomy
- **PRESENT**: Hardware component physically detected on device.
- **AVAILABLE**: Android framework service or API exposed and reachable.
- **PERMISSION_GRANTED**: Required manifest runtime permission is granted.
- **USABLE**: Capability is present, granted, and operational for automation.
- **UNSUPPORTED**: Hardware component or framework feature is absent.
- **BLOCKED**: Capability is physically present but blocked by missing permissions or user interlocks.
- **UNKNOWN**: State could not be definitively evaluated.

## Capability Mapping Structure
| Capability | Hardware | Permission | Operation | Verification |
| --- | --- | --- | --- | --- |
| ACCESSIBILITY_AUTOMATION | PRESENT | PERMISSION_GRANTED | USABLE | USABLE |
| CAMERA_VISION | PRESENT | PERMISSION_GRANTED | USABLE | USABLE |
| HAPTIC_FEEDBACK | PRESENT | PERMISSION_GRANTED | USABLE | UNSUPPORTED |
| GYROSCOPE_MOTION | PRESENT / UNSUPPORTED | PERMISSION_GRANTED | USABLE / UNSUPPORTED | USABLE / UNSUPPORTED |
