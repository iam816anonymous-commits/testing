# PHASE 2.2 PHYSICAL TEST PLAN

## Safe Physical Validation Test Suite
1. **TEST-OBS-001 (Accessibility UI Hierarchy Extraction)**:
   - Verifies root node tree capture via `AutomationAccessibilityService`.
   - Criteria: Non-empty node collection when accessibility service is enabled.

2. **TEST-RES-001 (Action Candidate Resolution & Ambiguity)**:
   - Verifies target node resolution scoring and duplicate candidate detection.
   - Criteria: Exact candidate count matching without target ambiguity masking.

3. **TEST-INP-001 (System Home Key Dispatch)**:
   - Verifies global system action capability (`GLOBAL_ACTION_HOME`).
   - Criteria: Service availability check and successful action dispatch.

4. **TEST-VER-001 (WaitEngine Timeout Validation)**:
   - Verifies condition polling and deterministic timeout propagation.
   - Criteria: `WaitResult.TIMEOUT` returned upon unsatisfied target condition.

5. **TEST-REC-001 (RecoveryManager Bounded Retry Counter)**:
   - Verifies failure tracking and retry upper-bound enforcement.
   - Criteria: Permits up to max attempts, denies execution past threshold.

6. **TEST-SAF-001 (External Side-Effect Safety Interlock)**:
   - Verifies safety blocking for side-effect operations lacking user approval.
   - Criteria: Status returns `BLOCKED` with failure category `USER_APPROVAL_REQUIRED`.
