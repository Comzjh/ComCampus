package com.xmu.course.data.academiccompletion

import com.xmu.course.adapter.jw.JwWebViewReadExecutor
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 刷新编排测试：成功导入、失败保缓存、403 分类、结构异常分类。
 *
 * 全部合成数据；假页面按端点路径返回固定信封。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JwAcademicRefreshCoordinatorTest {

    private class FakeJwPage(
        val origin: String = "https://jw.xmu.edu.cn",
        val ready: Boolean = true,
        /** 前 N 次就绪探测返回未就绪：复刻 xywccx SPA 冷启动竞态。 */
        val notReadyProbes: Int = 0,
        /** 路径关键字 → (httpStatus, body) */
        val responses: Map<String, Pair<Int, String>>,
        val defaultStatus: Int = 200,
    ) {
        private var currentPath: String? = null
        var readyProbeCount = 0
        val requestedPaths = mutableListOf<String>()

        suspend fun evaluate(script: String): String? = when {
            script.contains("location.origin") -> """{"origin":"$origin"}"""
            script.contains("started: true") -> {
                currentPath = responses.keys.firstOrNull { script.contains(it) }
                requestedPaths += currentPath ?: "<unknown>"
                """{"started":true}"""
            }
            script.contains("BH_UTILS") -> {
                val probe = readyProbeCount++
                """{"ready":${ready && probe >= notReadyProbes}}"""
            }
            script.contains("delete window") -> """{"cleared":true}"""
            script.contains("state:") -> {
                val (status, body) = responses[currentPath ?: ""] ?: (defaultStatus to "")
                """{"state":"done","status":$status,"body":${JSONObject.quote(body)}}"""
            }
            else -> null
        }
    }

    private fun envelope(action: String, rowsJson: String): String =
        """{"code":"0","datas":{"$action":{"totalSize":1,"rows":$rowsJson}}}"""

    private fun successResponses(): Map<String, Pair<Int, String>> = mapOf(
        "cxdqxnxq" to (200 to envelope("cxdqxnxq", """[{"DM":"2026-2027-1"}]""")),
        "grpyfacx" to (200 to envelope("grpyfacx", """[{"PYFADM":"PLAN01","PYFAMC":"主修方案","XDLXDM":"01","ZSYQXF":"148"}]""")),
        "cxxsscfa" to (200 to envelope("cxxsscfa", """[{"PYFADM":"PLAN01","WCXF":"55","YQXF":"148","CZSJ":"2026-09-16 22:09:00"}]""")),
        "cxfakzyxxfgj" to (200 to envelope("cxfakzyxxfgj", """[{"XKXF":"3"}]""")),
        "queryKzkcXsyx" to (200 to envelope("cxscfakzkc_xsyx", """[{"KCH":"C001","KCM":"示例课程A","XF":"3"}]""")),
        "queryXspkjg" to (200 to """{"pkjgList":[{"KCDM":"C001","KCMC":"示例课程A","JSXM":"教师甲","BJMC":"班1"}]}"""),
    )

    private fun newStore(): AcademicCompletionStore =
        AcademicCompletionStore(Files.createTempDirectory("cc-jw-refresh").resolve("snapshot.json").toFile())

    private fun coordinator(
        store: AcademicCompletionStore,
        page: FakeJwPage,
        fetchedAtEpochMillis: Long = 1_800_000_000_123L,
    ) =
        JwAcademicRefreshCoordinator(
            executor = JwWebViewReadExecutor(evaluate = { page.evaluate(it) }, await = {}),
            store = store,
            todayProvider = { "2026-09-20" },
            epochMillisProvider = { fetchedAtEpochMillis },
        )

    @Test
    fun successfulRefreshImportsSnapshot() = runTest {
        val store = newStore()
        val page = FakeJwPage(responses = successResponses())
        val outcome = coordinator(store, page).refresh()
        assertEquals(
            AcademicRefreshOutcome.Success(courseCount = 1, pendingManualCount = 0),
            outcome,
        )
        val state = store.current()
        assertTrue(state is AcademicCompletionStore.State.Loaded)
        assertEquals("示例课程A", (state as AcademicCompletionStore.State.Loaded).snapshot.enrolledCourses.single().courseName)
        assertEquals(1_800_000_000_123L, state.snapshot.fetchedAtEpochMillis)
    }

    @Test
    fun authRequired403KeepsPreviousCacheIntact() = runTest {
        val store = newStore()
        // 先成功导入一次
        coordinator(store, FakeJwPage(responses = successResponses())).refresh()
        val before = (store.current() as AcademicCompletionStore.State.Loaded).snapshot
        // 再刷新时 xywccx 返回 403
        val failing = successResponses().toMutableMap().apply { put("cxxsscfa", 403 to "") }
        val outcome = coordinator(store, FakeJwPage(responses = failing)).refresh()
        assertEquals(AcademicRefreshOutcome.AuthRequired, outcome)
        val after = (store.current() as AcademicCompletionStore.State.Loaded).snapshot
        assertEquals(before, after)
    }

    @Test
    fun untrustedOriginShortCircuitsBeforeAnyEndpointCall() = runTest {
        val store = newStore()
        val page = FakeJwPage(origin = "https://ids.xmu.edu.cn", responses = successResponses())
        val outcome = coordinator(store, page).refresh()
        assertEquals(AcademicRefreshOutcome.AuthRequired, outcome)
        assertTrue("no data endpoints may be called before auth", page.requestedPaths.isEmpty())
    }

    @Test
    fun notReadyPageYieldsPageNotReady() = runTest {
        val store = newStore()
        val page = FakeJwPage(ready = false, responses = successResponses())
        val outcome = coordinator(store, page).refresh()
        assertEquals(AcademicRefreshOutcome.PageNotReady, outcome)
        assertTrue("未就绪时不得发起任何取数", page.requestedPaths.isEmpty())
        assertTrue("未就绪时本地缓存必须保持为空", store.current() is AcademicCompletionStore.State.Empty)
    }

    /**
     * 真机竞态回归（2026-09-23）：autoRefresh 落在 onPageFinished 时，
     * 官方 xywccx SPA 尚未定义 window.BH_UTILS（约 4 秒后才就绪）。
     * 有界等待必须等到就绪后照常完成 7 次只读取数并导入快照。
     */
    @Test
    fun coldSpaBecomingReadyAfterRaceStillImportsSnapshot() = runTest {
        val store = newStore()
        val page = FakeJwPage(notReadyProbes = 5, responses = successResponses())
        val outcome = coordinator(store, page).refresh()
        assertEquals(
            AcademicRefreshOutcome.Success(courseCount = 1, pendingManualCount = 0),
            outcome,
        )
        assertEquals("就绪探测必须有界重试", 6, page.readyProbeCount)
        assertEquals("7 个白名单只读端点全部取数", 7, page.requestedPaths.size)
        assertTrue(store.current() is AcademicCompletionStore.State.Loaded)
    }

    @Test
    fun server504ClassifiesAsServerUnavailable() = runTest {
        val store = newStore()
        val failing = successResponses().toMutableMap().apply { put("queryKzkcXsyx", 504 to "Gateway Time-out") }
        val outcome = coordinator(store, FakeJwPage(responses = failing)).refresh()
        assertEquals(AcademicRefreshOutcome.ServerUnavailable, outcome)
        assertTrue(store.current() is AcademicCompletionStore.State.Empty)
    }

    @Test
    fun reconciliationMismatchIsRejectedAndCachePreserved() = runTest {
        val store = newStore()
        coordinator(store, FakeJwPage(responses = successResponses())).refresh()
        val before = (store.current() as AcademicCompletionStore.State.Loaded).snapshot
        // XKXF=99 与课程求和不一致 → 解析器整单拒绝
        val skewed = successResponses().toMutableMap().apply {
            put("cxfakzyxxfgj", 200 to envelope("cxfakzyxxfgj", """[{"XKXF":"99"}]"""))
        }
        val outcome = coordinator(store, FakeJwPage(responses = skewed)).refresh()
        assertTrue(outcome is AcademicRefreshOutcome.RejectedByValidation)
        val diagnostic = (outcome as AcademicRefreshOutcome.RejectedByValidation).diagnostics
        assertEquals(AcademicRefreshDiagnosticCode.PLAN_CREDIT_TOTAL_MISMATCH, diagnostic?.code)
        assertEquals(AcademicRefreshDiagnosticStage.SNAPSHOT_VALIDATION, diagnostic?.stage)
        assertEquals("3", diagnostic?.reconciliation?.inPlanCreditsSum)
        assertEquals("99", diagnostic?.reconciliation?.planLevelCredits)
        assertEquals(false, diagnostic?.reconciliation?.reconciledWithPlanLevel)
        val after = (store.current() as AcademicCompletionStore.State.Loaded).snapshot
        assertEquals(before, after)
    }

    @Test
    fun malformedHtmlResponseIsClassified() = runTest {
        val store = newStore()
        val broken = successResponses().toMutableMap().apply { put("cxdqxnxq", 200 to "<html>redirect</html>") }
        val outcome = coordinator(store, FakeJwPage(responses = broken)).refresh()
        assertEquals(AcademicRefreshOutcome.MalformedResponse, outcome)
    }
}
