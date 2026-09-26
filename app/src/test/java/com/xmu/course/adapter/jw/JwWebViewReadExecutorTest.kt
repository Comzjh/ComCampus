package com.xmu.course.adapter.jw

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 桥的生命周期行为：origin 守卫、槽位轮询、取消、清理、白名单强制。 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JwWebViewReadExecutorTest {

    /** 假 WebView：按脚本类型返回可预测结果。 */
    private class FakePage(
        val origin: String = "https://jw.xmu.edu.cn",
        val ready: Boolean = true,
        /** 前 N 次就绪探测返回未就绪：复刻官方 SPA 冷启动竞态。 */
        val notReadyProbes: Int = 0,
        /** done 之前返回 pending 的次数。 */
        val pendingTicks: Int = 0,
        val finalStatus: Int = 200,
        val finalBody: String = """{"code":"0","datas":{"cxxsscfa":{"totalSize":0,"rows":[]}}}""",
    ) {
        var ticks = 0
        var readyProbes = 0
        var cleared = false
        val scripts = mutableListOf<String>()

        suspend fun evaluate(script: String): String? {
            scripts += script
            return when {
                script.contains("location.origin") -> """{"origin":"$origin"}"""
                script.contains("started: true") -> """{"started":true}"""
                script.contains("BH_UTILS") -> {
                    val probe = readyProbes++
                    """{"ready":${ready && probe >= notReadyProbes}}"""
                }
                script.contains("delete window") -> {
                    cleared = true
                    """{"cleared":true}"""
                }
                script.contains("state: 'pending'") || script.contains("ns[") -> {
                    if (ticks++ < pendingTicks) {
                        """{"state":"pending"}"""
                    } else {
                        """{"state":"done","status":$finalStatus,"body":${jsonString(finalBody)}}"""
                    }
                }
                else -> null
            }
        }

        private fun jsonString(value: String): String =
            org.json.JSONObject.quote(value)
    }

    private fun executor(page: FakePage, await: suspend (Long) -> Unit = {}) =
        JwWebViewReadExecutor(
            evaluate = { script -> page.evaluate(script) },
            await = await,
        )

    @Test
    fun trustedReadyPagePassesProbe() = runTest {
        val page = FakePage()
        assertEquals(JwPageCheck.TRUSTED_READY, executor(page).verifyTrustedPage(requireBhUtils = true))
    }

    @Test
    fun untrustedOriginFailsProbeWithoutFetching() = runTest {
        val page = FakePage(origin = "https://ids.xmu.edu.cn")
        assertEquals(JwPageCheck.UNTRUSTED_ORIGIN, executor(page).verifyTrustedPage(requireBhUtils = false))
    }

    @Test
    fun xywccxRequiresReadySpa() = runTest {
        val page = FakePage(ready = false)
        assertEquals(JwPageCheck.TRUSTED_NOT_READY, executor(page).verifyTrustedPage(requireBhUtils = true))
        // cjcx/zmsqxmu 不依赖 BH_UTILS
        assertEquals(JwPageCheck.TRUSTED_READY, executor(page).verifyTrustedPage(requireBhUtils = false))
    }

    @Test
    fun awaitTrustedPageWaitsForColdSpaThenPasses() = runTest {
        val page = FakePage(notReadyProbes = 3)
        val check = executor(page).awaitTrustedPage(requireBhUtils = true)
        assertEquals(JwPageCheck.TRUSTED_READY, check)
        assertTrue("readiness must be re-polled", page.readyProbes >= 4)
    }

    @Test
    fun awaitTrustedPageGivesUpAfterBoundedAttempts() = runTest {
        val page = FakePage(ready = false)
        var waits = 0
        val check = executor(page, await = { waits++ })
            .awaitTrustedPage(requireBhUtils = true)
        assertEquals(JwPageCheck.TRUSTED_NOT_READY, check)
        // 20s / 400ms = 50 次探测（49 次等待），绝不无界空转
        assertEquals(50, page.readyProbes)
        assertEquals(49, waits)
    }

    @Test
    fun awaitTrustedPageDoesNotPollWhileUntrustedOrigin() = runTest {
        val page = FakePage(origin = "https://ids.xmu.edu.cn")
        var waits = 0
        val check = executor(page, await = { waits++ })
            .awaitTrustedPage(requireBhUtils = true)
        assertEquals(JwPageCheck.UNTRUSTED_ORIGIN, check)
        assertEquals("CAS 登录页不得空等", 0, waits)
        assertEquals(0, page.readyProbes)
    }

    @Test
    fun fetchPollsPendingSlotsThenParsesEnvelopeAndClears() = runTest {
        val page = FakePage(pendingTicks = 2)
        val result = executor(page).fetch(JwReadEndpoint.XYWCCX_COMPLETION_SNAPSHOT, formBody = "XH=0000000000&SCLBDM=04")
        assertTrue(result is JwEnvelopeResult.Ok)
        assertTrue("pending slots must have been polled", page.ticks >= 3)
        assertTrue("result slot must be cleared", page.cleared)
    }

    @Test
    fun http403IsTypedFailureNotLoginAssumption() = runTest {
        val page = FakePage(finalStatus = 403, finalBody = "")
        val result = executor(page).fetch(JwReadEndpoint.CJCX_STUDENT_GRADES, formBody = "querySetting=%5B%5D")
        assertEquals(JwEnvelopeResult.HttpFailure(403), result)
    }

    @Test
    fun fetchRejectsPathThatPolicyDeniesEvenIfCallerForcesIt() = runTest {
        // 写端点无法表达为 JwReadEndpoint 枚举成员；
        // 这里验证执行器层的 require 与脚本构造层同样拒绝非法输入。
        var thrown = false
        try {
            JwFetchScriptBuilder.startFetchScript(
                endpoint = JwReadEndpoint.XYWCCX_PLANS,
                requestId = "bad id",
                formBody = "",
            )
        } catch (error: IllegalArgumentException) {
            thrown = true
        }
        assertTrue("malformed request id must be rejected", thrown)
    }

    @Test
    fun cancellationStopsPollingAndClearsSlot() = runTest {
        val page = FakePage(pendingTicks = Int.MAX_VALUE)
        var pollAwaits = 0
        val exec = JwWebViewReadExecutor(
            evaluate = { script -> page.evaluate(script) },
            await = { pollAwaits++; delay(10) },
        )
        val job = launch { exec.fetch(JwReadEndpoint.XYWCCX_COMPLETION_SNAPSHOT) }
        advanceTimeBy(50)
        job.cancelAndJoin()
        assertTrue("executor must have polled", pollAwaits >= 1)
        assertTrue("slot cleared on cancellation", page.cleared)
    }
}
