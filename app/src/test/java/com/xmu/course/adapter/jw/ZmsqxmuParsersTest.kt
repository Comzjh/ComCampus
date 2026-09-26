package com.xmu.course.adapter.jw

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** zmsqxmu 只读解析：类型清单白名单取字段、缺主键行跳过、申请列表仅结构校验。全部合成数据。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZmsqxmuParsersTest {

    private fun typesPayload(rowsJson: String): JSONObject =
        JSONObject(
            """{"code":"0","datas":{"dsqzmcx":{"totalSize":15,"rows":$rowsJson}}}""",
        )

    @Test
    fun parsesDocumentedTypeFields() {
        val payload = typesPayload(
            """[{"WID":"T1","ZMWJMC":"示例证明","YWZMWJMC":"Sample Cert","WJYXTS":"7",""" +
                """"SFZJ_DISPLAY":"在籍学生","BKXSLB_DISPLAY":"普通本科","BKXSLB":null,"CZRQ":"2026-01-01"}]""",
        )
        val result = ZmsqxmuParsers.parseCertificateTypes(payload)
        val success = result as ZmsqxmuParsers.TypesResult.Success
        val type = success.types.single()
        assertEquals("T1", type.wid)
        assertEquals("示例证明", type.nameZh)
        assertEquals("Sample Cert", type.nameEn)
        assertEquals("7", type.validityDaysText)
        assertEquals("在籍学生", type.enrollmentRestrictionDisplay)
        assertEquals("普通本科", type.studentCategoryDisplay)
    }

    @Test
    fun rowsWithoutPrimaryKeyAreSkipped() {
        val payload = typesPayload(
            """[{"ZMWJMC":"无主键行"},{"WID":"T2","ZMWJMC":"有效行","YWZMWJMC":null}]""",
        )
        val success = ZmsqxmuParsers.parseCertificateTypes(payload) as ZmsqxmuParsers.TypesResult.Success
        assertEquals(1, success.types.size)
        assertEquals("T2", success.types.single().wid)
        assertEquals(null, success.types.single().nameEn)
    }

    @Test
    fun missingEnvelopeStructureIsRejected() {
        assertTrue(
            ZmsqxmuParsers.parseCertificateTypes(JSONObject("""{"code":"0"}"""))
                is ZmsqxmuParsers.TypesResult.Rejected,
        )
        assertTrue(
            ZmsqxmuParsers.parseApplicationList(JSONObject("""{"datas":{}}"""))
                is ZmsqxmuParsers.ApplicationListResult.Rejected,
        )
    }

    @Test
    fun applicationListOnlyValidatesStructureAndTotalSize() {
        val payload = JSONObject(
            """{"code":"0","datas":{"xszmsqcx":{"totalSize":0,"pageNumber":1,"pageSize":10,"rows":[]}}}""",
        )
        val result = ZmsqxmuParsers.parseApplicationList(payload)
        assertEquals(ZmsqxmuParsers.ApplicationListResult.Success(totalSize = 0), result)
    }
}
