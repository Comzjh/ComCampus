package com.xmu.course.data.academiccompletion

import org.json.JSONArray
import org.json.JSONObject

/**
 * 内部持久化 JSON 的编解码（schemaVersion 绑定内部模型，与来源格式无关）。
 *
 * 解码严格：任何无法解释的内容抛异常，由 Store 呈现为存储失败态，
 * 不 crash、不静默编造空数据。
 */
object AcademicCompletionSnapshotCodec {

    fun encode(snapshot: AcademicCompletionSnapshot): String {
        val root = JSONObject()
        root.put("schemaVersion", snapshot.schemaVersion)
        root.put("semesterLabel", snapshot.semesterLabel)
        root.put("fetchedAtEpochMillis", snapshot.fetchedAtEpochMillis ?: JSONObject.NULL)
        root.put(
            "plan",
            JSONObject()
                .put("planName", snapshot.plan.planName)
                .put("requiredCreditsText", snapshot.plan.requiredCreditsText)
                .put("earnedCreditsText", snapshot.plan.earnedCreditsText)
                .put("sourceSnapshotAt", snapshot.plan.sourceSnapshotAt)
                .put("sourceThisSemesterTotalText", snapshot.plan.sourceThisSemesterTotalText),
        )
        root.put("courses", JSONArray().apply { snapshot.enrolledCourses.forEach { put(encodeCourse(it)) } })
        root.put(
            "completedOutsidePlan",
            JSONArray().apply {
                snapshot.completedCoursesOutsidePlan.forEach {
                    put(
                        JSONObject()
                            .put("courseCode", it.courseCode)
                            .put("courseName", it.courseName)
                            .put("creditsText", it.creditsText)
                            .put("termCode", it.termCode)
                            .put("scoreText", it.scoreText),
                    )
                }
            },
        )
        root.put(
            "localOverrides",
            JSONArray().apply {
                snapshot.localOverrides.values.sortedBy { it.courseCode }.forEach {
                    put(JSONObject().put("courseCode", it.courseCode).put("creditsText", it.creditsText))
                }
            },
        )
        return root.toString()
    }

    private fun encodeCourse(course: SourceCourse): JSONObject = JSONObject()
        .put("courseCode", course.courseCode)
        .put("courseName", course.courseName)
        .put("creditsText", course.creditsText ?: JSONObject.NULL)
        .put("status", course.status.name)
        .put("inPlan", course.inPlan)
        .put("teacherNames", course.teacherNames)
        .put("classCode", course.classCode)
        .put("confirmationHint", course.confirmationHint ?: JSONObject.NULL)

    fun decode(raw: String): AcademicCompletionSnapshot {
        val root = JSONObject(raw)
        val version = root.optInt("schemaVersion", -1)
        check(version == ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION) {
            "unsupported internal schema version: $version"
        }
        val semesterLabel = root.requireNonBlankString("semesterLabel")
        val fetchedAtEpochMillis = root.optionalNonNegativeLong("fetchedAtEpochMillis")
        val planJson = root.getJSONObject("plan")
        val plan = PlanSummary(
            planName = planJson.requireNonBlankString("planName"),
            requiredCreditsText = planJson.requireDecimal("requiredCreditsText"),
            earnedCreditsText = planJson.requireDecimal("earnedCreditsText"),
            sourceSnapshotAt = planJson.requireNonBlankString("sourceSnapshotAt"),
            sourceThisSemesterTotalText = planJson.requireDecimal("sourceThisSemesterTotalText"),
        )
        val coursesJson = root.getJSONArray("courses")
        val courses = mutableListOf<SourceCourse>()
        val seen = mutableSetOf<String>()
        for (index in 0 until coursesJson.length()) {
            val item = coursesJson.getJSONObject(index)
            val course = SourceCourse(
                courseCode = item.requireNonBlankString("courseCode"),
                courseName = item.requireNonBlankString("courseName"),
                creditsText = item.optionalDecimalOrNull("creditsText"),
                status = enumOrThrow<SourceXfStatus>(item.requireNonBlankString("status")),
                inPlan = item.getBoolean("inPlan"),
                teacherNames = item.optString("teacherNames", ""),
                classCode = item.optString("classCode", ""),
                confirmationHint = item.optionalNonBlankString("confirmationHint"),
            )
            check(seen.add(course.courseCode)) { "duplicate course code in persisted snapshot" }
            check(course.status != SourceXfStatus.CONFIRMED || course.creditsText != null) {
                "corrupted snapshot: confirmed course without credits"
            }
            check(course.status != SourceXfStatus.NEEDS_MANUAL || course.creditsText == null) {
                "corrupted snapshot: needs-manual course carries source credits"
            }
            courses += course
        }
        val completedJson = root.getJSONArray("completedOutsidePlan")
        val completed = mutableListOf<CompletedCourseOutsidePlan>()
        val completedSeen = mutableSetOf<String>()
        for (index in 0 until completedJson.length()) {
            val item = completedJson.getJSONObject(index)
            val course = CompletedCourseOutsidePlan(
                courseCode = item.requireNonBlankString("courseCode"),
                courseName = item.requireNonBlankString("courseName"),
                creditsText = item.requireDecimal("creditsText"),
                termCode = item.requireNonBlankString("termCode"),
                scoreText = item.requireNonBlankString("scoreText"),
            )
            check(completedSeen.add(course.courseCode)) { "duplicate completed-outside-plan code" }
            completed += course
        }
        val overridesJson = root.getJSONArray("localOverrides")
        val overrides = mutableMapOf<String, LocalCreditOverride>()
        for (index in 0 until overridesJson.length()) {
            val item = overridesJson.getJSONObject(index)
            val courseCode = item.requireNonBlankString("courseCode")
            val creditsText = item.requireDecimal("creditsText")
            check(overrides.put(courseCode, LocalCreditOverride(courseCode, creditsText)) == null) {
                "duplicate local override"
            }
        }
        return AcademicCompletionSnapshot(
            schemaVersion = version,
            semesterLabel = semesterLabel,
            plan = plan,
            enrolledCourses = courses,
            completedCoursesOutsidePlan = completed,
            localOverrides = overrides.toMap(),
            fetchedAtEpochMillis = fetchedAtEpochMillis,
        )
    }

    private inline fun <reified T : Enum<T>> enumOrThrow(value: String): T =
        try {
            enumValueOf<T>(value)
        } catch (error: IllegalArgumentException) {
            throw IllegalStateException("corrupted snapshot: unknown status $value")
        }

    private fun JSONObject.requireNonBlankString(key: String): String {
        check(has(key) && !isNull(key)) { "corrupted snapshot: missing $key" }
        val text = get(key).toString().trim()
        check(text.isNotEmpty()) { "corrupted snapshot: blank $key" }
        return text
    }

    private fun JSONObject.optionalNonBlankString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return get(key).toString().trim().ifEmpty { null }
    }

    private fun JSONObject.requireDecimal(key: String): String {
        check(has(key) && !isNull(key)) { "corrupted snapshot: missing $key" }
        val normalized = CreditsDecimal.parseDecimalText(get(key).toString())
        check(normalized != null) { "corrupted snapshot: invalid decimal $key" }
        return normalized
    }

    private fun JSONObject.optionalDecimalOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val normalized = CreditsDecimal.parseDecimalText(get(key).toString())
        check(normalized != null) { "corrupted snapshot: invalid decimal $key" }
        return normalized
    }

    private fun JSONObject.optionalNonNegativeLong(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        val value = getLong(key)
        check(value >= 0L) { "corrupted snapshot: invalid $key" }
        return value
    }
}
