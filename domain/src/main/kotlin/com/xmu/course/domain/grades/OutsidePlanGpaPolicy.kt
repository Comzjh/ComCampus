package com.xmu.course.domain.grades

/**
 * 方案外课程是否参与本地派生 GPA 的三态策略（由用户明确裁决）。
 *
 * 只影响 LOCAL DERIVED GPA：不修改官方成绩、官方学分、官方课程状态或培养方案归属，
 * 也绝不写回学校系统。学校方案归属可能延迟或异常，默认要求用户确认，不替用户做决定。
 */
enum class OutsidePlanGpaPolicy {
    /** 未确认：存在方案外 GPA 候选课程时，本地 GPA 不出数。 */
    UNCONFIRMED,

    /** 用户确认：方案外课程计入本地 GPA。 */
    INCLUDE_OUTSIDE_PLAN,

    /** 用户确认：方案外课程不计入本地 GPA（官方记录仍完整展示）。 */
    EXCLUDE_OUTSIDE_PLAN,
}
