package com.xmu.course.adapter.jw

import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * cjcx 请求方言：`querySetting` JSON 条件数组 + `%2Aorder` 排序串 + `pageSize/pageNumber`。
 *
 * 与 xywccx（page/rows 平铺）和 zmsqxmu（无 querySetting）严格隔离：
 * 混用会 403 或挂起（集成包 README §3 实测）。
 *
 * 官方最小条件集（README §②，缺任一条件即 403）：
 * - SFYX="1"（字符串值）：只取有效成绩；
 * - SHOWMAXCJ=0（数值）：不折叠最高分，保留全部原始成绩行；
 * - `*order=-XNXQDM,-KCH,-KXH`：学期降序 + 课程号 + 选课号；
 *   顶层排序参数键的 `*` 必须编码为 `%2A`，值中逗号按官方请求保持原文。
 *
 * 隐私：本方言不含任何学号参数，服务端按会话识别学生；
 * 因此请求体不出现 `{{XH}}` 占位符。不加 XNXQDM 条件 = 全部学期一次拉全。
 */
object CjcxRequestBuilder {

    /** 一次拉全所有学期（实测 30 行，余量充足）。 */
    const val PAGE_SIZE = 999

    /** 官方默认排序串（README §② 原文，含逗号不转义）。 */
    const val ORDER_VALUE = "-XNXQDM,-KCH,-KXH"

    /** 成绩单全量请求体（application/x-www-form-urlencoded）。 */
    fun gradesBody(): String {
        val querySetting = JSONArray()
            .put(
                JSONObject()
                    .put("name", "SFYX")
                    .put("caption", "是否有效")
                    .put("linkOpt", "AND")
                    .put("builderList", "cbl_m_List")
                    .put("builder", "m_value_equal")
                    .put("value", "1")
                    .put("value_display", "是"),
            )
            .put(
                JSONObject()
                    .put("name", "SHOWMAXCJ")
                    .put("caption", "显示最高成绩")
                    .put("linkOpt", "AND")
                    .put("builderList", "cbl_String")
                    .put("builder", "equal")
                    .put("value", 0)
                    .put("value_display", "否"),
            )
            .put(
                JSONObject()
                    .put("name", "*order")
                    .put("value", ORDER_VALUE)
                    .put("linkOpt", "AND")
                    .put("builder", "m_value_equal"),
            )
        return "querySetting=" + URLEncoder.encode(querySetting.toString(), "UTF-8") +
            "&%2Aorder=$ORDER_VALUE" +
            "&pageSize=$PAGE_SIZE&pageNumber=1"
    }
}
