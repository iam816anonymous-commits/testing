package com.creator.automation

object InteractionDiscoveryEngine {

    private val NAVIGATION_KEYWORDS = listOf("back", "home", "menu", "close", "cancel", "navigate up", "drawer")
    private val SUBMIT_KEYWORDS = listOf("search", "submit", "go", "enter", "done", "find", "send")
    private val DESTRUCTIVE_KEYWORDS = listOf("delete", "clear data", "uninstall", "format", "remove account", "factory reset", "pay", "buy")

    /**
     * Extracts generic UI interaction surfaces from a ScreenObservation or UiSnapshot.
     */
    fun discoverSurfaces(snapshot: UiSnapshot): List<InteractionSurface> {
        val surfaces = mutableListOf<InteractionSurface>()
        val stateSig = StateSignatureGenerator.generateSignature(snapshot)

        snapshot.allNodes.forEachIndexed { index, node ->
            val label = node.text?.trim()
            val contentDesc = node.contentDescription?.trim()
            val viewId = node.viewIdResourceName
            val className = node.className
            val bounds = parseBounds(node.boundsInScreen)

            val displayLabel = label?.takeIf { it.isNotBlank() } ?: contentDesc?.takeIf { it.isNotBlank() } ?: viewId?.substringAfterLast('/')

            // 1. Editable Field
            if (node.isEditable || className?.contains("EditText", ignoreCase = true) == true) {
                surfaces.add(
                    InteractionSurface(
                        id = "surface_editable_$index",
                        surfaceType = InteractionSurfaceType.EDITABLE_FIELD,
                        label = displayLabel,
                        contentDescription = contentDesc,
                        resourceId = viewId,
                        className = className,
                        bounds = bounds,
                        supportedActions = listOf(ActionType.TYPE_TEXT, ActionType.CLEAR_TEXT, ActionType.SUBMIT_INPUT),
                        confidence = 1.0,
                        stateSignature = stateSig
                    )
                )
            }

            // 2. Submit Candidate
            val lowerText = "${label ?: ""} ${contentDesc ?: ""} ${viewId ?: ""}".lowercase()
            if (SUBMIT_KEYWORDS.any { lowerText.contains(it) } && (node.isClickable || node.isFocusable)) {
                surfaces.add(
                    InteractionSurface(
                        id = "surface_submit_$index",
                        surfaceType = InteractionSurfaceType.SUBMIT_CANDIDATE,
                        label = displayLabel,
                        contentDescription = contentDesc,
                        resourceId = viewId,
                        className = className,
                        bounds = bounds,
                        supportedActions = listOf(ActionType.SUBMIT_INPUT, ActionType.CLICK_TEXT),
                        confidence = 0.9,
                        stateSignature = stateSig
                    )
                )
            }

            // 3. Navigation Control
            if (NAVIGATION_KEYWORDS.any { lowerText.contains(it) } && node.isClickable) {
                surfaces.add(
                    InteractionSurface(
                        id = "surface_nav_$index",
                        surfaceType = InteractionSurfaceType.NAVIGATION_CONTROL,
                        label = displayLabel,
                        contentDescription = contentDesc,
                        resourceId = viewId,
                        className = className,
                        bounds = bounds,
                        supportedActions = listOf(ActionType.CLICK_TEXT, ActionType.GO_BACK),
                        confidence = 0.9,
                        stateSignature = stateSig
                    )
                )
            }

            // 4. Scrollable Container
            if (node.isScrollable || className?.contains("ScrollView", ignoreCase = true) == true || className?.contains("RecyclerView", ignoreCase = true) == true) {
                surfaces.add(
                    InteractionSurface(
                        id = "surface_scroll_$index",
                        surfaceType = InteractionSurfaceType.SCROLLABLE_CONTAINER,
                        label = displayLabel ?: "Scroll Container",
                        resourceId = viewId,
                        className = className,
                        bounds = bounds,
                        supportedActions = listOf(ActionType.SCROLL_DOWN, ActionType.SCROLL_UP),
                        confidence = 1.0,
                        stateSignature = stateSig
                    )
                )
            }

            // 5. Clickable Button
            if (node.isClickable && !node.isEditable) {
                surfaces.add(
                    InteractionSurface(
                        id = "surface_click_$index",
                        surfaceType = InteractionSurfaceType.CLICKABLE_BUTTON,
                        label = displayLabel,
                        contentDescription = contentDesc,
                        resourceId = viewId,
                        className = className,
                        bounds = bounds,
                        supportedActions = listOf(ActionType.CLICK_TEXT),
                        confidence = 1.0,
                        stateSignature = stateSig
                    )
                )
            }

            // 6. Long Clickable Control
            if (node.isLongClickable) {
                surfaces.add(
                    InteractionSurface(
                        id = "surface_longclick_$index",
                        surfaceType = InteractionSurfaceType.LONG_CLICKABLE_CONTROL,
                        label = displayLabel,
                        contentDescription = contentDesc,
                        resourceId = viewId,
                        className = className,
                        bounds = bounds,
                        supportedActions = listOf(ActionType.LONG_CLICK),
                        confidence = 0.95,
                        stateSignature = stateSig
                    )
                )
            }

            // 7. Toggle / Checkbox
            if (node.isChecked || className?.contains("Switch", ignoreCase = true) == true || className?.contains("CheckBox", ignoreCase = true) == true) {
                surfaces.add(
                    InteractionSurface(
                        id = "surface_toggle_$index",
                        surfaceType = InteractionSurfaceType.TOGGLEABLE_SWITCH,
                        label = displayLabel,
                        contentDescription = contentDesc,
                        resourceId = viewId,
                        className = className,
                        bounds = bounds,
                        supportedActions = listOf(ActionType.CLICK_TEXT),
                        confidence = 1.0,
                        stateSignature = stateSig
                    )
                )
            }
        }

        return surfaces.distinctBy { "${it.surfaceType}_${it.resourceId}_${it.label}_${it.bounds}" }
    }

