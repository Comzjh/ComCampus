package com.xmu.course

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.domain.Course
import com.xmu.course.domain.CourseSource
import com.xmu.course.domain.Timetable
import com.xmu.course.ui.timetable.TimetableLayoutEngine
import com.xmu.course.ui.timetable.TimetableViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 当前周选择器测试：
 * - actualWeek 计算与 viewWeek 解析（resolveViewWeek 纯函数）；
 * - viewWeek 按课表持久化（TimetablePrefs）；
 * - 课程按查看周过滤（LayoutEngine）。
 *
 * 说明：VM 直连 Room 的 Robolectric 测试存在 "Illegal connection pointer"
 * 线程检查限制，故将切换逻辑提取为可测纯函数后在此覆盖。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeekSelectorTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        TimetablePrefs.setCurrent(context, null)
    }

    @After
    fun teardown() {
        TimetablePrefs.setCurrent(context, null)
    }

    private val timetable = Timetable(id = 1L, name = "A", semesterId = 11L, totalWeeks = 25)

    @Test fun `actualWeek按开学日期计算`() {
        // 开学当天 = 第1周周一；第7天仍在第1周；第8天进入第2周。
        val t = timetable.copy(startDate = "2026-09-14")
        assertEquals(1, TimetableViewModel.computeCurrentWeek(t, LocalDate.parse("2026-09-14")))
        assertEquals(1, TimetableViewModel.computeCurrentWeek(t, LocalDate.parse("2026-09-20")))
        assertEquals(2, TimetableViewModel.computeCurrentWeek(t, LocalDate.parse("2026-09-21")))
        // 开学前回退第1周。
        assertEquals(1, TimetableViewModel.computeCurrentWeek(t, LocalDate.parse("2026-09-01")))
    }

    @Test fun `viewWeek默认等于actualWeek`() {
        // 首次打开（无前课表、无保存值）→ 查看周 = 实际周。
        assertEquals(
            3,
            TimetableViewModel.resolveViewWeek(
                previousTimetableId = null, previousViewWeek = 1,
                timetableId = 1L, savedViewWeek = null,
                actualWeek = 3, totalWeeks = 25,
            ),
        )
    }

    @Test fun `手动切换viewWeek不影响actualWeek`() {
        // 切到第10周：viewWeek 变为 10；actualWeek 由 computeCurrentWeek 独立计算，不写库。
        val saved = 10
        // 同课表再次解析时保持浏览位置，而不是回到 actualWeek。
        assertEquals(
            10,
            TimetableViewModel.resolveViewWeek(
                previousTimetableId = 1L, previousViewWeek = saved,
                timetableId = 1L, savedViewWeek = saved,
                actualWeek = 3, totalWeeks = 25,
            ),
        )
        // actualWeek 计算仅依赖 startDate + 今天。
        val t = timetable.copy(startDate = "2026-09-14")
        assertEquals(2, TimetableViewModel.computeCurrentWeek(t, LocalDate.parse("2026-09-21")))
    }

    @Test fun `同课表保持浏览位置`() {
        assertEquals(
            10,
            TimetableViewModel.resolveViewWeek(
                previousTimetableId = 1L, previousViewWeek = 10,
                timetableId = 1L, savedViewWeek = null,
                actualWeek = 3, totalWeeks = 25,
            ),
        )
    }

    @Test fun `切换课表viewWeek各自独立`() {
        // A 保存第10周；B 未保存 → 切到 B 回到 B 的 actualWeek；切回 A 恢复第10周。
        TimetablePrefs.setViewWeek(context, 1L, 10)
        assertEquals(10, TimetablePrefs.getViewWeek(context, 1L))
        assertEquals(null, TimetablePrefs.getViewWeek(context, 2L))

        // 切到 B：saved=null → actualWeek=1
        assertEquals(
            1,
            TimetableViewModel.resolveViewWeek(
                previousTimetableId = 1L, previousViewWeek = 10,
                timetableId = 2L, savedViewWeek = TimetablePrefs.getViewWeek(context, 2L),
                actualWeek = 1, totalWeeks = 25,
            ),
        )
        // 切回 A：恢复保存的第10周
        assertEquals(
            10,
            TimetableViewModel.resolveViewWeek(
                previousTimetableId = 2L, previousViewWeek = 1,
                timetableId = 1L, savedViewWeek = TimetablePrefs.getViewWeek(context, 1L),
                actualWeek = 3, totalWeeks = 25,
            ),
        )
    }

    @Test fun `viewWeek越界回退到totalWeeks范围`() {
        assertEquals(
            25,
            TimetableViewModel.resolveViewWeek(
                previousTimetableId = null, previousViewWeek = 1,
                timetableId = 1L, savedViewWeek = 99,
                actualWeek = 3, totalWeeks = 25,
            ),
        )
    }

    @Test fun `课程按查看周过滤`() {
        val courses = listOf(
            Course(
                name = "短期课程", dayOfWeek = 1, startSection = 1, duration = 2,
                weeks = setOf(1, 2, 3), source = CourseSource.MANUAL,
            ),
        )
        val week2 = TimetableLayoutEngine.layoutForWeek(courses, 2, onlyCurrentWeek = false)
        assertTrue(week2.getValue(1).single().isCurrentWeek)
        val week5 = TimetableLayoutEngine.layoutForWeek(courses, 5, onlyCurrentWeek = false)
        assertTrue(week5.getValue(1).single().let { !it.isCurrentWeek })
    }
}
