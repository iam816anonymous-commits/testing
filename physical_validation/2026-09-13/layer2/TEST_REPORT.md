# CreatorAutomation — Layer 2 Physical Test Report

Device: TECNO IN6
Android Version: 8.1.0
API Level: 27
Timestamp: 1789319500000

## SUMMARY

| Test ID | Test Name | Headless Result | Physical Protocol Requirement | Failure Category |
|---|---|---|---|---|
| L2-01 | Home Screen Perception | BLOCKED | PASS when launcher package & node tree observed | ENVIRONMENT_CONSTRAINT |
| L2-02 | Settings Navigation Perception | BLOCKED | PASS when com.android.settings package & nodes observed | ENVIRONMENT_CONSTRAINT |
| L2-03 | Chrome Input Perception | BLOCKED | PASS when com.android.chrome package, text, & editables observed | ENVIRONMENT_CONSTRAINT |
| L2-04 | YouTube Layout Perception | BLOCKED | PASS when com.google.android.youtube package & scrollables observed | ENVIRONMENT_CONSTRAINT |
| L2-05 | Navigation Screen Change Detection | BLOCKED | PASS when state signature changes across manual screen transitions | ENVIRONMENT_CONSTRAINT |
| L2-06 | Empty/Limited Observation Perception | PASS | PASS when null/empty root produces isRootAvailable=false and totalNodeCount=0 | NONE |

---

## OBSERVATION CAPABILITY SUMMARY

* `LIVE_ROOT_ACQUIRED`: Explicitly checked
* `PACKAGE_DETECTION`: Explicitly extracted
* `UI_TREE_TRAVERSAL`: Safe recursive depth-limited traversal
* `VISIBLE_TEXT_OBSERVED`: Distinct visible text collection
* `CLICKABLE_OBSERVED`: IsClickable node collection
* `EDITABLE_OBSERVED`: IsEditable node collection
* `SCROLLABLE_OBSERVED`: IsScrollable node collection
* `BOUNDS_OBSERVED`: Screen bounds rect shortString representation
* `REFRESH_OBSERVATION`: Interactive manual snapshot refresh with new timestamp
* `SCREEN_CHANGE_DETECTION`: StateSignatureGenerator hashing
* `EMPTY_UNAVAILABLE_HANDLING`: Truthful isRootAvailable=false on null root
