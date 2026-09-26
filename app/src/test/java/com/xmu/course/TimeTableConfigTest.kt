package com.xmu.course

import com.xmu.course.domain.Timetable
import com.xmu.course.ui.timetable.TimeTableConfig
import com.xmu.course.ui.timetable.TimetableViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TimeTableConfigTest {

    @Test fun `厦大作息为11节`() {
        assertEquals(11, TimeTableConfig.sectionCount)
        assertEquals(11, TimeTableConfig.xmuSections.size)
    }

    @Test fun `节次时间与作息一致`() {
        assertEquals("08:00-08:45", TimeTableConfig.timeOf(1))
        assertEquals("11:05-11:50", TimeTableConfig.timeOf(4))
        assertEquals("14:30-15:15", TimeTableConfig.timeOf(5))
        assertEquals("21:00-21:45", TimeTableConfig.timeOf(11))
        assertEquals("", TimeTableConfig.timeOf(0))
        assertEquals("", TimeTableConfig.timeOf(12))
    }
}

class CurrentWeekTest {

    @Test fun `根据开学日期计算当前周`() {
        val semester = Timetable(name = "test", semesterId = 1L, startDate = "2026-09-14")
        // 开学当天 = 第1周周一
        assertEquals(1, TimetableViewModel.computeCurrentWeek(semester, LocalDate.parse("2026-09-14")))
        // 第7天（周日）仍是第1周
        assertEquals(1, TimetableViewModel.computeCurrentWeek(semester, LocalDate.parse("2026-09-20")))
        // 第8天进入第2周
        assertEquals(2, TimetableViewModel.computeCurrentWeek(semester, LocalDate.parse("2026-09-21")))
    }

    @Test fun `开学前与未设置日期回退第1周`() {
        val semester = Timetable(name = "test", semesterId = 1L, startDate = "2026-09-14")
        assertEquals(1, TimetableViewModel.computeCurrentWeek(semester, LocalDate.parse("2026-09-01")))
        assertEquals(1, TimetableViewModel.computeCurrentWeek(Timetable(name = "n", semesterId = 1L), LocalDate.now()))
        // 非法日期格式
        assertEquals(1, TimetableViewModel.computeCurrentWeek(
            Timetable(name = "n", semesterId = 1L, startDate = "bad"), LocalDate.now(),
        ))
    }
}
