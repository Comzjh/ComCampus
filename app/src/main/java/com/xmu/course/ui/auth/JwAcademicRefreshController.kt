package com.xmu.course.ui.auth

import com.xmu.course.data.academiccompletion.AcademicRefreshOutcome
import com.xmu.course.data.academiccompletion.AcademicRefreshDiagnostics
import com.xmu.course.data.academiccompletion.JwAcademicRefreshCoordinator
import com.xmu.course.data.academiccompletion.AcademicCompletionStore
import com.xmu.course.data.jwgrades.GradeRefreshOutcome
import com.xmu.course.data.jwgrades.JwGradeRefreshCoordinator
import com.xmu.course.data.jwgrades.JwGradeStore
import com.xmu.course.adapter.jw.JwWebViewReadExecutor
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.delay

data class JwAcademicRefreshResult(
    val message: String,
    val diagnostics: AcademicRefreshDiagnostics? = null,
    val isWarning: Boolean = false,
)

/**
 * 用户显式点击的一次「刷新学业数据」编排：xywccx 培养方案 + cjcx 成绩单。
 *
 * 本类不持有 WebView：页面JS求值通道由调用方注入（evaluate），
 * 保持 UI 层对 URL/参数方言/Cookie 零感知；刷新仅在用户点击时发生（MANUAL_ONLY）。
 */
class JwAcademicRefreshController(
    private val academicStore: AcademicCompletionStore,
    private val gradeStore: JwGradeStore,
    private val todayProvider: () -> String = { LocalDate.now().toString() },
) {
    /** 返回给用户的一句话结果；任一源失败都只影响该源，旧缓存保持不变。 */
    suspend fun refresh(evaluate: suspend (String) -> String?): JwAcademicRefreshResult {
        val executor = JwWebViewReadExecutor(
            evaluate = evaluate,
            await = { delayMs -> delay(delayMs) },
        )
        val academic = JwAcademicRefreshCoordinator(
            executor = executor,
            store = academicStore,
            todayProvider = todayProvider,
        ).refresh()
        val grades = JwGradeRefreshCoordinator(
            executor = executor,
            store = gradeStore,
        ).refresh()
        val diagnostics = (academic as? AcademicRefreshOutcome.RejectedByValidation)?.diagnostics
        val isWarning = (academic as? AcademicRefreshOutcome.Success)
            ?.creditReconciliationWarning != null
        return JwAcademicRefreshResult(
            message = describe(academic, grades),
            diagnostics = diagnostics,
            isWarning = isWarning,
        )
    }

    companion object {
        internal fun describe(
            academic: AcademicRefreshOutcome,
            grades: GradeRefreshOutcome,
        ): String {
            val parts = listOfNotNull(
                academicMessage(academic),
                gradeMessage(grades),
            ).distinct()
            return parts.joinToString("；")
        }

        /** null 表示静默（成功已由组合文案表达时保留，其他同源失败去重后不重复播报）。 */
        private fun academicMessage(outcome: AcademicRefreshOutcome): String? = when (outcome) {
            is AcademicRefreshOutcome.Success -> {
                val summary = "培养方案已更新：在修 ${outcome.courseCount} 门，待确认学分 ${outcome.pendingManualCount} 门"
                val warning = outcome.creditReconciliationWarning ?: return summary
                val difference = BigDecimal(warning.planLevelCredits)
                    .subtract(BigDecimal(warning.inPlanCreditsSum))
                    .abs()
                    .stripTrailingZeros()
                    .toPlainString()
                "已导入可用课程明细，但结果可能不完整：课程明细合计 ${warning.inPlanCreditsSum} 学分，方案级合计 ${warning.planLevelCredits} 学分，相差 $difference 学分；在修 ${outcome.courseCount} 门，待确认学分 ${outcome.pendingManualCount} 门"
            }
            AcademicRefreshOutcome.AuthRequired -> "未登录教务：请先在页面内完成登录后重试"
            AcademicRefreshOutcome.PageNotReady -> "学校页面尚未就绪，请稍候重试"
            AcademicRefreshOutcome.ServerUnavailable -> "教务服务繁忙，本机数据保持不变，请稍后重试"
            AcademicRefreshOutcome.RequestRejected -> "请求被教务系统拒绝，本机数据保持不变"
            AcademicRefreshOutcome.MalformedResponse -> "教务响应异常，本机数据保持不变"
            is AcademicRefreshOutcome.RejectedByValidation ->
                ("培养方案数据校验未通过：" + outcome.reasons.firstOrNull().orEmpty()).ifBlank {
                    "培养方案数据校验未通过，本机数据保持不变"
                }
            AcademicRefreshOutcome.NetworkError -> "网络请求失败，本机数据保持不变"
            AcademicRefreshOutcome.StorageFailure -> "本机缓存写入失败"
        }

        private fun gradeMessage(outcome: GradeRefreshOutcome): String? = when (outcome) {
            is GradeRefreshOutcome.Success ->
                "成绩单已更新：${outcome.entryCount} 条记录、${outcome.semesterCount} 个学期"
            GradeRefreshOutcome.AuthRequired -> "未登录教务：请先在页面内完成登录后重试"
            GradeRefreshOutcome.RoleContextRequired -> "请先在学校页面确认学生身份后重试"
            GradeRefreshOutcome.ServerUnavailable -> "教务服务繁忙，本机数据保持不变，请稍后重试"
            GradeRefreshOutcome.RequestRejected -> "请求被教务系统拒绝，本机数据保持不变"
            is GradeRefreshOutcome.MalformedResponse -> "教务响应异常，本机数据保持不变"
            GradeRefreshOutcome.NetworkError -> "网络请求失败，本机数据保持不变"
            GradeRefreshOutcome.StorageFailure -> "本机缓存写入失败"
        }
    }
}
