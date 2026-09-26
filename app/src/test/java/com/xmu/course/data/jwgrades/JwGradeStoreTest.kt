package com.xmu.course.data.jwgrades

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** cjcx 成绩缓存：编解码往返、原子替换、损坏文件不 crash、清空。全部合成数据。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JwGradeStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newFile() = File(tempFolder.newFolder(), "cjcx_grades.json")

    private fun snapshot(
        credits: String = "7.5",
        entries: List<JwGradeEntry> = listOf(
            JwGradeEntry(
                rowId = "W1",
                semesterCode = "2025-2026-2",
                semesterDisplay = "2025-2026学年第二学期",
                courseCode = "C001",
                courseName = "示例课程A",
                creditsText = "3",
                gradeText = "87",
                pointGradeText = "3.7",
                courseNatureDisplay = "必修",
                courseCategoryDisplay = null,
                offeringUnitDisplay = "示例单位",
                retakeCode = null,
            ),
        ),
    ) = JwGradeSnapshot(
        refreshedAtEpochMillis = 1_770_000_000_000L,
        providerId = "xmu.jw",
        sourceCapability = "cjcx.xscjcx",
        totalCreditsText = credits,
        entries = entries,
    )

    @Test
    fun codecRoundTripPreservesNullSemantics() {
        val original = snapshot()
        val decoded = JwGradeSnapshotCodec.decode(JwGradeSnapshotCodec.encode(original))
        assertEquals(original, decoded)
        assertEquals(null, decoded.entries.single().courseCategoryDisplay)
        assertEquals(null, decoded.entries.single().retakeCode)
    }

    @Test
    fun replaceThenReloadFromDisk() {
        val file = newFile()
        val store = JwGradeStore(file)
        assertTrue(store.current() is JwGradeStore.State.Empty)
        assertTrue(store.replace(snapshot()))
        val reopened = JwGradeStore(file)
        val state = reopened.current()
        assertTrue(state is JwGradeStore.State.Loaded)
        assertEquals(snapshot(), (state as JwGradeStore.State.Loaded).snapshot)
    }

    @Test
    fun replaceOverwritesEntirePreviousSnapshot() {
        val store = JwGradeStore(newFile())
        store.replace(snapshot(credits = "3"))
        store.replace(snapshot(credits = "7.5", entries = emptyList()))
        val state = store.current() as JwGradeStore.State.Loaded
        assertEquals("7.5", state.snapshot.totalCreditsText)
        assertTrue(state.snapshot.entries.isEmpty())
    }

    @Test
    fun corruptedFileYieldsStorageFailureWithoutCrash() {
        val file = newFile()
        file.parentFile?.mkdirs()
        file.writeText("{not-json")
        val store = JwGradeStore(file)
        assertTrue(store.current() is JwGradeStore.State.StorageFailure)
        // 损坏文件仍可被成功刷新覆盖恢复
        assertTrue(store.replace(snapshot()))
        assertTrue(store.current() is JwGradeStore.State.Loaded)
    }

    @Test
    fun clearAllRemovesFileAndState() {
        val file = newFile()
        val store = JwGradeStore(file)
        store.replace(snapshot())
        val temp = File(file.parentFile, file.name + ".tmp").apply { writeText("stale partial cache") }
        assertTrue(file.exists())
        assertTrue(store.clearAll())
        assertFalse(file.exists())
        assertFalse(temp.exists())
        assertTrue(store.current() is JwGradeStore.State.Empty)
    }

    @Test
    fun failedClearDoesNotPretendTheCacheIsEmpty() {
        val file = newFile()
        val store = JwGradeStore(file)
        assertTrue(store.replace(snapshot()))
        assertTrue(file.delete())
        assertTrue(file.mkdir())
        val blocker = File(file, "keep").apply { writeText("synthetic blocker") }

        assertFalse(store.clearAll())
        assertTrue(store.current() is JwGradeStore.State.StorageFailure)
        assertTrue(blocker.exists())
    }

    @Test
    fun failedPersistCleansTemporaryFile() {
        val file = newFile()
        val store = JwGradeStore(file)
        assertTrue(file.mkdir())
        File(file, "keep").writeText("synthetic blocker")
        val temp = File(file.parentFile, file.name + ".tmp")

        assertFalse(store.replace(snapshot()))
        assertFalse(temp.exists())
        assertTrue(store.current() is JwGradeStore.State.Empty)
    }

    @Test
    fun wrongSchemaVersionIsRejectedOnReplace() {
        val store = JwGradeStore(newFile())
        val bad = snapshot().copy(schemaVersion = JwGradeSnapshot.SCHEMA_VERSION + 1)
        assertFalse(runCatching { store.replace(bad) }.isSuccess)
    }
}
