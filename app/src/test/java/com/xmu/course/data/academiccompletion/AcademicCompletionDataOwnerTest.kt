package com.xmu.course.data.academiccompletion

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 学业快照隐私数据属主的固定测试（合成数据）。
 *
 * 归属：本地文件导入数据，不挂任何 Provider；
 * 契约复用 PrivacyDataOwner，未修改契约。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AcademicCompletionDataOwnerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = """
        {
          "generated_at": "2026-09-19",
          "semester": {"jwapp_code": "2026-2027-1"},
          "plan": {
            "name": "合成方案", "required_xf": 160, "earned_xf_snapshot": 40,
            "snapshot_czsj": "2026-09-16 22:09",
            "this_semester_selected_xf_plan_level": 3
          },
          "courses": [
            {"KCDM":"C001","KCMC":"合成课程甲","XF":3,"in_plan":true,"xf_status":"confirmed"},
            {"KCDM":"C002","KCMC":"合成课程乙","XF":null,"in_plan":false,"xf_status":"需人工确认"}
          ],
          "faw_completed_courses": [
            {"KCH":"X001","KCMC":"合成课外的课","XF":2,"XNXQDM":"2025-2026-2","CJ":88}
          ],
          "totals": {"in_plan_xf_sum": 3}
        }
    """.trimIndent()

    private fun loadedStore(): AcademicCompletionStore {
        val store = AcademicCompletionStore(tempFolder.newFile("snapshot.json"))
        val parsed = LocalAcademicSnapshotParser.parse(json)
        assertTrue(parsed is SnapshotImportResult.Success)
        store.import((parsed as SnapshotImportResult.Success).snapshot)
        return store
    }

    @Test
    fun countCoversCoursesCompletedAndOverrides() = runTest {
        val store = loadedStore()
        store.putLocalCreditOverride("C002", "2")
        val owner = AcademicCompletionDataOwner(store)
        // 2 在修 + 1 方案外已结课 + 1 本地覆盖
        assertEquals(4, owner.countLocalData())
    }

    @Test
    fun clearRemovesEverythingOwnedByThisSource() = runTest {
        val store = loadedStore()
        store.putLocalCreditOverride("C002", "2")
        val owner = AcademicCompletionDataOwner(store)
        owner.clearLocalData()
        assertEquals(0, owner.countLocalData())
        assertTrue(store.current() is AcademicCompletionStore.State.Empty)
    }

    @Test
    fun emptyStoreCountsZero() = runTest {
        val store = AcademicCompletionStore(java.io.File(tempFolder.root, "not-created.json"))
        assertEquals(0, AcademicCompletionDataOwner(store).countLocalData())
    }

    @Test
    fun unreadableSnapshotIsNotReportedAsEmpty() = runTest {
        val file = tempFolder.newFile("corrupt.json").apply { writeText("{not-json") }
        val owner = AcademicCompletionDataOwner(AcademicCompletionStore(file))

        assertFalse(runCatching { owner.countLocalData() }.isSuccess)
    }

    @Test
    fun failedClearIsPropagatedToCaller() = runTest {
        val file = tempFolder.newFile("blocked.json")
        val store = AcademicCompletionStore(file)
        assertTrue(file.delete())
        assertTrue(file.mkdir())
        java.io.File(file, "keep").writeText("synthetic blocker")
        val owner = AcademicCompletionDataOwner(store)

        assertFalse(runCatching { owner.clearLocalData() }.isSuccess)
    }
}
