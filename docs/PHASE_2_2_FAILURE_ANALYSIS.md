# PHASE 2.2 FAILURE ANALYSIS

## Failure Classification
- **USER_APPROVAL_REQUIRED**: Action or test execution blocked due to ungranted explicit user approval for physical side effects.
- **SERVICE_UNAVAILABLE**: Accessibility or MediaProjection service is disabled by user in system settings.
- **PERMISSION_DENIED**: Required runtime permission (e.g. CAMERA, VIBRATE) is denied by user.
- **TARGET_NOT_FOUND / AMBIGUOUS_TARGET**: UI candidate resolution failed to find a unique target node in current hierarchy.
- **WAIT_TIMEOUT**: UI state change or expected text failed to appear within specified timeout window.
- **SYSTEM_ERROR**: Unhandled runtime exception encountered during execution.

## Investigation Workflow
1. Inspect `FAILURES.md` or Jetpack Compose Diagnostic UI Failures Screen.
2. Review `diagnosticTrace` step logs for the failed test ID.
3. Compare `observationBefore` vs `observationAfter` node counts and signatures.
4. Verify whether failure is hardware-bound (e.g., missing sensor), permission-bound, or execution timing-bound.
