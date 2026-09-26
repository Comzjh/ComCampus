package com.xmu.course.ui.tutorial

/**
 * A page-scoped tutorial. Bumping [version] resets the discovery hint for users
 * who already completed the previous version.
 */
data class TutorialDefinition(
    val id: String,
    val version: Int = 1,
    val steps: List<TutorialStep>,
) {
    init {
        require(id.isNotBlank()) { "tutorial id must not be blank" }
        require(version >= 1) { "tutorial version must be >= 1" }
        require(steps.isNotEmpty()) { "tutorial $id must contain at least one step" }
        require(steps.size <= MAX_STEPS) { "tutorial $id exceeds $MAX_STEPS steps" }
        require(
            steps.all {
                (it.targetKey == null || it.targetKey.isNotBlank()) &&
                    it.title.isNotBlank() && it.message.isNotBlank()
            },
        ) { "tutorial $id contains a step with blank target, title or message" }
    }

    val seenKey: String
        get() = "tutorial_seen_${id}_v$version"

    companion object {
        const val MAX_STEPS = 6
    }
}
