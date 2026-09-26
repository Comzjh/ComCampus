package com.xmu.course.contracts.presentation

/** 冷启动默认落地页；用户偏好，不是业务逻辑。 */
enum class StartupDestination {
    HOME,
    TIMETABLE,
}

/**
 * 首次启动时用户选择的默认打开页面偏好。
 *
 * 未选择前返回 null（触发一次性选择流程）；一旦选择即持久化，之后不再询问，
 * 仅可在设置中修改。不进 Room。
 */
interface StartupDestinationPreference {
    /** 已选择的启动页；null 表示尚未选择。 */
    fun destination(): StartupDestination?

    fun setDestination(destination: StartupDestination)
}
