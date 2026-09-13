# CreatorAutomation — Layer 2 Cross-App Observation Physical Test Report

Device: TECNO IN6
Android Version: 8.1.0
API Level: 27
Timestamp: 1789320000000

## SUMMARY

| Test ID | Test Name | Headless Result | Physical Protocol Requirement | Failure Category |
|---|---|---|---|---|
| L2-X01 | CreatorAutomation -> Home | BLOCKED | PASS when foregroundPackage transitions to launcher package after Home press | ENVIRONMENT_CONSTRAINT |
| L2-X02 | Open Chrome Manually | BLOCKED | PASS when foregroundPackage = com.android.chrome while CA is in background | ENVIRONMENT_CONSTRAINT |
| L2-X03 | Change Chrome Screen | BLOCKED | PASS when eventCount & observationTimestamp update on Chrome screen changes | ENVIRONMENT_CONSTRAINT |
| L2-X04 | YouTube Cross-App Observation | BLOCKED | PASS when foregroundPackage = com.google.android.youtube with active node tree | ENVIRONMENT_CONSTRAINT |
| L2-X05 | Settings Cross-App Observation | BLOCKED | PASS when foregroundPackage = com.android.settings with active node tree | ENVIRONMENT_CONSTRAINT |
| L2-X06 | Return to CreatorAutomation | BLOCKED | PASS when foregroundPackage switches back to com.creator.automation cleanly | ENVIRONMENT_CONSTRAINT |
| L2-X07 | Overlay Lifecycle | PASS | PASS when show/hide diagnostic overlay attaches/removes TYPE_ACCESSIBILITY_OVERLAY cleanly without duplicate views | NONE |

---

## CROSS-APP OBSERVATION GATE STATUS

* `ACCESSIBILITY_SERVICE_CONNECTED`: Authority for observation
* `ACTIVITY_BACKGROUND_OBSERVATION`: Activity backgrounded, service continues receiving events
* `ZERO_POLLUTION_GUARANTEE`: Overlay nodes excluded when observing external apps
* `OVERLAY_TYPE`: `TYPE_ACCESSIBILITY_OVERLAY` (API 27 supported)
