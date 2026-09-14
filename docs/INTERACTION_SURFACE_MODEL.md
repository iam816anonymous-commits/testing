# INTERACTION SURFACE MODEL

**Target Device Architecture:** TECNO IN6 / Android 8.1.0 / API 27 / ~4 GB RAM / ~64 GB Storage
**Objective:** Define the complete, generic interaction-surface model extracted from Android Accessibility trees without app-specific hardcoding.

---

## 1. MODEL DEFINITION (`InteractionSurface`)

```kotlin
data class InteractionSurface(
    val surfaceId: String = UUID.randomUUID().toString(),
    val index: Int = 0,
    val observationId: String = "",
    val windowId: Int = -1,
    val text: String? = null,
    val contentDescription: String? = null,
    val viewId: String? = null,
    val className: String? = null,
    val role: SurfaceRole = SurfaceRole.UNKNOWN,
    val isClickable: Boolean = false,
    val isLongClickable: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false,
    val isFocused: Boolean = false,
    val isFocusable: Boolean = false,
    val isEnabled: Boolean = true,
    val isVisibleToUser: Boolean = true,
    val bounds: String? = null,
    val parentContext: String? = null,
    val observationTimestamp: Long = System.currentTimeMillis(),
    val stateSignature: String = "",
    val confidence: Double = 1.0
)
```

---

## 2. GENERIC ROLE INFERENCE TAXONOMY (`SurfaceRole`)

Roles are inferred purely from accessibility node properties and API 27 class hierarchies:

| Role | Properties / Class Criteria | Example Controls |
|---|---|---|
| **BUTTON** | `isClickable == true` AND `className` contains "Button" or "ImageButton" | Play, Submit, Options |
| **EDITABLE** | `isEditable == true` OR `className` contains "EditText" | Search input, comment box |
| **SCROLL_CONTAINER**| `isScrollable == true` OR `className` contains "ScrollView", "RecyclerView", "ListView" | Feed, list, web view |
| **CHECKBOX** | `className` contains "CheckBox" or `isCheckable == true` | Settings toggle |
| **SWITCH** | `className` contains "Switch" or "ToggleButton" | System setting switch |
| **IMAGE** | `className` contains "ImageView" with `contentDescription` | Profile icon, thumbnail |
| **TAB** | `className` contains "Tab" or "TabView" | YouTube Shorts tab |
| **LINK** | `className` contains "Url" or "Link" | Web link |
| **TEXT** | Non-empty text without clickability | Headline, label |
| **UNKNOWN** | Fallback for unclassified nodes | Generic ViewGroup |

---

## 3. RANKING & ANCESTOR CLICKABLE RESOLUTION

1. **Deterministic Ranking Priority:**
   - Priority 1: View ID Resource Name Match (`1.0` confidence)
   - Priority 2: Exact Content Description Match (`0.90` confidence)
   - Priority 3: Exact Visible Text Match (`0.85` confidence)
   - Priority 4: Role / Class Compatibility Match (`0.40` confidence -> **Always flagged as AMBIGUOUS if candidateCount > 1**)

2. **Ancestor Clickable Node Walking:**
   - If a target text node has `isClickable == false`, `ActionResolver` walks up the `parent` chain to locate the closest clickable ancestor node.
   - The action is dispatched against the clickable ancestor while retaining the semantic identity of the target text node.
