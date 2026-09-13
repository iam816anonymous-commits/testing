# CreatorAutomation — Layer 2 Physical Test Report (Observation Upgrade)

Device: TECNO IN6
Android Version: 8.1.0
API Level: 27
Timestamp: 1789320500000

## SUMMARY

| Test ID | Test Name | Headless Result | Physical Protocol Requirement | Failure Category |
|---|---|---|---|---|
| L2-OBS-01 | YouTube Idle Observation Refresh | BLOCKED | PASS when YouTube package & fresh nodes captured on idle screen without user movement | ENVIRONMENT_CONSTRAINT |
| L2-OBS-02 | Chrome Idle Observation Refresh | BLOCKED | PASS when Chrome package & fresh nodes captured on idle screen without user movement | ENVIRONMENT_CONSTRAINT |
| L2-OBS-03 | Settings Idle Observation Refresh | BLOCKED | PASS when Settings package & fresh nodes captured on idle screen without user movement | ENVIRONMENT_CONSTRAINT |
| L2-OBS-04 | Screen Transition Observation Update | BLOCKED | PASS when manual navigation updates snapshot and state signature | ENVIRONMENT_CONSTRAINT |
| L2-OBS-05 | On-Demand MediaProjection Capture | BLOCKED | PASS when CAPTURE SCREENSHOT returns valid frame dimensions and visual signature | ENVIRONMENT_CONSTRAINT |
| L2-OBS-06 | Idle SystemUI Stale State Prevention | BLOCKED | PASS when idle screen maintains actual foreground app package rather than reverting to SystemUI | ENVIRONMENT_CONSTRAINT |

---

## OBSERVATION UPGRADE STATUS

* `CURRENT_FOREGROUND_DETECTION`: Multi-tier live root / window package resolution
* `FRESH_ROOT`: On-demand query via `refreshCurrentScreenObservation()`
* `STRUCTURED_UI_TREE`: Traversed and populated into `UiSnapshot`
* `EXPLICIT_REFRESH`: `[ REFRESH CURRENT SCREEN ]` button triggers instant capture
* `IDLE_OBSERVATION`: Independent of user gesture/movement
* `MEDIAPROJECTION_SCREENSHOT`: On-demand diagnostic capture
