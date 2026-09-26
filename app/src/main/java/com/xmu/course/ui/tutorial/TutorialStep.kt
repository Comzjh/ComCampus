package com.xmu.course.ui.tutorial

/**
 * A single contextual tutorial step.
 *
 * [targetKey] must match a Modifier.tutorialTarget(key) anchor on the page, or be null for an
 * intentional targetless step (D5). A targetless step draws no highlight, triggers no auto
 * scroll and never shows the missing-target fallback copy; a null target is therefore not the
 * same thing as a broken target.
 */
data class TutorialStep(
    val targetKey: String?,
    val title: String,
    val message: String,
)
