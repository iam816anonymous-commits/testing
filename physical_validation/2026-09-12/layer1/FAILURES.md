# CreatorAutomation — Layer 1 Known Failures & Limitations

## HEADLESS / UNIT TEST LIMITATIONS

1. **Unbound Service Instance in Unit Tests**
   - Headless unit test environments (Robolectric) do not bind a live system AccessibilityService IPC.
   - Physical tests L1-001 through L1-005 are classified as `BLOCKED` in headless environments to ensure no false PASS reports are made without real TECNO IN6 device execution.

2. **Physical Device Onboarding Protocol**
   - Real hardware execution requires navigating to `Settings -> Accessibility -> CreatorAutomation` on the TECNO IN6 to grant live accessibility permissions and bind the service instance.
