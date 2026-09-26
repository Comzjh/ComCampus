package com.xmu.course.data.jwgrades

import org.json.JSONArray
import org.json.JSONObject

/**
 * [JwGradeSnapshot] 的磁盘编解码（内部格式，与远端响应结构解耦）。
 *
 * fail-closed：任何缺失/畸形字段直接抛异常，由存储层转为 StorageFailure，
 * 绝不静默伪造空数据。输出不含身份、凭据、URL。
 */
object JwGradeSnapshotCodec {

    fun encode(snapshot: JwGradeSnapshot): String {
        val entries = JSONArray()
        for (entry in snapshot.entries) {
            entries.put(
                JSONObject()
                    .put("row_id", entry.rowId)
                    .put("semester_code", entry.semesterCode)
                    .put("semester_display", entry.semesterDisplay)
                    .put("course_code", entry.courseCode)
                    .put("course_name", entry.courseName)
                    .put("credits_text", entry.creditsText)
                    .put("grade_text", entry.gradeText)
                    .put("point_grade_text", entry.pointGradeText ?: JSONObject.NULL)
                    .put("course_nature_display", entry.courseNatureDisplay ?: JSONObject.NULL)
                    .put("course_category_display", entry.courseCategoryDisplay ?: JSONObject.NULL)
                    .put("offering_unit_display", entry.offeringUnitDisplay ?: JSONObject.NULL)
                    .put("retake_code", entry.retakeCode ?: JSONObject.NULL),
            )
        }
        return JSONObject()
            .put("schema_version", snapshot.schemaVersion)
            .put("refreshed_at_ms", snapshot.refreshedAtEpochMillis)
            .put("provider_id", snapshot.providerId)
            .put("source_capability", snapshot.sourceCapability)
            .put("total_credits_text", snapshot.totalCreditsText)
            .put("entries", entries)
            .toString()
    }

    fun decode(text: String): JwGradeSnapshot {
        val root = JSONObject(text)
        val version = root.getInt("schema_version")
        require(version == JwGradeSnapshot.SCHEMA_VERSION) { "unsupported schema_version" }
        val entriesArray = root.getJSONArray("entries")
        val entries = ArrayList<JwGradeEntry>(entriesArray.length())
        for (index in 0 until entriesArray.length()) {
            val obj = entriesArray.getJSONObject(index)
            entries += JwGradeEntry(
                rowId = obj.getString("row_id"),
                semesterCode = obj.getString("semester_code"),
                semesterDisplay = obj.getString("semester_display"),
                courseCode = obj.getString("course_code"),
                courseName = obj.getString("course_name"),
                creditsText = obj.getString("credits_text"),
                gradeText = obj.getString("grade_text"),
                pointGradeText = obj.optNullableString("point_grade_text"),
                courseNatureDisplay = obj.optNullableString("course_nature_display"),
                courseCategoryDisplay = obj.optNullableString("course_category_display"),
                offeringUnitDisplay = obj.optNullableString("offering_unit_display"),
                retakeCode = obj.optNullableString("retake_code"),
            )
        }
        return JwGradeSnapshot(
            schemaVersion = version,
            refreshedAtEpochMillis = root.getLong("refreshed_at_ms"),
            providerId = root.getString("provider_id"),
            sourceCapability = root.getString("source_capability"),
            totalCreditsText = root.getString("total_credits_text"),
            entries = entries,
        )
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (isNull(key)) return null
        return optString(key, "").ifEmpty { null }
    }
}
