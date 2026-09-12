package com.xmu.course

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.CourseRepository
import com.xmu.course.data.TimetableRepository
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.domain.Course
import com.xmu.course.domain.CourseSource
import com.xmu.course.ui.widget.WidgetRepository
import com.xmu.course.ui.widget.toChineseDay
import java.time.LocalDate
import java.time.LocalTime
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var courseRepo: CourseRepository
    private lateinit var timetableRepo: TimetableRepository
    private var timetableId: Long = 0L
    private var semesterId: Long = 0L
    private val today: LocalDate = LocalDate.of(2026, 9, 14)

    @Before
    fun setup() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        courseRepo = CourseRepository(db)
        timetableRepo = TimetableRepository(db)
        val timetable = timetableRepo.createTimetable("Widget测试")
        timetableId = timetable.id
        semesterId = timetable.semesterId
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun course(
        name: String,
        day: Int,
        start: Int,
        duration: Int,
        weeks: Set<Int>,
    ) = Course(
        name = name,
        teacher = "老师",
        location = "教室",
        dayOfWeek = day,
        startSection = start,
        duration = duration,
        weeks = weeks,
        source = CourseSource.MANUAL,
    )

    private suspend fun insert(
        name: String,
        day: Int,
        start: Int,
        duration: Int = 2,
        weeks: Set<Int> = (1..16).toSet(),
    ): Long {
        courseRepo.addCourse(semesterId, course(name, day, start, duration, weeks))
        return courseRepo.observeCoursesByTimetable(timetableId).first().first { it.name == name }.id
    }

    @Test
    fun todayWidgetDataTest() = runTest {
        val monday = today.dayOfWeek.value
        insert("大学物理实验", monday, 1)
        insert("程序设计", monday, 5)
        val tuesday = today.plusDays(1).dayOfWeek.value
        insert("明天课", tuesday, 1)

        val data = WidgetRepository.loadToday(
            ApplicationProvider.getApplicationContext(),
            today = today,
            now = LocalTime.of(9, 0),
            db = db,
        )

        assertEquals("Widget测试", data.timetableName)
        assertEquals(1, data.week)
        assertEquals(monday, data.dayOfWeek)
        assertEquals(2, data.courses.size)
        assertEquals("大学物理实验", data.courses.first().name)
        assertEquals("08:00", data.courses.first().startTime)
    }

    @Test
    fun nextCourseWidgetTest() = runTest {
        val monday = today.dayOfWeek.value
        insert("上午课", monday, 1)
        val afternoonId = insert("下午课", monday, 5)

        val data = WidgetRepository.loadToday(
            ApplicationProvider.getApplicationContext(),
            today = today,
            now = LocalTime.of(10, 0),
            db = db,
        )
        val next = WidgetRepository.findNextCourse(data.courses, data.nowMinuteOfDay)

        assertEquals("下午课", next?.name)
        assertEquals(afternoonId, next?.id)
        assertEquals("14:30", next?.startTime)
        assertEquals("还有4小时30分钟", WidgetRepository.countdownText(data.nowMinuteOfDay, next))
    }

    @Test
    fun weekFilterWidgetTest() = runTest {
        val monday = today.dayOfWeek.value
        insert("单周课", monday, 1, weeks = setOf(1, 3, 5))
        insert("双周课", monday, 5, weeks = setOf(2, 4, 6))

        val week1 = WidgetRepository.loadToday(
            ApplicationProvider.getApplicationContext(),
            today = today,
            now = LocalTime.of(8, 0),
            db = db,
        )
        assertEquals(1, week1.week)
        assertEquals(listOf("单周课"), week1.courses.map { it.name })
    }

    @Test
    fun skipCourseWidgetTest() = runTest {
        val monday = today.dayOfWeek.value
        val normalId = insert("普通课", monday, 1)
        val skippedId = insert("翘课课", monday, 5)

        courseRepo.markCourseSkipped(skippedId)
        val data = WidgetRepository.loadToday(
            ApplicationProvider.getApplicationContext(),
            today = today,
            now = LocalTime.of(8, 0),
            db = db,
        )
        val skipped = data.courses.first { it.id == skippedId }
        val normal = data.courses.first { it.id == normalId }
        assertTrue(skipped.isSkipped)
        assertFalse(normal.isSkipped)
    }

    @Test
    fun widgetRefreshTest() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val monday = today.dayOfWeek.value
        val before = WidgetRepository.loadToday(context, today, LocalTime.of(8, 0), db)
        assertTrue(before.courses.isEmpty())

        val id = insert("动态课程", monday, 1)
        val after = WidgetRepository.loadToday(context, today, LocalTime.of(8, 0), db)
        assertEquals(listOf("动态课程"), after.courses.map { it.name })

        courseRepo.deleteCourse(id)
        val removed = WidgetRepository.loadToday(context, today, LocalTime.of(8, 0), db)
        assertTrue(removed.courses.isEmpty())
    }

    @Test
    fun widgetDataTest() = runTest {
        val monday = today.dayOfWeek.value
        insert("数据展示课", monday, 1)
        val data = WidgetRepository.loadToday(ApplicationProvider.getApplicationContext(), today, LocalTime.of(7, 0), db)
        assertEquals("数据展示课", data.courses.single().name)
        assertEquals("教室", data.courses.single().location)
    }

    @Test
    fun todayWidgetTest() = runTest {
        val monday = today.dayOfWeek.value
        insert("今日课程", monday, 5)
        val data = WidgetRepository.loadToday(ApplicationProvider.getApplicationContext(), today, LocalTime.of(7, 0), db)
        assertEquals("星期一", data.dayOfWeek.toChineseDay())
        assertEquals("14:30", data.courses.single().startTime)
    }

    @Test
    fun emptyDayWidgetTest() = runTest {
        val data = WidgetRepository.loadToday(ApplicationProvider.getApplicationContext(), today, LocalTime.of(8, 0), db)
        assertTrue(data.courses.isEmpty())
        assertEquals("今日无课程", WidgetRepository.countdownText(data.nowMinuteOfDay, null))
    }
}
