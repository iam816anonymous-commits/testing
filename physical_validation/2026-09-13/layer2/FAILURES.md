# CreatorAutomation — Layer 2 Known Failures & Limitations

## HEADLESS / UNIT TEST LIMITATIONS

1. **Live Accessibility Node Trees in Headless Environments**
   - Robolectric unit tests simulate AccessibilityNodeInfo trees via mock or synthetic nodes.
   - Physical tests L2-01 through L2-05 require live window accessibility events on TECNO IN6 hardware and are classified as `BLOCKED` in headless environments to avoid false PASS claims.

2. **Truthful Null Root Reporting**
   - Test L2-06 verifies that when no root accessibility node is available, the snapshot engine truthfully reports `isRootAvailable = false` with 0 nodes, preventing false success reports.
