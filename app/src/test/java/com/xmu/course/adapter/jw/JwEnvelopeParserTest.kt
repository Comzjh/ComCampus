package com.xmu.course.adapter.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** EMAP 信封解析：合成数据，不含任何真实学生记录。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JwEnvelopeParserTest {

    @Test
    fun successEnvelopeWithRowsIsOk() {
        val body = """{"code":"0","datas":{"xscjcx":{"totalSize":2,"rows":[{"KCM":"示例课程A"},{"KCM":"示例课程B"}]}}}"""
        val result = JwEnvelopeParser.parse(200, body)
        assertTrue(result is JwEnvelopeResult.Ok)
        val rows = (result as JwEnvelopeResult.Ok).payload
            .getJSONObject("datas").getJSONObject("xscjcx").getJSONArray("rows")
        assertEquals(2, rows.length())
    }

    @Test
    fun successEnvelopeWithEmptyRowsIsOk() {
        val result = JwEnvelopeParser.parse(200, """{"code":"0","datas":{"xszmsqcx":{"totalSize":0,"rows":[]}}}""")
        assertTrue(result is JwEnvelopeResult.Ok)
    }

    @Test
    fun businessErrorCodeIsClassifiedWithTruncatedMessage() {
        val long = "x".repeat(500)
        val result = JwEnvelopeParser.parse(200, """{"code":"-1","msg":"$long"}""")
        assertTrue(result is JwEnvelopeResult.BusinessError)
        val error = result as JwEnvelopeResult.BusinessError
        assertEquals("-1", error.code)
        assertTrue((error.message?.length ?: 0) <= 120)
    }

    @Test
    fun http403IsHttpFailure() {
        assertTrue(JwEnvelopeParser.parse(403, "") is JwEnvelopeResult.HttpFailure)
        assertEquals(403, (JwEnvelopeParser.parse(403, "") as JwEnvelopeResult.HttpFailure).status)
    }

    @Test
    fun http504IsHttpFailure() {
        assertEquals(504, (JwEnvelopeParser.parse(504, "Gateway Time-out") as JwEnvelopeResult.HttpFailure).status)
    }

    @Test
    fun networkAbortIsNetworkFailure() {
        assertTrue(JwEnvelopeParser.parse(0, "") is JwEnvelopeResult.NetworkFailure)
    }

    @Test
    fun malformedJsonIsMalformed() {
        assertTrue(JwEnvelopeParser.parse(200, "<html>login redirect</html>") is JwEnvelopeResult.Malformed)
        assertTrue(JwEnvelopeParser.parse(200, """{"unexpected":true}""") is JwEnvelopeResult.Malformed)
        assertTrue(JwEnvelopeParser.parse(200, """{"code":"0"}""") is JwEnvelopeResult.Malformed)
    }

    @Test
    fun oversizedBodyIsRejected() {
        val huge = "a".repeat(JwEnvelopeParser.MAX_PAYLOAD_CHARS + 1)
        assertTrue(JwEnvelopeParser.parse(200, huge) is JwEnvelopeResult.TooLarge)
    }

    @Test
    fun plainJsonEnvelopeAcceptsAnyValidJsonObject() {
        val ok = JwEnvelopeParser.parse(
            200,
            """{"pkjgList":[{"kch":"A0000001"}]}""",
            JwResponseEnvelope.PLAIN_JSON,
        )
        assertTrue(ok is JwEnvelopeResult.Ok)
        // 无 code/datas 的合法 JSON 在 PLAIN_JSON 模式下不再被判 Malformed。
        assertTrue(
            JwEnvelopeParser.parse(200, """{"unexpected":true}""", JwResponseEnvelope.PLAIN_JSON)
                is JwEnvelopeResult.Ok,
        )
    }

    @Test
    fun plainJsonEnvelopeStillRejectsNonJsonAndHttpFailures() {
        assertTrue(
            JwEnvelopeParser.parse(200, "<html>login redirect</html>", JwResponseEnvelope.PLAIN_JSON)
                is JwEnvelopeResult.Malformed,
        )
        assertTrue(
            JwEnvelopeParser.parse(403, "", JwResponseEnvelope.PLAIN_JSON) is JwEnvelopeResult.HttpFailure,
        )
        assertTrue(
            JwEnvelopeParser.parse(0, "", JwResponseEnvelope.PLAIN_JSON) is JwEnvelopeResult.NetworkFailure,
        )
    }
}
