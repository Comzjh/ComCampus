package com.xmu.course.ui.academic

import com.xmu.course.data.academiccompletion.AcademicCompletionSnapshot
import com.xmu.course.data.academiccompletion.SourceXfStatus
import com.xmu.course.data.jwgrades.JwGradeSnapshot
import com.xmu.course.domain.grades.JwDerivedGpa
import com.xmu.course.domain.grades.OutsidePlanGpaPolicy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 学业模拟的数据转换层：本地教务缓存快照 → 模拟基线（spec Phase S）。
 *
 * 纯函数、零 Android 依赖、JVM 直测；Composable 不自行拼数据。
 * 三条纪律：
 * 1. 只读：绝不修改 AcademicCompletionStore / JwGradeStore 的任何事实；
 * 2. 不猜测：学分缺失、绩点无法解释、方案外未确认 → 标记 unresolved，不生成错误 GPA；
 * 3. 不重复计数：本学期已有真实成绩的课程已在基线内，不再作为可模拟行重复注入。
 */

/** 基线 GPA 的状态语义：只有 COMPUTED 才可作为模拟起点。 */
enum class SimulationGpaStatus { NO_DATA, NEEDS_CONFIRMATION, INSUFFICIENT, COMPUTED }

/** 一门课程进入模拟器时的事实快照。 */
data class SimulationCourseSeed(
    val courseCode: String,
    val courseName: String,
    /** 有效学分（来源确认优先，其次本地确认）；null = 学分待确认，不参与计算。 */
    val creditsText: String?,
    /** 官方成绩原文；本学期课程通常尚无成绩。 */
    val realGradeText: String?,
    /** 官方绩点原文；已有真实成绩的课程已计入基线，绝不再度参与模拟。 */
    val realPointText: String?,
    val outsidePlan: Boolean,
) {
    /** 已有官方成绩 = 基线的一部分，不作为可编辑模拟行。 */
    val alreadyGraded: Boolean
        get() = !realGradeText.isNullOrBlank()

    /** 学分缺失 = 需要用户确认，不自动猜测。 */
    val needsCreditConfirmation: Boolean
        get() = creditsText.isNullOrBlank()
}

/** 模拟器自动载入的当前学业基线。 */
data class SimulationBaseline(
    /** 本地计算 GPA 文本；null = 不可用（原因见 gpaStatus）。 */
    val gpaText: String?,
    val gpaStatus: SimulationGpaStatus,
    /** 已修学分：口径以培养方案为权威，不与成绩记录互相补齐。 */
    val earnedCreditsText: String?,
    val requiredCreditsText: String?,
    val planName: String?,
    val outsidePlanCompletedCount: Int,
    val pendingCreditCourseCount: Int,
    /** 数据更新时间（本机刷新时间戳，非学校统计时间）。 */
    val dataUpdatedText: String?,
)

/** 一次模拟会话的只读起点。 */
data class AcademicSimulationSeed(
    val sourceAvailable: Boolean,
    val semesterLabel: String?,
    val baseline: SimulationBaseline?,
    /** 本学期在修课程（保持来源顺序）。 */
    val currentSemesterCourses: List<SimulationCourseSeed>,
) {
    /** 可注入模拟列表的课程：尚无真实成绩且学分可得。 */
    val simlatableCourses: List<SimulationCourseSeed>
        get() = currentSemesterCourses.filter { !it.alreadyGraded && !it.needsCreditConfirmation }

    /** 学分待确认课程：显式呈现，绝不代猜。 */
    val unresolvedCourses: List<SimulationCourseSeed>
        get() = currentSemesterCourses.filter { !it.alreadyGraded && it.needsCreditConfirmation }

    /** 已有真实成绩、已计入基线的本学期课程。 */
    val alreadyGradedCourses: List<SimulationCourseSeed>
        get() = currentSemesterCourses.filter { it.alreadyGraded }

    val gpaNeedsConfirmation: Boolean
        get() = baseline?.gpaStatus == SimulationGpaStatus.NEEDS_CONFIRMATION

    /** 可作为计算起点的 GPA 文本；未确认/数据不足时为 null。 */
    val usableGpaText: String?
        get() = baseline?.takeIf { it.gpaStatus == SimulationGpaStatus.COMPUTED }?.gpaText

    /** 基线是否足以直接开始模拟。 */
    val canSimulate: Boolean
        get() = usableGpaText != null && baseline?.earnedCreditsText != null

    /** 唯一可用性判据：seed、ViewModel 与页面共用，不再各写一套（BUG-05）。 */
    val availability: AcademicSimulationAvailability
        get() = when {
            !sourceAvailable -> AcademicSimulationAvailability.Empty
            canSimulate -> AcademicSimulationAvailability.Ready(this)
            else -> AcademicSimulationAvailability.Partial(
                creditsAvailable = baseline?.earnedCreditsText != null,
                gradesAvailable = usableGpaText != null,
            )
        }

    companion object {
        val EMPTY = AcademicSimulationSeed(
            sourceAvailable = false,
            semesterLabel = null,
            baseline = null,
            currentSemesterCourses = emptyList(),
        )
    }
}

