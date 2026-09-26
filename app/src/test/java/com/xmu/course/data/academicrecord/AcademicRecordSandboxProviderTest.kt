package com.xmu.course.data.academicrecord

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.contracts.academicrecord.AcademicRecord
import com.xmu.course.contracts.academicrecord.AcademicRecordSource
import com.xmu.course.data.local.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AcademicRecordSandboxProviderTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: AcademicRecordRepository
    private lateinit var provider: AcademicRecordSandboxProvider

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = AcademicRecordRepository(db.academicRecordDao())
        provider = AcademicRecordSandboxProvider(repository)
    }

    @After
    fun teardown() = db.close()

    @Test
    fun loadsRepositoryRecordsAsEditableDrafts() = runTest {
        repository.save(
            AcademicRecord(
                name = "微积分II-2",
                creditsText = "5",
                source = AcademicRecordSource.JW_REPORT,
            ),
        )

        val drafts = provider.loadDrafts()

        assertEquals(1, drafts.size)
        assertEquals("微积分II-2", drafts.single().name)
        assertEquals("5", drafts.single().credits)
        assertEquals("", drafts.single().grade)
    }

    @Test
    fun emptyRepositoryProducesEmptyDraftList() = runTest {
        assertEquals(emptyList<Any>(), provider.loadDrafts())
    }
}
