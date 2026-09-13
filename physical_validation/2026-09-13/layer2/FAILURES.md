# CreatorAutomation — Layer 2 Observation Upgrade Known Failures & Limitations

## HEADLESS / UNIT TEST LIMITATIONS

1. **System Window State in Headless Environment**
   - Robolectric unit test environments do not run a multi-process Android system window manager.
   - Tests L2-OBS-01 through L2-OBS-06 require live TECNO IN6 execution and are marked `BLOCKED` in headless mode to maintain zero false-positive claims.

2. **SystemUI Stale Event Resolution**
   - In physical runtime, `SystemUI` accessibility events (e.g. status bar / navigation bar redraws) occur periodically while the device is idle. `resolveCurrentForegroundPackage()` resolves the active application window/root package rather than storing `com.android.systemui`, fixing the idle observation stale state.
