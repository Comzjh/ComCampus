package com.xmu.course.data.privacy

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.adapter.jw.XmuJwAdapter
import com.xmu.course.contracts.provider.PrivacyDataOwner
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
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PrivacyDataOwner 测试。
 * - countLocalData：SELECT COUNT 边界；
 * - clearLocalData：由 PrivacyDeleteTest 覆盖真实删除与隔离语义。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivacyDataOwnerTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test fun `wisedu count 覆盖五个课表数据表`() = runTest {
        val semesterId = db.semesterDao().insert(SemesterEntity(code = "20261", name = "2026 春"))
        db.courseDao().insert(
            CourseEntity(
                semesterId = semesterId,
                name = "课程",
                teacher = "",
                location = "",
                dayOfWeek = 1,
                startSection = 1,
                duration = 2,
                weeks = "1",
                source = "IMPORT",
                color = "",
                note = "",
            ),
        )
        val timetableId = db.timetableDao().insert(TimetableEntity(name = "课表", semesterId = semesterId))
        db.timetableDao().upsertConfig(TimetableConfigEntity(timetableId = timetableId))
        db.skippedCourseDao().insert(SkippedCourseEntity(courseId = 1L, createdAt = 0L))

        assertEquals(5, WiseduTimetableDataOwner(db).countLocalData())
    }

    @Test fun `tronclass count 只统计 TRONCLASS 待办`() = runTest {
        val now = 0L
        db.todoDao().insert(
            TodoEntity(title = "本地待办", description = "", courseId = null, source = "LOCAL", deadline = null, createdTime = now, updatedTime = now),
        )
        db.todoDao().insert(
            TodoEntity(title = "畅课作业", description = "", courseId = null, source = "TRONCLASS", deadline = null, createdTime = now, updatedTime = now, externalId = "hw-1"),
        )
        db.todoDao().insert(
            TodoEntity(title = "畅课作业2", description = "", courseId = null, source = "TRONCLASS", deadline = null, createdTime = now, updatedTime = now, externalId = "hw-2"),
        )
        db.tronTodoSyncMetadataDao().insertIfAbsent(TronTodoSyncMetadataEntity(importBaselineAt = now))
        db.tronCourseDao().insertAll(
            listOf(TronCourseEntity(tronCourseId = 1L, name = "课", semester = "2026", instructor = "", updatedTime = now)),
        )

        // LOCAL 待办不计入：2 todo + 1 metadata + 1 course
        assertEquals(4, TronClassDataOwner(db).countLocalData())
    }

    @Test fun `academicimport count 统计学业记录`() = runTest {
        db.academicRecordDao().insert(AcademicRecordEntity(name = "课程", creditsText = "3", source = "PDF"))
        db.academicRecordDao().insert(AcademicRecordEntity(name = "课程2", creditsText = "2", source = "XLSX"))

        assertEquals(2, AcademicImportDataOwner(db).countLocalData())
    }

    @Test fun `JW Adapter 不声明 PrivacyDataOwner`() {
        // 组合根给 JW 传 dataOwner = null；契约上 XmuJwAdapter 也不实现 PrivacyDataOwner。
        assertFalse(PrivacyDataOwner::class.java.isAssignableFrom(XmuJwAdapter::class.java))
    }
}
