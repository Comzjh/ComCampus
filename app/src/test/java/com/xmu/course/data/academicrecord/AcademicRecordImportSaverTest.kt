package com.xmu.course.data.academicrecord

import com.xmu.course.contracts.academicimport.ConfirmedAcademicCourse
import com.xmu.course.contracts.academicrecord.AcademicRecordSource
import com.xmu.course.data.local.AcademicRecordDao
import com.xmu.course.data.local.AcademicRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AcademicRecordImportSaverTest {
    @Test
    fun savesOnlyConfirmedCourseFields() = runTest {
        val dao = RecordingAcademicRecordDao()
        val saver = AcademicRecordImportSaver(
            repository = AcademicRecordRepository(dao),
            source = AcademicRecordSource.JW_REPORT,
        )

        val savedCount = saver.saveConfirmed(
            listOf(ConfirmedAcademicCourse(name = "微积分II-2", creditText = "5")),
        )

        assertEquals(1, savedCount)
        assertEquals(
            AcademicRecordEntity(
                name = "微积分II-2",
                creditsText = "5",
                source = "JW_REPORT",
            ),
            dao.saved.single(),
        )
    }

    @Test
    fun savesConfirmedBatchAndReturnsSavedCount() = runTest {
        val dao = RecordingAcademicRecordDao()
        val saver = AcademicRecordImportSaver(AcademicRecordRepository(dao))

        val count = saver.saveConfirmed(
            listOf(
                ConfirmedAcademicCourse("课程一", "1"),
                ConfirmedAcademicCourse("课程二", "2"),
            ),
        )

        assertEquals(2, count)
        assertEquals(listOf("课程一", "课程二"), dao.saved.map { it.name })
        assertEquals(listOf("1", "2"), dao.saved.map { it.creditsText })
    }

    @Test
    fun emptyConfirmationDoesNotWriteAnything() = runTest {
        val dao = RecordingAcademicRecordDao()
        val saver = AcademicRecordImportSaver(AcademicRecordRepository(dao))

        assertEquals(0, saver.saveConfirmed(emptyList()))
        assertEquals(emptyList<AcademicRecordEntity>(), dao.saved)
    }
}

private class RecordingAcademicRecordDao : AcademicRecordDao {
    val saved = mutableListOf<AcademicRecordEntity>()

    override suspend fun insert(record: AcademicRecordEntity): Long {
        saved += record
        return saved.size.toLong()
    }

    override suspend fun countAll(): Int = 0

    override suspend fun getAll(): List<AcademicRecordEntity> = saved.toList()

    override fun observeAll(): Flow<List<AcademicRecordEntity>> = emptyFlow()

    override suspend fun deleteAll() = saved.clear()
}
