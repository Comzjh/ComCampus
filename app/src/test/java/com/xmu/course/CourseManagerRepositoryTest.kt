package com.xmu.course

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.CourseRepository
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.data.TimetableRepository
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.domain.Course
import com.xmu.course.domain.CourseSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 课程管理 Repository 测试：定向字段更新 / 删除 / 手动添加 / 多课表隔离。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CourseManagerRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var courseRepo: CourseRepository
    private lateinit var timetableRepo: TimetableRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        courseRepo = CourseRepository(db)
        timetableRepo = TimetableRepository(db)
        TimetablePrefs.setCurrent(context, null)
    }

    @After
    fun teardown() {
        db.close()
        TimetablePrefs.setCurrent(
            ApplicationProvider.getApplicationContext<Context>(),
            null,
        )
    }

    private suspend fun courseWith(
        timetableId: Long,
        name: String = "高等数学A",
    ): Course {
        val timetable = timetableRepo.getTimetable(timetableId)!!
        val course = Course(
            name = name,
            teacher = "张老师",
            location = "翔安 A203",
            dayOfWeek = 1,
            startSection = 1,
            duration = 2,
            weeks = (1..16).toSet(),
            source = CourseSource.MANUAL,
            note = "重点课程",
        )
        courseRepo.addCourse(timetable.semesterId, course)
        return courseRepo.observeCoursesByTimetable(timetableId).first().first { it.name == name }
    }

    @Test fun `修改课程名称`() = runTest {
        val t = timetableRepo.createTimetable("测试课表")
        val course = courseWith(t.id)

        courseRepo.updateCourseName(course.id, "线性代数")

        val updated = courseRepo.observeCoursesByTimetable(t.id).first().first { it.id == course.id }
        assertEquals("线性代数", updated.name)
        // 其他字段不被覆盖
        assertEquals("张老师", updated.teacher)
        assertEquals("翔安 A203", updated.location)
        assertEquals("重点课程", updated.note)
    }

    @Test fun `修改备注`() = runTest {
        val t = timetableRepo.createTimetable("测试课表")
        val course = courseWith(t.id)

        courseRepo.updateCourseNote(course.id, "期中考前复习")

        val updated = courseRepo.observeCoursesByTimetable(t.id).first().first { it.id == course.id }
        assertEquals("期中考前复习", updated.note)
    }

    @Test fun `修改颜色`() = runTest {
        val t = timetableRepo.createTimetable("测试课表")
        val course = courseWith(t.id)

        courseRepo.updateCourseColor(course.id, "#7FD4E0")

        val updated = courseRepo.observeCoursesByTimetable(t.id).first().first { it.id == course.id }
        assertEquals("#7FD4E0", updated.color)
    }

    @Test fun `删除课程只删Course`() = runTest {
        val t = timetableRepo.createTimetable("测试课表")
        val course = courseWith(t.id)

        courseRepo.deleteCourse(course.id)

        assertTrue(courseRepo.observeCoursesByTimetable(t.id).first().isEmpty())
        // 学期与课表保留
        assertEquals(1, db.semesterDao().getAll().size)
        assertTrue(timetableRepo.getTimetable(t.id) != null)
    }

    @Test fun `添加手动课程并按课表观察`() = runTest {
        val t = timetableRepo.createTimetable("测试课表")

        val timetable = timetableRepo.getTimetable(t.id)!!
        courseRepo.addCourse(
            timetable.semesterId,
            Course(
                name = "大学英语",
                dayOfWeek = 3,
                startSection = 3,
                duration = 2,
                weeks = setOf(1, 3, 5),
                source = CourseSource.MANUAL,
            ),
        )

        val courses = courseRepo.observeCoursesByTimetable(t.id).first()
        assertEquals(1, courses.size)
        assertEquals("大学英语", courses[0].name)
        assertEquals(CourseSource.MANUAL, courses[0].source)

        // 今日课程（2026-09-14 是星期一 → dayOfWeek=1 应无结果；构造星期三课程用周三日期）
        val wednesday = LocalDate.parse("2026-09-16")
        val today = courseRepo.observeTodayCourses(t.id, wednesday).first()
        assertEquals(1, today.size)
        assertEquals(3, today[0].dayOfWeek)
    }

    @Test fun `不同课表课程不互相影响`() = runTest {
        val t1 = timetableRepo.createTimetable("课表A")
        val t2 = timetableRepo.createTimetable("课表B")

        val c1 = courseWith(t1.id, name = "A的课程")
        val c2 = courseWith(t2.id, name = "B的课程")

        // 修改 A 的课程名称
        courseRepo.updateCourseName(c1.id, "A的课程-改名")
        val a = courseRepo.observeCoursesByTimetable(t1.id).first()
        val b = courseRepo.observeCoursesByTimetable(t2.id).first()
        assertEquals("A的课程-改名", a.first().name)
        assertEquals("B的课程", b.first().name)

        // 删除 A 的课程
        courseRepo.deleteCourse(c1.id)
        assertTrue(courseRepo.observeCoursesByTimetable(t1.id).first().isEmpty())
        assertEquals(1, courseRepo.observeCoursesByTimetable(t2.id).first().size)
    }

    @Test fun `weeksLabel格式化`() {
        assertEquals("1-16周", com.xmu.course.ui.course.weeksLabel((1..16).toSet()))
        assertEquals("1-3,5,8-9周", com.xmu.course.ui.course.weeksLabel(setOf(9, 1, 3, 8, 5, 2)))
        assertEquals("6周", com.xmu.course.ui.course.weeksLabel(setOf(6)))
        assertEquals("未设置周次", com.xmu.course.ui.course.weeksLabel(emptySet()))
        assertFalse(com.xmu.course.ui.course.weeksLabel(setOf(1, 2)).contains(","))
    }
}
