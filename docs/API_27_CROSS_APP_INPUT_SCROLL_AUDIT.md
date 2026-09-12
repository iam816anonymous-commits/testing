# API 27 Cross-App Text Input & Submit Capability Audit

## 1. Executive Summary
Physical device testing on TECNO IN6 (Android 8.1.0 / API 27) revealed distinct cross-application accessibility representations for editable text input and search submission across Chrome, YouTube, and Notebook applications:
- **Chrome:** Exposes standard `EditText` nodes with `ACTION_SET_TEXT` support and explicit semantic search/submit controls ("Go", "Search").
- **YouTube:** Wraps search inputs inside custom `ViewGroup` containers where the editable field is a child node with `isEditable = true` and `isFocusable = true`, requiring focus activation (`ACTION_FOCUS` + `ACTION_CLICK`) before `ACTION_SET_TEXT` dispatch.
- **Notebook / Generic Apps:** Custom input views that may lack explicit search buttons, requiring multi-mechanism `SUBMIT_INPUT` resolution.

---

## 2. Multi-Mechanism `SUBMIT_INPUT` Hierarchy
1. **Tier 1: Semantic Submit Controls:** Matches clickable controls with text/desc "Search", "Go", "Enter", "Submit", "Find".
2. **Tier 2: IME / Editor Action or Node Click:** Dispatches `ACTION_CLICK` on focused editable node or IME enter trigger.
3. **Tier 3: Dynamic Gesture Tap Fallback:** Dispatches gesture tap at the center bounds of the submit control.
4. **Tier 4: Structured Failure Reporting:** Reports `UNSUPPORTED_SUBMISSION_MECHANISM` or `NEEDS_USER_INPUT` if unresolvable.
