package com.xmu.course.data.academiccompletion

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 快照存储的固定测试（合成数据）。
 *
 * 重点锁定三条审查决议：
 * 1. 本地覆盖绝不修改来源事实（RC-1）；
 * 2. 重导入按 exact 课程身份迁移覆盖，来源确认后丢弃覆盖（RC-6/RC-7）；
 * 3. 持久化文件不含姓名/学号（RC-3），损坏时进入失败态而非 crash（原子持久化）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AcademicCompletionStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun json(courses: String, planTotal: String): String = """
        {
          "generated_at": "2026-09-19",
          "student": {"XH": "00000000000000", "XM": "合成姓名"},
          "semester": {"jwapp_code": "2026-2027-1"},
          "plan": {
            "name": "合成方案", "required_xf": 160, "earned_xf_snapshot": 40,
            "snapshot_czsj": "2026-09-16 22:09",
            "this_semester_selected_xf_plan_level": $planTotal
          },
          "courses": $courses,
          "totals": {"in_plan_xf_sum": $planTotal}
        }
    """.trimIndent()

    private val baseCourses = """
        [{"KCDM":"C001","KCMC":"合成课程甲","XF":3,"in_plan":true,"xf_status":"confirmed"},
         {"KCDM":"C002","KCMC":"合成课程乙","XF":null,"in_plan":false,"xf_status":"需人工确认"}]
    """.trimIndent()

    private fun storeWith(json: String): Pair<AcademicCompletionStore, java.io.File> {
        val file = tempFolder.newFile("snapshot.json")
        val store = AcademicCompletionStore(file)
        val parsed = LocalAcademicSnapshotParser.parse(json)
        assertTrue(parsed is SnapshotImportResult.Success)
        assertTrue(store.import((parsed as SnapshotImportResult.Success).snapshot))
        return store to file
    }

    private fun snapshotOf(store: AcademicCompletionStore): AcademicCompletionSnapshot =
        (store.current() as AcademicCompletionStore.State.Loaded).snapshot

    @Test
    fun persistsAndSurvivesRecreation() {
        val (store, file) = storeWith(json(baseCourses, "3"))
        val reopened = AcademicCompletionStore(file)
        val snapshot = snapshotOf(reopened)
        assertEquals(2, snapshot.enrolledCourses.size)
        assertEquals("合成方案", snapshot.plan.planName)
    }

    @Test
    fun persistedFileContainsNoStudentIdentity() {
        val (_, file) = storeWith(json(baseCourses, "3"))
        val raw = file.readText()
        assertFalse("持久化文件不得包含姓名", raw.contains("合成姓名"))
        assertFalse("持久化文件不得包含学号", raw.contains("00000000000000"))
        assertFalse("不得保存原始来源全文", raw.contains("student"))
    }

    @Test
    fun replacementIsSingleSnapshot() {
        val (store, file) = storeWith(json(baseCourses, "3"))
        val second = json(
            """[{"KCDM":"D001","KCMC":"新方案课程","XF":2,"in_plan":true,"xf_status":"confirmed"}]""",
            "2",
        )
        assertTrue(store.import((LocalAcademicSnapshotParser.parse(second) as SnapshotImportResult.Success).snapshot))
        val snapshot = snapshotOf(store)
        assertEquals(listOf("D001"), snapshot.enrolledCourses.map { it.courseCode })
        val reopened = AcademicCompletionStore(file)
        assertEquals(1, snapshotOf(reopened).enrolledCourses.size)
    }

    @Test
    fun corruptedPersistedDataFailsSafely() {
        val file = tempFolder.newFile("corrupt.json")
        file.writeText("{ this is not valid internal snapshot")
        val store = AcademicCompletionStore(file)
        assertTrue(store.current() is AcademicCompletionStore.State.StorageFailure)
        val failure = store.current() as AcademicCompletionStore.State.StorageFailure
        assertTrue(failure.message.isNotEmpty())
    }

    @Test
    fun unsupportedInternalSchemaVersionFailsSafely() {
        val file = tempFolder.newFile("future.json")
        file.writeText("""{"schemaVersion": 999}""")
        val store = AcademicCompletionStore(file)
        assertTrue(store.current() is AcademicCompletionStore.State.StorageFailure)
    }

    @Test
    fun manualCreditNeverMutatesSourceFacts() {
        val (store, _) = storeWith(json(baseCourses, "3"))
        assertTrue(store.putLocalCreditOverride("C002", "2.0"))
        val snapshot = snapshotOf(store)
        val source = snapshot.enrolledCourses.first { it.courseCode == "C002" }
        assertNull("来源学分必须保持 null", source.creditsText)
        assertEquals("来源状态必须保持需人工确认", SourceXfStatus.NEEDS_MANUAL, source.status)
        assertEquals("来源总计不得被本地覆盖改变", "3", snapshot.plan.sourceThisSemesterTotalText)
        assertEquals("本地覆盖值需规范化", "2", snapshot.localOverrides.getValue("C002").creditsText)
        assertEquals("2", snapshot.effectiveCreditsText(source))
        assertEquals("2", snapshot.localSupplementedTotalText())
        assertEquals("5", snapshot.combinedTotalIncludingLocalText())
    }

    @Test
    fun overrideRejectedForNonNeedsManualCourses() {
        val (store, _) = storeWith(json(baseCourses, "3"))
        assertFalse(store.putLocalCreditOverride("C001", "4"))
        assertFalse(store.putLocalCreditOverride("UNKNOWN", "4"))
        assertFalse(store.putLocalCreditOverride("C002", "abc"))
        assertFalse(store.putLocalCreditOverride("C002", "-1"))
        assertFalse(store.putLocalCreditOverride("C002", "0"))
        assertTrue(snapshotOf(store).localOverrides.isEmpty())
    }

    @Test
    fun reImportKeepsOverrideForExactNeedsManualMatch() {
        val (store, _) = storeWith(json(baseCourses, "3"))
        store.putLocalCreditOverride("C002", "2")
        assertTrue(store.import((LocalAcademicSnapshotParser.parse(json(baseCourses, "3")) as SnapshotImportResult.Success).snapshot))
        assertEquals("2", snapshotOf(store).localOverrides.getValue("C002").creditsText)
    }

    @Test
    fun reImportDropsOverrideWhenSourceBecomesConfirmed() {
        val (store, _) = storeWith(json(baseCourses, "3"))
        store.putLocalCreditOverride("C002", "2")
        val updatedCourses = """
            [{"KCDM":"C001","KCMC":"合成课程甲","XF":3,"in_plan":true,"xf_status":"confirmed"},
             {"KCDM":"C002","KCMC":"合成课程乙","XF":2,"in_plan":true,"xf_status":"confirmed"}]
        """.trimIndent()
        assertTrue(store.import((LocalAcademicSnapshotParser.parse(json(updatedCourses, "5")) as SnapshotImportResult.Success).snapshot))
        val snapshot = snapshotOf(store)
        assertTrue("来源确认后必须丢弃本地覆盖", snapshot.localOverrides.isEmpty())
        assertEquals("2", snapshot.enrolledCourses.first { it.courseCode == "C002" }.creditsText)
    }

    @Test
    fun reImportDropsUnmatchedOverridesWithoutFuzzyCarrying() {
        val (store, _) = storeWith(json(baseCourses, "3"))
        store.putLocalCreditOverride("C002", "2")
        val renamed = """
            [{"KCDM":"D999","KCMC":"看起来相似的课","XF":null,"in_plan":false,"xf_status":"需人工确认"}]
        """.trimIndent()
        assertTrue(store.import((LocalAcademicSnapshotParser.parse(json(renamed, "0")) as SnapshotImportResult.Success).snapshot))
        assertTrue("课程代码不精确一致时不得迁移覆盖", snapshotOf(store).localOverrides.isEmpty())
    }

    @Test
    fun clearAllRemovesSnapshotAndOverrides() {
        val (store, file) = storeWith(json(baseCourses, "3"))
        store.putLocalCreditOverride("C002", "2")
        val temp = File(file.parentFile, file.name + ".tmp").apply { writeText("stale partial snapshot") }
        assertTrue(store.clearAll())
        assertTrue(store.current() is AcademicCompletionStore.State.Empty)
        assertFalse("快照文件必须被删除", file.exists())
        assertFalse("失败写入残留的临时文件也必须被删除", temp.exists())
    }

    @Test
    fun failedClearDoesNotPretendTheCacheIsEmpty() {
        val (store, file) = storeWith(json(baseCourses, "3"))
        assertTrue(file.delete())
        assertTrue(file.mkdir())
        val blocker = File(file, "keep").apply { writeText("synthetic blocker") }

        assertFalse(store.clearAll())
        assertTrue(store.current() is AcademicCompletionStore.State.StorageFailure)
        assertTrue(blocker.exists())
    }

    @Test
    fun failedPersistCleansTemporaryFileAndKeepsLastLoadedState() {
        val (store, file) = storeWith(json(baseCourses, "3"))
        val previous = store.current()
        assertTrue(file.delete())
        assertTrue(file.mkdir())
        File(file, "keep").writeText("synthetic blocker")
        val temp = File(file.parentFile, file.name + ".tmp")

        assertFalse(store.import(snapshotOf(store)))
        assertFalse(temp.exists())
        assertEquals(previous, store.current())
    }

    @Test
    fun codecRoundTripPreservesAllLayers() {
        val (store, _) = storeWith(json(baseCourses, "3"))
        store.putLocalCreditOverride("C002", "0.5")
        val original = snapshotOf(store).copy(
            completedCoursesOutsidePlan = listOf(
                CompletedCourseOutsidePlan(
                    courseCode = "DONE001",
                    courseName = "合成方案外课程",
                    creditsText = "2",
                    termCode = "2025-2026-2",
                    scoreText = "88",
                ),
            ),
        )
        val decoded = AcademicCompletionSnapshotCodec.decode(AcademicCompletionSnapshotCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun optionalFetchTimeRoundTripsAndLegacyCacheDecodesWithoutIt() {
        val (store, _) = storeWith(json(baseCourses, "3"))
        val original = snapshotOf(store).copy(fetchedAtEpochMillis = 1_800_000_000_123L)
        val encoded = AcademicCompletionSnapshotCodec.encode(original)

        assertEquals(original, AcademicCompletionSnapshotCodec.decode(encoded))
        val legacy = JSONObject(encoded).apply { remove("fetchedAtEpochMillis") }.toString()
        assertNull(AcademicCompletionSnapshotCodec.decode(legacy).fetchedAtEpochMillis)
    }

    @Test
    fun missingPersistedArraysFailClosedInsteadOfDroppingData() {
        val (store, _) = storeWith(json(baseCourses, "3"))
        assertTrue(store.putLocalCreditOverride("C002", "0.5"))
        val snapshot = snapshotOf(store).copy(
            completedCoursesOutsidePlan = listOf(
                CompletedCourseOutsidePlan("DONE001", "合成课程", "2", "2025-2026-2", "88"),
            ),
        )
        val encoded = AcademicCompletionSnapshotCodec.encode(snapshot)

        val missingCompleted = tempFolder.newFile("missing-completed.json")
        missingCompleted.writeText(JSONObject(encoded).apply { remove("completedOutsidePlan") }.toString())
        val missingOverrides = tempFolder.newFile("missing-overrides.json")
        missingOverrides.writeText(JSONObject(encoded).apply { remove("localOverrides") }.toString())

        assertTrue(AcademicCompletionStore(missingCompleted).current() is AcademicCompletionStore.State.StorageFailure)
        assertTrue(AcademicCompletionStore(missingOverrides).current() is AcademicCompletionStore.State.StorageFailure)
    }
}
