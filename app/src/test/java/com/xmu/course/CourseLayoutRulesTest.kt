package com.xmu.course

import com.xmu.course.domain.Course
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import com.xmu.course.domain.occursInWeek
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.ui.timetable.TimetableLayoutEngine
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase A 布局正确性回归测试（规范 Test 1-6）。
 *
 * 核心原则：周次决定存在性；节次决定纵向位置和高度；时间重叠只决定横向分栏。
 */
class CourseLayoutRulesTest {

    private fun course(
        day: Int, start: Int, duration: Int,
        name: String = "C$day-$start",
        weeks: Set<Int> = (1..16).toSet(),
    ) = Course(name = name, dayOfWeek = day, startSection = start, duration = duration, weeks = weeks)

    @Test fun `Test1_四节连堂高度不受其他课程影响`() {
        // 周一 1-4 节，另有一门下午 5-6 节课（不同时段）
        val layout = TimetableLayoutEngine.layoutForWeek(
            listOf(course(1, 1, 4, "四节连堂"), course(1, 5, 2, "下午课")),
            week = 1,
        )
        val items = layout.getValue(1)
        val long = items.first { it.course.name == "四节连堂" }
        // duration=4：渲染高度 = CELL_HEIGHT * 4（纵向只由节次决定，未被压缩）
        assertEquals(4, long.course.duration)
        assertEquals(1, long.laneCount)
        // 4节卡片纵向覆盖 1..4 节：offset=(start-1)*CELL，height=duration*CELL
        assertEquals(0, (long.course.startSection - 1))
        assertTrue(long.course.startSection + long.course.duration - 1 == 4)
    }

    @Test fun `Test2_单双周同位置互不影响布局`() {
        val a = course(1, 1, 4, "A单周", weeks = listOf(1, 3, 5, 7, 9, 11, 13, 15).toSet())
        val b = course(1, 1, 4, "B双周", weeks = listOf(2, 4, 6, 8, 10, 12, 14, 16).toSet())

        // 第1周：只有 A，全宽单栏，4节高度
        val week1 = TimetableLayoutEngine.layoutForWeek(listOf(a, b), week = 1)
        val items1 = week1.getValue(1).filter { it.isCurrentWeek }
        assertEquals(1, items1.size)
        assertEquals(1, items1[0].laneCount)
        assertEquals(4, items1[0].course.duration)
        assertEquals("A单周", items1[0].course.name)

        // 第2周：只有 B
        val week2 = TimetableLayoutEngine.layoutForWeek(listOf(a, b), week = 2)
        val items2 = week2.getValue(1).filter { it.isCurrentWeek }
        assertEquals(1, items2.size)
        assertEquals(1, items2[0].laneCount)
        assertEquals(4, items2[0].course.duration)
        assertEquals("B双周", items2[0].course.name)
    }

    @Test fun `Test3_同周同位置两门课分栏且高度完整`() {
        val a = course(1, 1, 4, "A")
        val b = course(1, 1, 4, "B")
        val layout = TimetableLayoutEngine.layoutForWeek(listOf(a, b), week = 1)
        val items = layout.getValue(1).filter { it.isCurrentWeek }
        // 两门都存在，各占约一半宽度（laneCount=2），高度都是 4 节
        assertEquals(2, items.size)
        assertTrue(items.all { it.laneCount == 2 })
        assertEquals(setOf(0, 1), items.map { it.laneIndex }.toSet())
        assertTrue(items.all { it.course.duration == 4 })
    }

    @Test fun `Test4_部分时间重叠横向避让且高度各自保持`() {
        // A：1-4 节，B：3-4 节，同周同天 → 重叠分栏，但 A 仍覆盖 1-4、B 仍覆盖 3-4
        val a = course(1, 1, 4, "A")
        val b = course(1, 3, 2, "B")
        val layout = TimetableLayoutEngine.layoutForWeek(listOf(a, b), week = 1)
        val items = layout.getValue(1).filter { it.isCurrentWeek }
        assertEquals(2, items.size)
        val itemA = items.first { it.course.name == "A" }
        val itemB = items.first { it.course.name == "B" }
        assertEquals(4, itemA.course.duration)
        assertEquals(2, itemB.course.duration)
        assertTrue(itemA.laneCount == 2 && itemB.laneCount == 2)
        assertEquals(0, itemA.laneIndex)
        assertEquals(1, itemB.laneIndex)
    }

    @Test fun `Test5_同时间三门课程全部显示`() {
        val courses = listOf(
            course(3, 3, 2, "A"),
            course(3, 3, 2, "B"),
            course(3, 3, 2, "C"),
        )
        val layout = TimetableLayoutEngine.layoutForWeek(courses, week = 1)
        val items = layout.getValue(3).filter { it.isCurrentWeek }
        assertEquals(3, items.size)
        assertTrue(items.all { it.laneCount == 3 })
        assertEquals(setOf(0, 1, 2), items.map { it.laneIndex }.toSet())
    }

    @Test fun `occursInWeek统一周次判断`() {
        val c = course(1, 1, 2, weeks = setOf(1, 3, 5))
        assertTrue(c.occursInWeek(1))
        assertTrue(!c.occursInWeek(2))
        assertTrue(c.occursInWeek(3))
    }

    @Test fun `ghost课程不参与本周collision`() {
        // 本周 A(1-2)；非本周 B(1-2)、C(3-4)
        val a = course(1, 1, 2, "A")
        val b = course(1, 1, 2, "B", weeks = setOf(2))
        val c = course(1, 3, 2, "C", weeks = setOf(2))
        val layout = TimetableLayoutEngine.layoutForWeek(listOf(a, b, c), week = 1, onlyCurrentWeek = false)
        val dayItems = layout.getValue(1)
        val activeA = dayItems.first { it.isCurrentWeek }
        // A 是本周唯一 active → 全宽
        assertEquals(1, activeA.laneCount)
        // ghost 独立布局：B/C 分栏，但绝不影响 A 的宽度
        val ghosts = dayItems.filter { !it.isCurrentWeek }
        assertEquals(2, ghosts.size)
        // B(1-2) 与 C(3-4) 互不重叠 → 各自独立冲突组，各占全宽
        assertTrue(ghosts.all { it.laneCount == 1 })
    }
}

/**
 * Phase A Test 6：同位置两门课程在 Room 层必须同时保存与返回（Robolectric）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SameSlotRoomTest {

    @Test fun `Test6_Room同位置两门课程都保存并返回`() = kotlinx.coroutines.test.runTest {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val courseRepo = com.xmu.course.data.CourseRepository(db)
            val semester = com.xmu.course.domain.Semester(code = "20261", name = "测试学期")
            val two = listOf(
                Course(name = "课程A", teacher = "甲", location = "A101", dayOfWeek = 3, startSection = 3, duration = 2, weeks = (1..16).toSet()),
                Course(name = "课程B", teacher = "乙", location = "B202", dayOfWeek = 3, startSection = 3, duration = 2, weeks = (1..16).toSet()),
            )
            val r = courseRepo.commitImport(semester, two, overwrite = false) as com.xmu.course.data.ImportResult.Success
            val courses = courseRepo.observeCourses(r.semesterId).first()
            assertEquals(2, courses.size)
            assertEquals(setOf("课程A", "课程B"), courses.map { it.name }.toSet())
        } finally {
            db.close()
        }
    }
}
