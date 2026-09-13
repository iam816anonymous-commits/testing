# CreatorAutomation — Layer 2 Cross-App Observation Known Failures & Limitations

## HEADLESS / UNIT TEST LIMITATIONS

1. **Cross-App Window Events in Headless Environments**
   - Robolectric unit tests do not execute live multi-process Android system window switching.
   - Physical tests L2-X01 through L2-X06 are classified as `BLOCKED` in headless environments to avoid false PASS reports.

2. **Overlay View Window Manager Permissions in Headless Mocking**
   - Test L2-X07 verifies overlay toggle state logic directly in `CrossAppObservationTest.kt`.
