package com.xmu.course.di

import com.xmu.course.contracts.grades.TranscriptReader
import com.xmu.course.data.academic.AcademicGpaPolicyStore
import com.xmu.course.data.academiccompletion.AcademicCompletionStore
import com.xmu.course.data.academicrecord.AcademicRecordRepository
import com.xmu.course.data.academicrecord.AcademicRecordSandboxProvider
import com.xmu.course.data.jwgrades.JwGradeStore
import com.xmu.course.data.local.AcademicRecordDao
import com.xmu.course.data.local.AcademicRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GradesDependenciesTest {
    @Test
    fun exposesAcademicRecordProviderWithoutExposingRoomTypes() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val provider = AcademicRecordSandboxProvider(
            AcademicRecordRepository(EmptyAcademicRecordDao()),
        )
        val transcriptReader = object : TranscriptReader {
            override suspend fun getTranscript() = null
        }

        val dependencies = GradesDependencies(
            transcriptReader = transcriptReader,
            academicRecordSandboxProvider = provider,
            academicCompletionStore = AcademicCompletionStore(File(context.cacheDir, "deps-plan-${System.nanoTime()}.json")),
            jwGradeStore = JwGradeStore(File(context.cacheDir, "deps-grades-${System.nanoTime()}.json")),
            academicGpaPolicyStore = AcademicGpaPolicyStore(context),
        )

        assertSame(transcriptReader, dependencies.transcriptReader)
        assertSame(provider, dependencies.academicRecordSandboxProvider)
    }
}

private class EmptyAcademicRecordDao : AcademicRecordDao {
    override suspend fun insert(record: AcademicRecordEntity): Long = 1L

    override suspend fun countAll(): Int = 0

    override suspend fun getAll(): List<AcademicRecordEntity> = emptyList()

    override fun observeAll(): Flow<List<AcademicRecordEntity>> = emptyFlow()

    override suspend fun deleteAll() = Unit
}
