package com.xmu.course

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.CourseRepository
import com.xmu.course.data.ImportPreparation
import com.xmu.course.data.ImportResult
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.data.TimetableRepository
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.domain.Course
import com.xmu.course.domain.CourseSource
import com.xmu.course.domain.TimetableConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 多课表仓库测试（Robolectric 内存库）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimetableRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var courseRepo: CourseRepository
    private lateinit var repo: TimetableRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        courseRepo = CourseRepository(db)
        repo = TimetableRepository(db)
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

    @Test fun `创建多课表并统计课程数`() = runTest {
        val t1 = repo.createTimetable("2026秋季学期", startDate = "2026-09-14")
        val t2 = repo.createTimetable("自定义课表")

        val all = repo.observeTimetables().first()
        assertEquals(2, all.size)
        assertTrue(all.all { it.courseCount == 0 })
        // createTimetable 建的都是自定义课表（隐藏学期）
        assertTrue(all.all { it.isCustom })

        // 向 t1 加两门课，课程计数与学期隔离
        courseRepo.addCourse(
            t1.semesterId,
            Course(name = "手动课程", dayOfWeek = 1, startSection = 1, duration = 2, weeks = setOf(1), source = CourseSource.MANUAL),
        )
        courseRepo.addCourse(
            t1.semesterId,
            Course(name = "手动课程2", dayOfWeek = 2, startSection = 3, duration = 2, weeks = setOf(2), source = CourseSource.MANUAL),
        )
        val after = repo.observeTimetables().first()
        assertEquals(2, after.first { it.timetable.id == t1.id }.courseCount)
        assertEquals(0, after.first { it.timetable.id == t2.id }.courseCount)
    }

    @Test fun `导入后绑定课表且幂等`() = runTest {
        val html = javaClass.getResourceAsStream("/sample_timetable.html")!!
            .readBytes().toString(Charsets.UTF_8)
        val prep = courseRepo.prepareImport(html) as ImportPreparation.Ready
        val r = courseRepo.commitImport(prep.semester, prep.courses, overwrite = false) as ImportResult.Success

        // 首次绑定 + 幂等
        val t1 = repo.ensureForSemester(r.semesterId, prep.semester.name, prep.semester.startDate)
        val t2 = repo.ensureForSemester(r.semesterId, prep.semester.name, null)
        assertEquals(t1.id, t2.id)

        val list = repo.observeTimetables().first()
        assertEquals(1, list.size)
        // 导入课表不新建学期
        assertEquals(1, db.semesterDao().getAll().size)
        assertFalse(list[0].isCustom)
        assertEquals(38, list[0].courseCount)
        assertTrue(repo.observeCoursesFor(r.semesterId).size == 38)
    }

    @Test fun `多课表切换后课程数据正确`() = runTest {
        // 课表 A：导入学期（38 门）
        val html = javaClass.getResourceAsStream("/sample_timetable.html")!!
            .readBytes().toString(Charsets.UTF_8)
        val prep = courseRepo.prepareImport(html) as ImportPreparation.Ready
        val r = courseRepo.commitImport(prep.semester, prep.courses, overwrite = false) as ImportResult.Success
        val imported = repo.ensureForSemester(r.semesterId, prep.semester.name, prep.semester.startDate)

        // 课表 B：自定义（1 门手动课）
        val custom = repo.createTimetable("自定义")
        courseRepo.addCourse(
            custom.semesterId,
            Course(name = "手动课程", dayOfWeek = 1, startSection = 1, duration = 2, weeks = setOf(1), source = CourseSource.MANUAL),
        )

        // 通过 semesterId 读取各课表课程，模拟切换数据源
        assertEquals(38, courseRepo.observeCourses(imported.semesterId).first().size)
        assertEquals(1, courseRepo.observeCourses(custom.semesterId).first().size)
    }

    @Test fun `编辑与配置更新`() = runTest {
        val t = repo.createTimetable("旧名字")

        repo.rename(t.id, "新名字")
        repo.updateCurrentWeek(t.id, 3)
        repo.setColor(t.id, "#7FD4E0")

        val updated = repo.getTimetable(t.id)!!
        assertEquals("新名字", updated.name)
        assertEquals(3, updated.currentWeek)
        assertEquals("#7FD4E0", updated.color)

        // 配置默认值 → 修改 → Flow 更新
        assertEquals(false, repo.getConfig(t.id).showSaturday)
        repo.saveConfig(repo.getConfig(t.id).copy(showSaturday = false, courseHeight = 64))
        assertEquals(false, repo.observeConfig(t.id).first().showSaturday)
        assertEquals(64, repo.observeConfig(t.id).first().courseHeight)
    }

    @Test fun `删除自定义课表级联删除学期与课程`() = runTest {
        val t = repo.createTimetable("自定义课表")
        courseRepo.addCourse(
            t.semesterId,
            Course(name = "手动课程", dayOfWeek = 1, startSection = 1, duration = 2, weeks = setOf(1), source = CourseSource.MANUAL),
        )

        repo.deleteTimetable(t.id)

        assertTrue(repo.observeTimetables().first().isEmpty())
        assertTrue(courseRepo.observeCourses(t.semesterId).first().isEmpty())
        assertEquals(0, db.semesterDao().getAll().size)
    }

    @Test fun `删除导入课表保留学期与课程`() = runTest {
        val html = javaClass.getResourceAsStream("/sample_timetable.html")!!
            .readBytes().toString(Charsets.UTF_8)
        val prep = courseRepo.prepareImport(html) as ImportPreparation.Ready
        val r = courseRepo.commitImport(prep.semester, prep.courses, overwrite = false) as ImportResult.Success
        val imported = repo.ensureForSemester(r.semesterId, prep.semester.name, prep.semester.startDate)

        repo.deleteTimetable(imported.id)

        assertTrue(repo.observeTimetables().first().isEmpty())
        // 学期与课程保留
        assertEquals(38, courseRepo.observeCourses(r.semesterId).first().size)
        assertTrue(courseRepo.getSemesters().isNotEmpty())
    }

    @Test fun `TimetablePrefs保存与清除`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNull(TimetablePrefs.currentTimetableId.value)

        TimetablePrefs.setCurrent(context, 42L)
        assertEquals(42L, TimetablePrefs.currentTimetableId.value)

        // 新 load 模拟重启
        TimetablePrefs.load(context)
        assertEquals(42L, TimetablePrefs.currentTimetableId.value)

        TimetablePrefs.setCurrent(context, null)
        assertNull(TimetablePrefs.currentTimetableId.value)
        assertFalse(context.getSharedPreferences("timetable_prefs", Context.MODE_PRIVATE)
            .contains("current_timetable_id"))
    }

    private suspend fun TimetableRepository.observeCoursesFor(semesterId: Long) =
        courseRepo.observeCourses(semesterId).first()
}