object AcademicSimulationSeedBuilder {

    fun build(
        completion: AcademicCompletionSnapshot?,
        grade: JwGradeSnapshot?,
        policy: OutsidePlanGpaPolicy,
    ): AcademicSimulationSeed {
        val outsideCodes = AcademicModuleAggregator.outsidePlanCourseCodes(completion)
        val gradedByCode = grade?.entries?.associateBy({ it.courseCode }, { it }).orEmpty()

        val semesterCourses = completion?.enrolledCourses.orEmpty().map { course ->
            val graded = gradedByCode[course.courseCode]
            SimulationCourseSeed(
                courseCode = course.courseCode,
                courseName = course.courseName,
                creditsText = completion?.effectiveCreditsText(course),
                realGradeText = graded?.gradeText,
                realPointText = graded?.pointGradeText,
                outsidePlan = course.status == SourceXfStatus.NEEDS_MANUAL || !course.inPlan,
            )
        }

        val computed = grade?.let { result(it, outsideCodes, policy) }
        val baseline = if (completion == null && grade == null) {
            null
        } else {
            SimulationBaseline(
                gpaText = (computed as? JwDerivedGpa.Result.Computed)?.gpaText,
                gpaStatus = computed.status(),
                earnedCreditsText = (computed as? JwDerivedGpa.Result.Computed)?.countedCreditsText ?: completion?.plan?.earnedCreditsText,
                requiredCreditsText = completion?.plan?.requiredCreditsText,
                planName = completion?.plan?.planName,
                outsidePlanCompletedCount = completion?.completedCoursesOutsidePlan?.size ?: 0,
                pendingCreditCourseCount = completion?.pendingManualCourses?.size ?: 0,
                dataUpdatedText = grade?.let {
                    Instant.ofEpochMilli(it.refreshedAtEpochMillis)
                        .atZone(ZoneId.systemDefault())
                        .format(UPDATED_AT_FORMAT)
                },
            )
        }
        return AcademicSimulationSeed(
            sourceAvailable = completion != null || grade != null,
            semesterLabel = completion?.semesterLabel,
            baseline = baseline,
            currentSemesterCourses = semesterCourses,
        )
    }

    /** GPA 口径复用 JwDerivedGpa：与学业首页同一份计算，不另起炉灶。 */
    private fun result(
        grade: JwGradeSnapshot,
        outsideCodes: Set<String>,
        policy: OutsidePlanGpaPolicy,
    ): JwDerivedGpa.Result = JwDerivedGpa.compute(
        courses = grade.entries.map { entry ->
            JwDerivedGpa.Course(
                creditsText = entry.creditsText,
                pointGradeText = entry.pointGradeText,
                outsidePlan = entry.courseCode in outsideCodes,
            )
        },
        policy = policy,
    )

    /** null = 成绩缓存为空；其余口径与首页 GPA 完全一致。 */
    private fun JwDerivedGpa.Result?.status(): SimulationGpaStatus = when (this) {
        null -> SimulationGpaStatus.NO_DATA
        is JwDerivedGpa.Result.Computed -> SimulationGpaStatus.COMPUTED
        is JwDerivedGpa.Result.NeedsConfirmation -> SimulationGpaStatus.NEEDS_CONFIRMATION
        JwDerivedGpa.Result.Insufficient -> SimulationGpaStatus.INSUFFICIENT
    }

    private val UPDATED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
}
