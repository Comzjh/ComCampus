package com.xmu.course.data.transcript

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.contracts.academicrecord.AcademicRecord
import com.xmu.course.contracts.academicrecord.AcademicRecordSource
import com.xmu.course.contracts.grades.TranscriptSource
import com.xmu.course.data.academicrecord.AcademicRecordRepository
import com.xmu.course.data.local.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AcademicRecordTranscriptReaderTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: AcademicRecordRepository
    private lateinit var reader: AcademicRecordTranscriptReader

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AcademicRecordRepository(db.academicRecordDao())
        reader = AcademicRecordTranscriptReader(repository)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `empty records returns null unavailable`() = runTest {
        assertNull(reader.getTranscript())
    }

    @Test
    fun `records map to snapshot with academic import source`() = runTest {
        repository.save(AcademicRecord("数学", "3.0", AcademicRecordSource.JW_REPORT))
        repository.save(AcademicRecord("物理", "4.0", AcademicRecordSource.MANUAL_IMPORT))

        val snapshot = reader.getTranscript()

        assertTrue(snapshot != null)
        assertEquals("导入成绩记录", snapshot!!.semester.name)
        assertNull(snapshot.semester.code)
        assertEquals(TranscriptSource.ACADEMIC_IMPORT, snapshot.source)
        assertEquals(2, snapshot.courses.size)
    }

    @Test
    fun `courses carry no inferred score or grade`() = runTest {
        repository.save(AcademicRecord("数学", "3.0", AcademicRecordSource.JW_REPORT))

        val snapshot = reader.getTranscript()

        val course = snapshot!!.courses.single()
        assertEquals("数学", course.courseName)
        assertEquals("3.0", course.creditsText)
        assertEquals(TranscriptSource.ACADEMIC_IMPORT, course.source)
        assertNull(course.score)
        assertNull(course.gradeText)
        assertNull(course.term)
        assertNull(course.credits)
    }
}
