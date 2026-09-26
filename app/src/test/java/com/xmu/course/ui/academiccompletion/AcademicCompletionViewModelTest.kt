package com.xmu.course.ui.academiccompletion

import com.xmu.course.data.academiccompletion.AcademicCompletionStore
import com.xmu.course.data.academiccompletion.LocalAcademicSnapshotParser
import com.xmu.course.data.academiccompletion.SnapshotImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 培养方案页 ViewModel 状态机测试（合成数据）。
 *
 * 锁定：快照唯一写入通道是官方刷新，ViewModel 不提供任何文件导入通道；
 * 学分确认对话框取消/非法输入都不产生本地覆盖；
 * 本地覆盖只写入 overrides，来源事实与官方合计口径永不被修改。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AcademicCompletionViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var ioDispatcher: TestDispatcher

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        ioDispatcher = UnconfinedTestDispatcher(mainDispatcher.scheduler)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val validJson = """
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
          "totals": {"in_plan_xf_sum": 3}
        }
    """.trimIndent()

    private fun storeWithSnapshot(): Pair<AcademicCompletionStore, java.io.File> {
        val file = java.io.File(tempFolder.root, "snapshot.json")
        val store = AcademicCompletionStore(file)
        when (val result = LocalAcademicSnapshotParser.parse(validJson)) {
            is SnapshotImportResult.Success -> assertTrue(store.import(result.snapshot))
            is SnapshotImportResult.Rejected -> throw AssertionError("合成快照应可解析: ${result.reasons}")
        }
        return store to file
    }

    private fun viewModel(store: AcademicCompletionStore) =
        AcademicCompletionViewModel(store, ioDispatcher)

    private fun AcademicCompletionViewModel.snapshot() =
        (uiState.value.storeState as AcademicCompletionStore.State.Loaded).snapshot

    @Test
    fun officialSnapshotIsDisplayedWithoutMutation() {
        val (store, _) = storeWithSnapshot()
        val viewModel = viewModel(store)
        val snapshot = viewModel.snapshot()
        assertEquals("合成方案", snapshot.plan.planName)
        assertEquals("3", snapshot.plan.sourceThisSemesterTotalText)
        assertTrue(snapshot.localOverrides.isEmpty())
        assertNull(snapshot.enrolledCourses.first { it.courseCode == "C002" }.creditsText)
    }

    @Test
    fun creditDialogCancelWritesNothing() {
        val (store, _) = storeWithSnapshot()
        val viewModel = viewModel(store)
        val course = viewModel.snapshot().enrolledCourses.first { it.courseCode == "C002" }
        viewModel.openCreditDialog(course)
        viewModel.onCreditInputChange("7")
        viewModel.dismissCreditDialog()
        assertNull(viewModel.uiState.value.creditDialogCourse)
        assertTrue(viewModel.snapshot().localOverrides.isEmpty())
        assertEquals(
            course.status,
            (store.current() as AcademicCompletionStore.State.Loaded)
                .snapshot.enrolledCourses.first { it.courseCode == "C002" }.status,
        )
    }

    @Test
    fun invalidCreditInputShowsErrorAndWritesNothing() {
        val (store, _) = storeWithSnapshot()
        val viewModel = viewModel(store)
        val course = viewModel.snapshot().enrolledCourses.first { it.courseCode == "C002" }
        viewModel.openCreditDialog(course)
        viewModel.onCreditInputChange("abc")
        viewModel.submitCreditDialog()
        assertEquals("请输入合法的十进制学分，例如 2 或 0.25", viewModel.uiState.value.creditErrorMessage)
        assertTrue(viewModel.snapshot().localOverrides.isEmpty())
        viewModel.onCreditInputChange("11")
        viewModel.submitCreditDialog()
        assertEquals("单门课程学分不能超过 10", viewModel.uiState.value.creditErrorMessage)
        assertTrue(viewModel.snapshot().localOverrides.isEmpty())
        assertNull(viewModel.snapshot().enrolledCourses.first { it.courseCode == "C002" }.creditsText)
        assertEquals("3", viewModel.snapshot().plan.sourceThisSemesterTotalText)
    }

    @Test
    fun validCreditInputStoredAsOverrideWithoutTouchingSource() {
        val (store, _) = storeWithSnapshot()
        val viewModel = viewModel(store)
        val course = viewModel.snapshot().enrolledCourses.first { it.courseCode == "C002" }
        viewModel.openCreditDialog(course)
        viewModel.onCreditInputChange("2.0")
        viewModel.submitCreditDialog()
        assertNull(viewModel.uiState.value.creditDialogCourse)
        val snapshot = viewModel.snapshot()
        assertEquals("2", snapshot.localOverrides.getValue("C002").creditsText)
        assertNull(snapshot.enrolledCourses.first { it.courseCode == "C002" }.creditsText)
        assertEquals("3", snapshot.plan.sourceThisSemesterTotalText)
        assertEquals("5", snapshot.combinedTotalIncludingLocalText())
    }

    @Test
    fun creditDialogWithoutSnapshotStillWritesNothing() {
        val file = java.io.File(tempFolder.root, "empty.json")
        val store = AcademicCompletionStore(file)
        val viewModel = viewModel(store)
        val synthetic = LocalAcademicSnapshotParser.parse(validJson)
        val course = (synthetic as SnapshotImportResult.Success)
            .snapshot.enrolledCourses.first { it.courseCode == "C002" }
        viewModel.openCreditDialog(course)
        viewModel.onCreditInputChange("2")
        viewModel.submitCreditDialog()
        assertTrue(store.current() is AcademicCompletionStore.State.Empty)
        assertEquals("保存失败，本机数据保持不变", viewModel.uiState.value.creditErrorMessage)
    }

    @Test
    fun rejectedSnapshotNeverReachesStore() {
        val file = java.io.File(tempFolder.root, "rejected.json")
        val store = AcademicCompletionStore(file)
        val broken = validJson.replace(": 3}", ": 9}")
        assertTrue(LocalAcademicSnapshotParser.parse(broken) is SnapshotImportResult.Rejected)
        assertTrue(store.current() is AcademicCompletionStore.State.Empty)
        assertTrue(file.length() == 0L)
    }

    @Test
    fun clearLocalDataRemovesSnapshotAndOverrides() {
        val (store, _) = storeWithSnapshot()
        val viewModel = viewModel(store)
        val course = viewModel.snapshot().enrolledCourses.first { it.courseCode == "C002" }
        viewModel.openCreditDialog(course)
        viewModel.onCreditInputChange("2")
        viewModel.submitCreditDialog()
        viewModel.clearLocalData()
        assertEquals("已删除本机的学业快照与本地确认", viewModel.uiState.value.notice)
        assertTrue(store.current() is AcademicCompletionStore.State.Empty)
        viewModel.consumeNotice()
        assertNull(viewModel.uiState.value.notice)
    }
}