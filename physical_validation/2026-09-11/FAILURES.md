# PHYSICAL VALIDATION FAILURES & BLOCKS

## TEST-PHY-001 - System Home Navigation (GO_HOME)
Status: BLOCKED
Reason: Blocked / Unverified
Trace:
  - Dispatching GLOBAL_ACTION_HOME via AndroidAutomationCompat

## TEST-PHY-002 - System Back Navigation (GO_BACK)
Status: BLOCKED
Reason: Blocked / Unverified
Trace:
  - Dispatching GLOBAL_ACTION_BACK via AndroidAutomationCompat

## TEST-PHY-003 - Launch Application - YouTube
Status: FAIL
Reason: Blocked / Unverified
Trace:
  - AppResolver Youtube status: APP_NOT_INSTALLED, package: null

## TEST-PHY-004 - Launch Application - Chrome
Status: FAIL
Reason: Blocked / Unverified
Trace:
  - AppResolver Chrome status: APP_NOT_INSTALLED, package: null

## TEST-PHY-005 - Chrome Address Bar Type Text Resolution (CHROME_TYPE_TEXT)
Status: BLOCKED
Reason: Blocked / Unverified
Trace:
  - Resolving address bar or search target for Chrome input

## TEST-PHY-007 - YouTube Search Submit / Press Enter (YOUTUBE_SEARCH_SUBMIT)
Status: BLOCKED
Reason: Blocked / Unverified
Trace:
  - Testing SUBMIT_INPUT action mechanism for search

## TEST-PHY-008 - Generic Text Editor Type Text (NOTEBOOK_TYPE_TEXT)
Status: BLOCKED
Reason: Blocked / Unverified
Trace:
  - Testing generic text injection into focused editable target

## TEST-PHY-009 - Generic Scroll Action Resolution (GENERIC_SCROLL)
Status: BLOCKED
Reason: Blocked / Unverified
Trace:
  - Testing 3-tier scroll execution (Accessibility -> Scrollable container -> Gesture swipe)

## TEST-PHY-010 - Read Visible Screen Content (READ_VISIBLE_UI)
Status: FAIL
Reason: Blocked / Unverified
Trace:
  - Extracting visible screen text via GoalVerifier
  - GoalVerifier result: isVerified=false, explanation=Observation failed: Active screen snapshot is empty or unobservable

## TEST-PHY-011 - Flashlight Torch State Verification
Status: BLOCKED
Reason: VERIFICATION_UNAVAILABLE: Flashlight command dispatched, but torch hardware state verification is unobservable on this device.
Trace:
  - Verifying flashlight torch hardware state via GoalVerifier
  - GoalVerifier flashlight result: status=UNKNOWN, explanation=VERIFICATION_UNAVAILABLE: Flashlight command dispatched, but torch hardware state verification is unobservable on this device.

## TEST-PHY-012 - Home Screen Launcher App Discovery (HOME_SCREEN_APP_DISCOVERY)
Status: FAIL
Reason: Blocked / Unverified
Trace:
  - Scanning launcher apps via PackageManager queryIntentActivities
  - Found 0 launcher apps

## TEST-PHY-013 - Home Screen Generic Scroll & App Discovery
Status: BLOCKED
Reason: Blocked / Unverified
Trace:
  - Verifying generic scroll and app discovery capability on launcher

## TEST-PHY-015 - Safe In-App Exploration & Non-Destructive Control Interaction
Status: BLOCKED
Reason: Blocked / Unverified
Trace:
  - Testing safe non-destructive control interaction evaluation

## TEST-INP-001 - System Home Button Dispatch
Status: BLOCKED
Reason: Accessibility service not active on device
Trace:
  - Checking Accessibility Service state for Home key dispatch

## TEST-SAF-001 - External Side-Effect Safety Interlock
Status: BLOCKED
Reason: Test requires explicit user permission/side-effect approval
Trace:
  - Blocked side-effect test: TEST-SAF-001
