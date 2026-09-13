# CreatorAutomation — Layer 1 Physical Test Report

Device: TECNO IN6
Android Version: 8.1.0
API Level: 27
Timestamp: 1789233100000

## SUMMARY

| Test ID | Test Name | Headless Result | Physical Protocol Requirement | Failure Category |
|---|---|---|---|---|
| L1-001 | Service Connection | BLOCKED | PASS when Settings -> Accessibility service is toggled ON | ENVIRONMENT_CONSTRAINT |
| L1-002 | Real Accessibility Event | BLOCKED | PASS when user navigates Settings/Apps and eventCount > 0 | ENVIRONMENT_CONSTRAINT |
| L1-003 | Active Window | BLOCKED | PASS when getRootInActiveWindow() returns active root | ENVIRONMENT_CONSTRAINT |
| L1-004 | Observation Snapshot | BLOCKED | PASS when UiSnapshot produces non-zero node summary | ENVIRONMENT_CONSTRAINT |
| L1-005 | Service Lifecycle | BLOCKED | PASS when service re-binds across OFF/ON toggle | ENVIRONMENT_CONSTRAINT |

---

## CAPABILITY STATE SEPARATION

* `ACCESSIBILITY_PERMISSION_ENABLED`: Explicitly tracked
* `ACCESSIBILITY_SERVICE_CONNECTED`: Explicitly tracked
* `ACCESSIBILITY_ROOT_AVAILABLE`: Explicitly tracked
* `ACCESSIBILITY_EVENTS_RECEIVED`: Explicitly tracked

In headless test environments, permission checks pass while physical binding remains unbound until manual activation on TECNO IN6 hardware.
