package com.xmu.course.domain.grades

/**
 * GPA Sandbox 支持的手动成绩输入。
 *
 * 这只是用户输入，不代表来自学校系统的官方成绩。
 */
sealed interface GradeInput {
    data class Percentage(val value: Double) : GradeInput {
        init {
            require(value.isFinite()) { "百分制成绩必须是有限数值" }
        }
    }

    data class Letter(val value: String) : GradeInput {
        init {
            require(value.isNotBlank()) { "等级成绩不能为空" }
        }
    }
}
