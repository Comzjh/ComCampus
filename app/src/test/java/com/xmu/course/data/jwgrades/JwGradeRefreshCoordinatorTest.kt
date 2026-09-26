package com.xmu.course.data.jwgrades

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
 * cjcx 刷新编排测试：成功替换、403 双分类、失败保缓存、探测短路。
 * 全部合成数据；假页面按路径关键字返回固定响应。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JwGradeRefreshCoordinatorTest {

    private class FakeJwPage(
        val origin: String = "https://jw.xmu.edu.cn",
        val status: Int = 200,
        val body: String,
    ) {
        val requestedPaths = mutableListOf<String>()

        suspend fun evaluate(script: String): String? = when {
            script.contains("location.origin") -> """{"origin":"$origin"}"""
            script.contains("started: true") -> {
                requestedPaths += "xscjcx"
                """{"started":true}"""
            }
            script.contains("delete window") -> """{"cleared":true}"""
            script.contains("state:") ->
                """{"state":"done","status":$status,"body":${JSONObject.quote(body)}}"""
            else -> null
        }
    }

    private fun gradesOk(): String = envelope(
        totalSize = 2,
        rows = JSONArray()
            .put(
                JSONObject()
                    .put("XNXQDM", "2025-2026-2")
                    .put("XNXQDM_DISPLAY", "2025-2026学年第二学期")
                    .put("KCH", "C001")
                    .put("KCM", "示例课程A")
                    .put("XF", 3)
                    .put("ZCJ", 87)
                    .put("XFJD", "3.7")
                    .put("WID", "W1"),
            )
            .put(
                JSONObject()
                    .put("XNXQDM", "2025-2026-1")
                    .put("KCH", "C002")
                    .put("KCM", "示例课程B")
                    .put("XF", 2)
                    .put("ZCJ", "合格")
                    .put("XFJD", "N/A")
                    .put("WID", "W2"),
            ),
    ).toString()

    private fun gradesTruncated(): String = envelope(
        totalSize = 99,
        rows = JSONArray().put(
            JSONObject()
                .put("XNXQDM", "2025-2026-1")
                .put("KCH", "C001")
                .put("KCM", "示例课程A")
                .put("XF", 3)
                .put("ZCJ", 87)
                .put("WID", "W1"),
        ),
    ).toString()

    private fun envelope(totalSize: Int, rows: JSONArray): JSONObject = JSONObject()
        .put("code", "0")
        .put(
            "datas",
            JSONObject().put(
                "xscjcx",
                JSONObject()
                    .put("totalSize", totalSize)
                    .put("rows", rows),
            ),
        )

    private fun newStore(): JwGradeStore = JwGradeStore(
        Files.createTempDirectory("cc-jw-grade-refresh").resolve("cjcx_grades.json").toFile(),
    )

    private fun coordinator(store: JwGradeStore, page: FakeJwPage) = JwGradeRefreshCoordinator(
        executor = JwWebViewReadExecutor(evaluate = { page.evaluate(it) }, await = {}),
        store = store,
        nowEpochMillis = { 1_770_000_000_000L },
    )

    @Test
    fun successfulRefreshReplacesCacheWithProvenance() = runTest {
        val store = newStore()
        val page = FakeJwPage(body = gradesOk())
        val outcome = coordinator(store, page).refresh()
        assertEquals(GradeRefreshOutcome.Success(entryCount = 2, semesterCount = 2), outcome)
        val state = store.current() as JwGradeStore.State.Loaded
        assertEquals("xmu.jw", state.snapshot.providerId)
        assertEquals("cjcx.xscjcx", state.snapshot.sourceCapability)
        assertEquals(1_770_000_000_000L, state.snapshot.refreshedAtEpochMillis)
        assertEquals("5", state.snapshot.totalCreditsText)
    }

    @Test
    fun untrustedOriginShortCircuitsBeforeEndpointCall() = runTest {
        val store = newStore()
        val page = FakeJwPage(origin = "https://cas.xmu.edu.cn", body = "")
        val outcome = coordinator(store, page).refresh()
        assertEquals(GradeRefreshOutcome.AuthRequired, outcome)
        assertTrue("登录前不得调用任何数据端点", page.requestedPaths.isEmpty())
        assertTrue(store.current() is JwGradeStore.State.Empty)
    }

    @Test
    fun trustedPageHttp403IsClassifiedAsRoleContextRequired() = runTest {
        val store = newStore()
        coordinator(store, FakeJwPage(body = gradesOk())).refresh()
        val before = (store.current() as JwGradeStore.State.Loaded).snapshot
        val outcome = coordinator(store, FakeJwPage(status = 403, body = "")).refresh()
        assertEquals(GradeRefreshOutcome.RoleContextRequired, outcome)
        val after = (store.current() as JwGradeStore.State.Loaded).snapshot
        assertEquals(before, after)
    }

    @Test
    fun server504KeepsPreviousCache() = runTest {
        val store = newStore()
        coordinator(store, FakeJwPage(body = gradesOk())).refresh()
        val outcome = coordinator(store, FakeJwPage(status = 504, body = "Gateway Time-out")).refresh()
        assertEquals(GradeRefreshOutcome.ServerUnavailable, outcome)
        assertTrue(store.current() is JwGradeStore.State.Loaded)
    }

    @Test
    fun malformedHtmlKeepsPreviousCache() = runTest {
        val store = newStore()
        coordinator(store, FakeJwPage(body = gradesOk())).refresh()
        val outcome = coordinator(store, FakeJwPage(body = "<html>redirect</html>")).refresh()
        assertTrue(outcome is GradeRefreshOutcome.MalformedResponse)
        assertTrue(store.current() is JwGradeStore.State.Loaded)
    }

    @Test
    fun truncatedPageKeepsPreviousCache() = runTest {
        val store = newStore()
        coordinator(store, FakeJwPage(body = gradesOk())).refresh()
        val outcome = coordinator(store, FakeJwPage(body = gradesTruncated())).refresh()
        assertTrue(outcome is GradeRefreshOutcome.MalformedResponse)
        assertTrue(store.current() is JwGradeStore.State.Loaded)
    }
}
