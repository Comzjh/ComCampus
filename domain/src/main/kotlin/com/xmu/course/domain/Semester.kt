package com.xmu.course.domain

/**
 * 学期领域模型。
 *
 * [code] 为金智教务学期代码（如 20261 = 2026-2027 秋季学期）；
 * [startDate] 用于第一版教学周计算（Phase 5），解析层暂不提供。
 */
data class Semester(
    val id: Long = 0L,
    val name: String,
    val code: String,
    val startDate: String? = null,
    val endDate: String? = null,
)
