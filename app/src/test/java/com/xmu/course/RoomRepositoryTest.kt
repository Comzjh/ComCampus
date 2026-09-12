package com.xmu.course

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.CourseRepository
import com.xmu.course.data.ImportPreparation
import com.xmu.course.data.ImportResult
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.data.local.toDomain
import com.xmu.course.domain.Course
import com.xmu.course.domain.CourseSource
import com.xmu.course.domain.Semester
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

/**
 * Room + Repository 测试（Robolectric 内存库）。
 * 样本 HTML 为 Phase 3 脱敏样张，保证与真实金智页面结构一致。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: CourseRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CourseRepository(db)
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun sampleHtml(): String =
        javaClass.getResourceAsStream("/sample_timetable.html")!!
            .readBytes().toString(Charsets.UTF_8)

    @Test fun `导入-查询课程`() = runTest {
        val prep = repository.prepareImport(sampleHtml()) as ImportPreparation.Ready
        val result = repository.commitImport(prep.semester, prep.courses, overwrite = false)
        assertTrue(result is ImportResult.Success)

        val courses = repository.observeCourses((result as ImportResult.Success).semesterId).first()
        assertEquals(38, courses.size)
        assertTrue(courses.all { it.source == CourseSource.IMPORT })
        assertTrue(courses.all { it.weeks.isNotEmpty() })
    }

    @Test fun `同学期导入触发冲突并覆盖`() = runTest {
        val prep = repository.prepareImport(sampleHtml()) as ImportPreparation.Ready
        repository.commitImport(prep.semester, prep.courses, overwrite = false)

        // 再次导入同一学期：应得到 Conflict，含双方课程数。
        val conflict = repository.prepareImport(sampleHtml()) as ImportPreparation.Conflict
        assertEquals(38, conflict.existingCount)
        assertEquals(38, conflict.newCount)

        // 覆盖导入：旧数据被删除、新数据写入，总数不变。
        val result = repository.commitImport(conflict.semester, conflict.pendingCourses, overwrite = true)
        val courses = repository.observeCourses(conflict.semester.id).first()
        assertEquals(38, courses.size)
    }

    @Test fun `多学期隔离`() = runTest {
        val prep = repository.prepareImport(sampleHtml()) as ImportPreparation.Ready
        val r1 = repository.commitImport(prep.semester, prep.courses, overwrite = false) as ImportResult.Success

        // 手动创建第二学期并插入一门课程。
        val semester2 = Semester(code = "20252", name = "2025-2026学年 春季学期")
        val r2 = repository.commitImport(
            semester2,
            listOf(
                Course(
                    name = "手动课程",
                    dayOfWeek = 1,
                    startSection = 1,
                    duration = 2,
                    weeks = setOf(1, 2, 3),
                    source = CourseSource.MANUAL,
                ),
            ),
            overwrite = false,
        ) as ImportResult.Success

        assertEquals(38, repository.observeCourses(r1.semesterId).first().size)
        assertEquals(1, repository.observeCourses(r2.semesterId).first().size)
        assertEquals(2, repository.getSemesters().size)
    }

    @Test fun `删除学期级联删除课程`() = runTest {
        val prep = repository.prepareImport(sampleHtml()) as ImportPreparation.Ready
        val r = repository.commitImport(prep.semester, prep.courses, overwrite = false) as ImportResult.Success
        repository.deleteSemester(r.semesterId)
        assertEquals(0, repository.observeCourses(r.semesterId).first().size)
        assertTrue(repository.getSemesters().none { it.id == r.semesterId })
    }
}
