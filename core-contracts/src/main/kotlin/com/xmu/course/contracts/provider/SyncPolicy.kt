package com.xmu.course.contracts.provider

/**
 * Provider 同步策略：把产品规则（哪些数据允许何时同步）代码化。
 *
 * 当前产品映射（仅记录，不迁移现有代码）：
 * - JW：MANUAL_ONLY
 * - TronClass / 课表：保持现有行为，未来再映射到具体策略。
 */
enum class SyncPolicy {
    /** 仅用户主动触发；禁止后台同步。 */
    MANUAL_ONLY,

    /** 允许前台（用户可见）时同步；仍禁止后台。 */
    FOREGROUND_ALLOWED,

    /** 允许后台同步。 */
    BACKGROUND_ALLOWED,
}