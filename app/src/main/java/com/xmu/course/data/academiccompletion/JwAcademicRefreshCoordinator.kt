package com.xmu.course.data.academiccompletion

import com.xmu.course.adapter.jw.JwEnvelopeResult
import com.xmu.course.adapter.jw.JwPageCheck
import com.xmu.course.adapter.jw.JwReadEndpoint
import com.xmu.course.adapter.jw.JwWebViewReadExecutor
import com.xmu.course.adapter.jw.JwXywccxDialect
import org.json.JSONObject

/**
 * 刷新结果分类。
 *
 * 只携带安全元数据（计数/类别），绝不携带响应体、身份或成绩内容。
 */
sealed interface AcademicRefreshOutcome {
    /** 成功：快照已导入本地（原子覆盖，旧数据被新权威数据替换）。 */
    data class Success(val courseCount: Int, val pendingManualCount: Int) : AcademicRefreshOutcome

    /** 页面不在官方域或未登录：需要用户在官方 WebView 手动登录。 */
    data object AuthRequired : AcademicRefreshOutcome

    /** 官方 SPA 尚未就绪（加载中/角色未确认）。 */
    data object PageNotReady : AcademicRefreshOutcome

    /** 官方服务暂时不可用（504/超时）：旧缓存保持不变。 */
    data object ServerUnavailable : AcademicRefreshOutcome

    /** 请求被拒绝（业务错误码/其他 HTTP 状态）。 */
    data object RequestRejected : AcademicRefreshOutcome

    /** 响应无法解析、超限或探测异常。 */
    data object MalformedResponse : AcademicRefreshOutcome

    /** 组装/对账失败：数据过期或来源结构变化；旧缓存保持不变。 */
    data class RejectedByValidation(
        val reasons: List<String>,
        val diagnostics: AcademicRefreshDiagnostics? = null,
    ) : AcademicRefreshOutcome

    /** 网络失败。 */
    data object NetworkError : AcademicRefreshOutcome

    /** 存储写入失败：内存与磁盘旧快照保持不变。 */
    data object StorageFailure : AcademicRefreshOutcome
}

/**
 * xywccx 手动刷新编排：白名单只读序列 → 组装 → 复用既有 fail-closed 解析与导入。
 *
 * - 仅由用户显式动作触发（无定时/后台）；
 * - 任一步失败立即中止，AcademicCompletionStore 保持原状（失败不清缓存）；
 * - 全程不接触 Cookie/凭据/学号（学号在页面内替换）。
 */
