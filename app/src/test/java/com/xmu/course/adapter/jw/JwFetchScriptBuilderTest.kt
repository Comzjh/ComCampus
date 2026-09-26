package com.xmu.course.adapter.jw

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** JS 构造器的安全属性：只读、same-origin、无凭据读取、无同步 XHR。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JwFetchScriptBuilderTest {

    @Test
    fun startScriptUsesAsyncFetchWithSessionCredentialsAndAbort() {
        val script = JwFetchScriptBuilder.startFetchScript(
            endpoint = JwReadEndpoint.CJCX_STUDENT_GRADES,
            requestId = "r1",
            formBody = "pageSize=999",
        )
        assertTrue(script.contains("fetch("))
        assertTrue(script.contains("credentials: 'include'"))
        assertTrue(script.contains("AbortController"))
        assertTrue(script.contains("X-Requested-With"))
        // Android JSONObject.quote 会把 '/' 转义为 '\/'（JS 字符串等价）。
        assertTrue(script.contains(JSONObject.quote(JwReadEndpoint.CJCX_STUDENT_GRADES.path)))
        assertFalse("sync XHR is forbidden", script.contains("XMLHttpRequest("))
        assertFalse("must not read cookies", script.contains("document.cookie"))
    }

    @Test
    fun everyRegisteredEndpointCanBuildAndWritePathsCannot() {
        // 白名单内端点全部可构造脚本：签名端点走 doSyncAjax，其余走异步 fetch。
        JwReadEndpoint.values().forEach { endpoint ->
            val script = JwFetchScriptBuilder.startFetchScript(endpoint, "r1", "")
            assertTrue(
                if (endpoint.signed) script.contains("BH_UTILS.doSyncAjax") else script.contains("fetch("),
            )
        }
        // 写端点无法表达为枚举成员，且策略层独立拒绝其路径。
        assertTrue(JwEndpointPolicy.isWriteDenied("/jwapp/sys/xywccx/*default/modules/xywccx/bysc.do"))
        assertFalse(JwEndpointPolicy.isReadAllowed("/jwapp/sys/xywccx/*default/modules/xywccx/bysc.do"))
    }

    @Test
    fun invalidRequestIdIsRejected() {
        var thrown = false
        try {
            JwFetchScriptBuilder.startFetchScript(
                endpoint = JwReadEndpoint.XYWCCX_PLANS,
                requestId = "bad id;alert(1)",
                formBody = "",
            )
        } catch (error: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun signedGetEndpointPassesQueryAsParams() {
        // Phase H 实测修正：签名端点（含 GET 方言）统一经页面 $.ajax 以参数对象 POST 发送。
        val script = JwFetchScriptBuilder.startFetchScript(
            endpoint = JwReadEndpoint.XYWCCX_PLAN_SEMESTER_TOTAL,
            requestId = "r2",
            formBody = "",
            queryForGet = "XH=000000&PYFADM=abc",
        )
        assertTrue(script.contains("BH_UTILS.doSyncAjax"))
        assertTrue(script.contains(JSONObject.quote("XH=000000&PYFADM=abc")))
        assertFalse(script.contains("?XH=000000"))
    }

    @Test
    fun signedEndpointsUsePageSigningChannel() {
        val signed = JwReadEndpoint.values().filter { it.signed }
        assertTrue("xywccx 家族必须登记为签名端点", signed.isNotEmpty())
        signed.forEach { endpoint ->
            val script = JwFetchScriptBuilder.startFetchScript(endpoint, "r1", "a=1")
            assertTrue("签名通道 = 页面内 doSyncAjax 同步返回", script.contains("BH_UTILS.doSyncAjax"))
            assertTrue(script.contains("JSON.stringify(resp)"))
            assertFalse("签名通道不得再走裸 fetch", script.contains("fetch("))
            assertTrue("槽位需同步置 pending", script.contains("state: 'pending'"))
            assertTrue(script.contains(JSONObject.quote(endpoint.path)))
            assertFalse("must not read cookies", script.contains("document.cookie"))
        }
        // cjcx/zmsq 保持无签名异步 fetch（Phase H 实测 cjcx 无签名通道 200 可用）。
        assertFalse(JwReadEndpoint.CJCX_STUDENT_GRADES.signed)
        assertFalse(JwReadEndpoint.ZMSQ_APPLICATION_LIST.signed)
    }

    @Test
    fun signedPostKeepsXhPlaceholderInsidePageContext() {
        val script = JwFetchScriptBuilder.startFetchScript(
            endpoint = JwReadEndpoint.XYWCCX_PLANS,
            requestId = "r3",
            formBody = "XH={{XH}}",
        )
        assertTrue(script.contains("{{XH}}"))
        assertTrue(script.contains("_JW_INIT_CONFIG"))
        assertTrue(script.contains("BH_UTILS.doSyncAjax"))
    }

    @Test
    fun readAndClearScriptsTargetOnlyPrivateNamespace() {
        val read = JwFetchScriptBuilder.readResultScript("r7")
        val clear = JwFetchScriptBuilder.clearResultScript("r7")
        assertTrue(read.contains(JwFetchScriptBuilder.RESULT_NAMESPACE))
        assertTrue(clear.contains(JwFetchScriptBuilder.RESULT_NAMESPACE))
        assertFalse(read.contains("fetch("))
    }
}
