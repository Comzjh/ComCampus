package com.xmu.course.adapter.jw

import org.json.JSONObject

/**
 * zmsqxmu 可申请证明类型清单（dsqzmcx）解析。
 *
 * 仅解析只读展示所需字段；行值全部按官方文本保留（含 null→kotlin null）。
 * 缺 WID 主键的行直接跳过（无稳定标识不入库）。
 */
data class ZmsqCertificateType(
    val wid: String,
    val nameZh: String,
    val nameEn: String?,
    val validityDaysText: String?,
    val enrollmentRestrictionDisplay: String?,
    val studentCategoryDisplay: String?,
)

/**
 * zmsqxmu 我的证明申请记录（xszmsqcx）解析。
 *
 * 该端点在集成包实测中恒为空（totalSize=0，无稳定行结构证据）：
 * 因此当前仅解析信封元数据（totalSize），不对未知行字段做臆测建模；
 * 未来若实测到真实行结构，再按证据补充白名单字段。
 */
object ZmsqxmuParsers {

    sealed interface TypesResult {
        data class Success(val types: List<ZmsqCertificateType>) : TypesResult
        data class Rejected(val reason: String) : TypesResult
    }

    sealed interface ApplicationListResult {
        /** totalSize 为服务端声明的总记录数；行语义待实测证据补全。 */
        data class Success(val totalSize: Int) : ApplicationListResult
        data class Rejected(val reason: String) : ApplicationListResult
    }

    fun parseCertificateTypes(payload: JSONObject): TypesResult {
        val table = payload.optJSONObject("datas")?.optJSONObject("dsqzmcx")
            ?: return TypesResult.Rejected("缺少 datas.dsqzmcx 信封结构")
        val rows = table.optJSONArray("rows")
            ?: return TypesResult.Rejected("缺少 rows 数组")
        val types = ArrayList<ZmsqCertificateType>(rows.length())
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val wid = row.text("WID") ?: continue // 无主键的行跳过
            val nameZh = row.text("ZMWJMC") ?: continue
            types += ZmsqCertificateType(
                wid = wid,
                nameZh = nameZh,
                nameEn = row.text("YWZMWJMC"),
                validityDaysText = row.text("WJYXTS"),
                enrollmentRestrictionDisplay = row.text("SFZJ_DISPLAY"),
                studentCategoryDisplay = row.text("BKXSLB_DISPLAY"),
            )
        }
        return TypesResult.Success(types)
    }

    fun parseApplicationList(payload: JSONObject): ApplicationListResult {
        val table = payload.optJSONObject("datas")?.optJSONObject("xszmsqcx")
            ?: return ApplicationListResult.Rejected("缺少 datas.xszmsqcx 信封结构")
        if (table.optJSONArray("rows") == null) {
            return ApplicationListResult.Rejected("缺少 rows 数组")
        }
        return ApplicationListResult.Success(table.optInt("totalSize", 0))
    }

    private fun JSONObject.text(key: String): String? =
        if (isNull(key)) null else optString(key, "").trim().ifEmpty { null }
}