    /**
     * Generates action candidates from discovered surfaces, applying safety policy classification.
     */
    fun generateCandidates(surfaces: List<InteractionSurface>): List<InteractionCandidate> {
        val candidates = mutableListOf<InteractionCandidate>()

        surfaces.forEach { surface ->
            surface.supportedActions.forEach { action ->
                val labelText = "${surface.label ?: ""} ${surface.contentDescription ?: ""}".lowercase()

                val safetyLevel = when {
                    DESTRUCTIVE_KEYWORDS.any { labelText.contains(it) } -> InteractionSafetyLevel.DESTRUCTIVE_BLOCKED
                    action in listOf(ActionType.SCROLL_DOWN, ActionType.SCROLL_UP, ActionType.GO_BACK, ActionType.READ_VISIBLE_UI) -> InteractionSafetyLevel.SAFE_TO_EXPLORE
                    surface.surfaceType == InteractionSurfaceType.EDITABLE_FIELD && action == ActionType.TYPE_TEXT -> InteractionSafetyLevel.SAFE_TO_EXPLORE
                    surface.surfaceType == InteractionSurfaceType.NAVIGATION_CONTROL -> InteractionSafetyLevel.SAFE_TO_EXPLORE
                    action == ActionType.CLICK_TEXT && surface.surfaceType == InteractionSurfaceType.CLICKABLE_BUTTON -> InteractionSafetyLevel.SUPPORTED
                    else -> InteractionSafetyLevel.OBSERVED
                }

                val description = "Execute $action on ${surface.label ?: surface.surfaceType.name}"

                candidates.add(
                    InteractionCandidate(
                        surface = surface,
                        proposedAction = action,
                        safetyLevel = safetyLevel,
                        description = description
                    )
                )
            }
        }

        return candidates
    }

    private fun parseBounds(boundsStr: String?): TargetBounds? {
        if (boundsStr.isNullOrBlank()) return null
        return try {
            val nums = boundsStr.replace("[^0-9, -]".toRegex(), "")
                .split(" ", ",", "-")
                .mapNotNull { it.trim().toIntOrNull() }

            if (nums.size >= 4) {
                TargetBounds(nums[0], nums[1], nums[2], nums[3])
            } else null
        } catch (e: Throwable) {
            null
        }
    }
}