class JwAcademicRefreshCoordinator(
    private val executor: JwWebViewReadExecutor,
    private val store: AcademicCompletionStore,
    private val todayProvider: () -> String,
    private val epochMillisProvider: () -> Long = System::currentTimeMillis,
) {
    private var lastFailure: AcademicRefreshOutcome = AcademicRefreshOutcome.NetworkError

    /** 用户显式触发的一次 xywccx 只读刷新；任何一步失败都不触碰本地缓存。 */
    suspend fun refresh(): AcademicRefreshOutcome {
        lastFailure = AcademicRefreshOutcome.NetworkError
        when (executor.awaitTrustedPage(requireBhUtils = true)) {
            JwPageCheck.TRUSTED_READY -> Unit
            JwPageCheck.TRUSTED_NOT_READY -> return AcademicRefreshOutcome.PageNotReady
            JwPageCheck.UNTRUSTED_ORIGIN -> return AcademicRefreshOutcome.AuthRequired
            JwPageCheck.PROBE_FAILURE -> return AcademicRefreshOutcome.MalformedResponse
        }

        val semester = fetchOk(
            JwReadEndpoint.XYWCCX_CURRENT_SEMESTER,
            JwXywccxDialect.currentSemesterBody(),
        ) ?: return lastFailure
        val semesterCode = semester.optJSONObject("datas")
            ?.optJSONObject("cxdqxnxq")?.optJSONArray("rows")?.optJSONObject(0)
            ?.optString("DM").orEmpty()
        val gsappCode = XywccxSnapshotAssembler.mapGsappSemesterCode(semesterCode)
            ?: return AcademicRefreshOutcome.MalformedResponse

        val plans = fetchOk(JwReadEndpoint.XYWCCX_PLANS, JwXywccxDialect.plansBody()) ?: return lastFailure
        val planCode = XywccxSnapshotAssembler.selectMainPlan(plans)
            ?.optString("PYFADM")?.ifBlank { null }
            ?: return AcademicRefreshOutcome.RejectedByValidation(
                reasons = listOf("未找到个人培养方案"),
                diagnostics = AcademicRefreshDiagnostics(
                    stage = AcademicRefreshDiagnosticStage.PLAN_SELECTION,
                    code = AcademicRefreshDiagnosticCode.PLAN_NOT_FOUND,
                ),
            )

        val snapshot = fetchOk(
            JwReadEndpoint.XYWCCX_COMPLETION_SNAPSHOT,
            JwXywccxDialect.completionSnapshotBody(),
        ) ?: return lastFailure
        val planTotal = fetchOk(
            JwReadEndpoint.XYWCCX_PLAN_SEMESTER_TOTAL,
            query = JwXywccxDialect.planSemesterTotalQuery(planCode, semesterCode),
        ) ?: return lastFailure
        val pool = fetchOk(
            JwReadEndpoint.XYWCCX_COURSE_POOL,
            JwXywccxDialect.coursePoolBody(planCode),
        ) ?: return lastFailure
        val fawCompleted = fetchOk(
            JwReadEndpoint.XYWCCX_COURSE_POOL,
            JwXywccxDialect.outsidePlanCompletedBody(planCode),
        ) ?: return lastFailure
        val semesterCourses = fetchOk(
            JwReadEndpoint.GSAPP_SEMESTER_COURSES,
            JwXywccxDialect.semesterCoursesBody(gsappCode),
        ) ?: return lastFailure

        val assembled = XywccxSnapshotAssembler.assemble(
            XywccxSnapshotAssembler.Inputs(
                currentSemester = semester,
                plans = plans,
                completionSnapshot = snapshot,
                planSemesterTotal = planTotal,
                coursePool = pool,
                outsidePlanCompleted = fawCompleted,
                semesterCourses = semesterCourses,
                generatedAtDate = todayProvider(),
            ),
        )
        val sourceJson = when (assembled) {
            is AssembleResult.Invalid ->
                return AcademicRefreshOutcome.RejectedByValidation(
                    reasons = listOf(assembled.reason),
                    diagnostics = AcademicRefreshDiagnostics(
                        stage = AcademicRefreshDiagnosticStage.SNAPSHOT_ASSEMBLY,
                        code = AcademicRefreshDiagnosticCode.SOURCE_STRUCTURE_INVALID,
                    ),
                )
            is AssembleResult.Success -> assembled.sourceJson
        }
        return when (val parsed = LocalAcademicSnapshotParser.parse(sourceJson)) {
            is SnapshotImportResult.Rejected -> {
                val assembledSuccess = assembled as AssembleResult.Success
                val hasPlanCreditMismatch = parsed.reasons.any { reason ->
                    reason.startsWith("方案内课程学分求和（") &&
                        reason.contains("与方案级本学期已选合计（")
                }
                val code = if (hasPlanCreditMismatch) {
                    AcademicRefreshDiagnosticCode.PLAN_CREDIT_TOTAL_MISMATCH
                } else AcademicRefreshDiagnosticCode.SOURCE_VALIDATION_REJECTED
                AcademicRefreshOutcome.RejectedByValidation(
                    reasons = parsed.reasons.take(3),
                    diagnostics = AcademicRefreshDiagnostics(
                        stage = AcademicRefreshDiagnosticStage.SNAPSHOT_VALIDATION,
                        code = code,
                        reconciliation = assembledSuccess.diagnostics,
                    ),
                )
            }
            is SnapshotImportResult.Success -> {
                val fetchedSnapshot = parsed.snapshot.copy(fetchedAtEpochMillis = epochMillisProvider())
                if (!store.import(fetchedSnapshot)) return AcademicRefreshOutcome.StorageFailure
                AcademicRefreshOutcome.Success(
                    courseCount = fetchedSnapshot.enrolledCourses.size,
                    pendingManualCount = fetchedSnapshot.pendingManualCourses.size,
                )
            }
        }
    }

    private suspend fun fetchOk(
        endpoint: JwReadEndpoint,
        formBody: String = "",
        query: String = "",
    ): JSONObject? = when (val result = executor.fetch(endpoint, formBody, query)) {
        is JwEnvelopeResult.Ok -> result.payload
        is JwEnvelopeResult.HttpFailure -> {
            lastFailure = when (result.status) {
                403 -> AcademicRefreshOutcome.AuthRequired
                504 -> AcademicRefreshOutcome.ServerUnavailable
                else -> AcademicRefreshOutcome.RequestRejected
            }
            null
        }
        JwEnvelopeResult.NetworkFailure -> {
            lastFailure = AcademicRefreshOutcome.NetworkError
            null
        }
        is JwEnvelopeResult.BusinessError -> {
            lastFailure = AcademicRefreshOutcome.RequestRejected
            null
        }
        JwEnvelopeResult.Malformed -> {
            lastFailure = AcademicRefreshOutcome.MalformedResponse
            null
        }
        JwEnvelopeResult.TooLarge -> {
            lastFailure = AcademicRefreshOutcome.MalformedResponse
            null
        }
    }
}
