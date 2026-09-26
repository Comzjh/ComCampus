package com.xmu.course.data.privacy

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.local.AcademicRecordEntity
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.data.local.CourseEntity
import com.xmu.course.data.local.SemesterEntity
import com.xmu.course.data.local.SkippedCourseEntity
import com.xmu.course.data.local.TimetableConfigEntity
import com.xmu.course.data.local.TimetableEntity
import com.xmu.course.data.todo.model.TodoEntity
import com.xmu.course.data.tronclass.assignment.TronTodoSyncMetadataEntity
import com.xmu.course.data.tronclass.model.TronCourseEntity
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
 * Privacy 删除安全测试（Phase 5.3.2-C3）。
 *
 * 不变量：
 * - 删除 ≠ 同步 / ≠ 网络 / ≠ 登录 / ≠ 认证清理（实现层仅 Room DELETE）；
 * - 单次只删一个 DataOwner；
 * - TronClass 清理必须保留 source=LOCAL 的用户待办；
 * - Wisedu 清理不影响任何 Todo；Academic 清理不影响课表与 Todo。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivacyDeleteTest {

    private lateinit var db: AppDatabase
    private var selectionResetCount: Int = 0

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        selectionResetCount = 0
    }

    @After
    fun teardown() {
        db.close()
    }

    private suspend fun seedWisedu() {
        val semesterId = db.semesterDao().insert(SemesterEntity(code = "20261", name = "2026 春"))
        db.courseDao().insert(
            CourseEntity(
                semesterId = semesterId,
                name = "高等数学",
                teacher = "老师",
                location = "教室",
                dayOfWeek = 1,
                startSection = 1,
                duration = 2,
                weeks = "1-16",
                source = "IMPORT",
                color = "",
                note = "",
            ),
        )
        val timetableId = db.timetableDao().insert(TimetableEntity(name = "课表", semesterId = semesterId))
        db.timetableDao().upsertConfig(TimetableConfigEntity(timetableId = timetableId))
        db.skippedCourseDao().insert(SkippedCourseEntity(courseId = 1L, createdAt = 0L))
    }

    private suspend fun seedTodos() {
        val now = 0L
        db.todoDao().insert(
            TodoEntity(title = "本地待办A", description = "", courseId = null, source = "LOCAL", deadline = null, createdTime = now, updatedTime = now),
        )
        db.todoDao().insert(
            TodoEntity(title = "本地待办B", description = "", courseId = null, source = "LOCAL", deadline = null, createdTime = now, updatedTime = now),
        )
        db.todoDao().insert(
            TodoEntity(title = "畅课作业", description = "", courseId = null, source = "TRONCLASS", deadline = null, createdTime = now, updatedTime = now, externalId = "hw-1"),
        )
        db.todoDao().insert(
            TodoEntity(title = "畅课作业2", description = "", courseId = null, source = "TRONCLASS", deadline = null, createdTime = now, updatedTime = now, externalId = "hw-2"),
        )
    }

    @Test fun `wisedu 清理删除五类数据且 Todo 完整保留`() = runTest {
        seedWisedu()
        seedTodos()
        db.tronCourseDao().insertAll(
            listOf(TronCourseEntity(tronCourseId = 1L, name = "课", semester = "2026", instructor = "", updatedTime = 0L)),
        )
        assertEquals(5, WiseduTimetableDataOwner(db).countLocalData())

        val owner = WiseduTimetableDataOwner(db) { selectionResetCount++ }
        owner.clearLocalData()

        assertEquals(0, db.semesterDao().countAll())
        assertEquals(0, db.courseDao().countAll())
        assertEquals(0, db.timetableDao().countAll())
        assertEquals(0, db.timetableDao().countConfigs())
        assertEquals(0, db.skippedCourseDao().countAll())
        assertEquals(0, WiseduTimetableDataOwner(db).countLocalData())

        // Todo（LOCAL 与 TRONCLASS）均不受 Wisedu 清理影响
        val todos = db.todoDao().getAll()
        assertEquals(4, todos.size)
        assertEquals(2, todos.count { it.source == "LOCAL" })
        assertEquals(2, todos.count { it.source == "TRONCLASS" })
        // TronClass 数据不受影响
        assertEquals(1, db.tronCourseDao().countAll())
        // 悬空当前课表引用被重置一次
        assertEquals(1, selectionResetCount)
    }

    @Test fun `tronclass 清理保留 LOCAL 待办`() = runTest {
        seedTodos()
        db.tronTodoSyncMetadataDao().insertIfAbsent(TronTodoSyncMetadataEntity(importBaselineAt = 0L))
        db.tronCourseDao().insertAll(
            listOf(TronCourseEntity(tronCourseId = 1L, name = "课", semester = "2026", instructor = "", updatedTime = 0L)),
        )

        TronClassDataOwner(db).clearLocalData()

        val todos = db.todoDao().getAll()
        assertTrue("LOCAL Todo 必须保留", todos.all { it.source == "LOCAL" })
        assertEquals(2, todos.size)
        assertEquals("本地待办A", todos[0].title)
        assertEquals(0, db.todoDao().countBySource("TRONCLASS"))
        assertEquals(0, db.tronTodoSyncMetadataDao().countAll())
        assertEquals(0, db.tronCourseDao().countAll())
        assertEquals(0, TronClassDataOwner(db).countLocalData())
    }

    @Test fun `academic 清理不影响课表与 Todo`() = runTest {
        seedWisedu()
        seedTodos()
        db.academicRecordDao().insert(AcademicRecordEntity(name = "课程", creditsText = "3", source = "PDF"))
        db.academicRecordDao().insert(AcademicRecordEntity(name = "课程2", creditsText = "2", source = "XLSX"))

        AcademicImportDataOwner(db).clearLocalData()

        assertEquals(0, db.academicRecordDao().countAll())
        assertEquals(0, AcademicImportDataOwner(db).countLocalData())
        // 其他 Provider 数据保留
        assertEquals(5, WiseduTimetableDataOwner(db).countLocalData())
        val todos = db.todoDao().getAll()
        assertEquals(4, todos.size)
    }
}
