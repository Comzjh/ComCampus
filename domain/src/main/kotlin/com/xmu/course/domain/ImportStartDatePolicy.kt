package com.xmu.course.domain

import java.time.DayOfWeek
import java.time.LocalDate

/** 导入课表要求的开学日期规则：第一周的星期一。 */
fun isValidFirstWeekStartDate(date: LocalDate): Boolean = date.dayOfWeek == DayOfWeek.MONDAY
