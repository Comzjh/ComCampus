package com.xmu.course.data.academicrecord

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.contracts.academicrecord.AcademicRecord
import com.xmu.course.contracts.academicrecord.AcademicRecordSource
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.data.local.AcademicRecordEntity
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
class AcademicRecordRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: AcademicRecordRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = AcademicRecordRepository(db.academicRecordDao())
    }

    @After
    fun teardown() = db.close()

    @Test
    fun confirmedRecordsCanBeSavedAndReadWithoutRoomLeakingUpward() = runTest {
        val input = AcademicRecord(
            name = "微积分II-2",
            creditsText = "5",
            source = AcademicRecordSource.JW_REPORT,
        )

        assertEquals(1L, repository.save(input))
        assertEquals(listOf(input), repository.getAll())
        assertEquals(listOf(input), repository.observeAll().first())

        val stored = db.academicRecordDao().getAll().single()
        assertEquals("JW_REPORT", stored.source)
    }

    @Test
    fun unknownStoredSourceDoesNotEnterContractReadModel() = runTest {
        db.academicRecordDao().insert(
            AcademicRecordEntity(
                name = "未知来源课程",
                creditsText = "2",
                source = "UNKNOWN_SOURCE",
            ),
        )

        assertTrue(repository.getAll().isEmpty())
    }
}
