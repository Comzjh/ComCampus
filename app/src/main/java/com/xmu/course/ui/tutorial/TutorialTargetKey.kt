package com.xmu.course.ui.tutorial

/** Stable anchors used by Modifier.tutorialTarget(key). Naming: page.element. */
object TutorialTargetKey {
    // D4/D6：去掉 ACADEMIC_OVERVIEW（首步改为无目标引导）与 ACADEMIC_HISTORY
    // （历史成绩卡片是条件渲染，不能承担稳定锚点）。
    const val ACADEMIC_SIMULATION_ENTRY = "academic_home.simulation_entry"
    const val ACADEMIC_DATA_SOURCES = "academic_home.data_sources"
    const val SIMULATION_BASELINE = "academic_simulation.baseline"
    const val SIMULATION_SEMESTER = "academic_simulation.semester"
    const val SIMULATION_RESULT = "academic_simulation.result"
    const val SIMULATION_CUSTOM = "academic_simulation.custom"
    const val SIMULATION_TARGET_GPA = "academic_simulation.target_gpa"
    const val PLAN_OVERVIEW = "academic_plan.overview"
    const val PLAN_SEMESTER = "academic_plan.semester"
    const val SOURCES_REFRESH = "academic_data_sources.refresh"
    const val SOURCES_LOCAL = "academic_data_sources.local"
    const val SOURCES_ORIGINAL = "academic_data_sources.original"
    const val TIMETABLE_WEEK_SWITCH = "timetable.week_switch"
    const val TIMETABLE_COURSE_CARD = "timetable.course_card"
    const val TIMETABLE_SETTINGS = "timetable.settings"
    const val TODO_LIST = "todo.list"
    const val TODO_CHECKBOX = "todo.checkbox"
    const val TODO_ADD = "todo.add"
    const val SETTINGS_TIMETABLE = "settings.timetable"
    const val SETTINGS_ACCOUNT = "settings.account"
    const val SETTINGS_APPEARANCE = "settings.appearance"
    const val SETTINGS_ABOUT = "settings.about"
    const val DATA_LIST = "data_management.list"
    const val DATA_DELETE = "data_management.delete"
}
