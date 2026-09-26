package com.xmu.course

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.CourseRepository
import com.xmu.course.data.TimetableRepository
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.domain.Course
import com.xmu.course.domain.CourseSource
import com.xmu.course.ui.timetable.TimetableLayoutEngine
import com.xmu.course.ui.timetable.courseCardRenderAlpha
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SkipCourseTest {

    private lateinit var db: AppDatabase
    private lateinit var courseRepo: CourseRepository
    private lateinit var timetableRepo: TimetableRepository
    private var timetableId: Long = 0L

    @Before
    fun setup() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        courseRepo = CourseRepository(db)
        timetableRepo = TimetableRepository(db)
        val timetable = timetableRepo.createTimetable("翘课测试")
        timetableId = timetable.id
        courseRepo.addCourse(
            timetable.semesterId,
            Course(
                name = "大学物理实验",
                teacher = "张老师",
                location = "海韵教学楼104",
                dayOfWeek = 2,
                startSection = 1,
                duration = 4,
                weeks = (1..16).toSet(),
                source = CourseSource.MANUAL,
            ),
        )
        courseRepo.addCourse(
            timetable.semesterId,
            Course(
                name = "高等数学",
                teacher = "李老师",
                location = "翔安A203",
                dayOfWeek = 3,
                startSection = 5,
                duration = 2,
                weeks = (1..16).toSet(),
                source = CourseSource.MANUAL,
            ),
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    private suspend fun courses(): List<Course> =
        courseRepo.observeCoursesByTimetable(timetableId).first()

    @Test
    fun skipCourseSaveTest() = runTest {
        val course = courses().first { it.name == "大学物理实验" }

        courseRepo.saveSkippedCourses(setOf(course.id))

        assertEquals(setOf(course.id), courseRepo.observeSkippedCourseIds().first())
    }

    @Test
    fun skipCourseMultiSelectTest() = runTest {
        val ids = courses().map { it.id }.toSet()

        courseRepo.saveSkippedCourses(ids)

        assertEquals(ids, courseRepo.observeSkippedCourseIds().first())
    }

    @Test
    fun skipCourseCancelTest() = runTest {
        val course = courses().first { it.name == "大学物理实验" }
        courseRepo.markCourseSkipped(course.id)
        assertEquals(setOf(course.id), courseRepo.observeSkippedCourseIds().first())

        courseRepo.unmarkCourseSkipped(course.id)

        assertTrue(courseRepo.observeSkippedCourseIds().first().isEmpty())
    }

    @Test
    fun skipCourseNoLayoutEffectTest() = runTest {
        val allCourses = courses()
        val before = TimetableLayoutEngine.layoutForWeek(allCourses, week = 1)

        courseRepo.saveSkippedCourses(allCourses.map { it.id })

        val after = TimetableLayoutEngine.layoutForWeek(courses(), week = 1)
        assertEquals(before, after)
    }

    @Test
    fun skipCourseRenderAlphaTest() {
        val skippedAlpha = courseCardRenderAlpha(isCurrentWeek = true, configAlpha = 1f, isSkipped = true)
        val normalAlpha = courseCardRenderAlpha(isCurrentWeek = true, configAlpha = 1f, isSkipped = false)
        assertTrue(skippedAlpha in 0.35f..0.5f)
        assertEquals(1f, normalAlpha)
    }
}
