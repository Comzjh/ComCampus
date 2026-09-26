package com.xmu.course.data.jwgrades

import com.xmu.course.adapter.jw.CjcxRequestBuilder
import com.xmu.course.adapter.jw.JwCampusServices
import com.xmu.course.adapter.jw.JwEnvelopeResult
import com.xmu.course.adapter.jw.JwPageCheck
import com.xmu.course.adapter.jw.JwReadEndpoint
import com.xmu.course.adapter.jw.JwWebViewReadExecutor

/**
 * cjcx 手动刷新结果分类。
 *
 * 只携带安全元数据（计数），绝不携带响应体、成绩内容或身份信息。
 */
sealed interface GradeRefreshOutcome {
    /** 成功：整份成绩单已原子替换本地缓存。 */
    data class Success(val entryCount: Int, val semesterCount: Int) : GradeRefreshOutcome

    /** 页面不在官方域（未登录/停在 CAS）：需要用户手动登录。 */
    data object AuthRequired : GradeRefreshOutcome

    /**
     * 官方域内 403：集成包实测主因是页面处于「选择角色」未确认状态
     * （需用户在官方页面点选本科生角色）；条件缺失类 403 由 builder 单测防线排除。
     */
    data object RoleContextRequired : GradeRefreshOutcome

    /** 官方服务暂时不可用（504/超时）：旧缓存保持不变。 */
    data object ServerUnavailable : GradeRefreshOutcome

    /** 请求被拒绝（其他 HTTP 状态/业务错误码）。 */
    data object RequestRejected : GradeRefreshOutcome

    /** 响应无法解析、结构不完整或超限：旧缓存保持不变。 */
    data class MalformedResponse(val reason: String) : GradeRefreshOutcome

    /** 网络失败。 */
    data object NetworkError : GradeRefreshOutcome

    /** 存储写入失败：内存与磁盘旧快照保持不变。 */
    data object StorageFailure : GradeRefreshOutcome
}

/**
 * cjcx 手动刷新编排：可信页校验 → querySetting 方言全量拉取 → fail-closed 解析 → 整体替换缓存。
 *
 * - 仅由用户显式动作触发（MANUAL_ONLY，无后台/定时）；
 * - 任一步失败立即中止，[JwGradeStore] 保持原状（刷新失败不删可用数据）；
 * - 全程不接触 Cookie/凭据/学号（服务端按会话识别，请求体不含身份参数）。
 */
class JwGradeRefreshCoordinator(
    private val executor: JwWebViewReadExecutor,
    private val store: JwGradeStore,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun refresh(): GradeRefreshOutcome {
        // cjcx 不依赖 BH_UTILS：任意官方域已登录页均可发起 same-origin 请求。
        when (executor.verifyTrustedPage(requireBhUtils = false)) {
            JwPageCheck.TRUSTED_READY -> Unit
            JwPageCheck.TRUSTED_NOT_READY -> return GradeRefreshOutcome.RoleContextRequired
            JwPageCheck.UNTRUSTED_ORIGIN -> return GradeRefreshOutcome.AuthRequired
            JwPageCheck.PROBE_FAILURE -> return GradeRefreshOutcome.MalformedResponse("页面探测失败")
        }

        val payload = when (
            val result = executor.fetch(
                endpoint = JwReadEndpoint.CJCX_STUDENT_GRADES,
                formBody = CjcxRequestBuilder.gradesBody(),
            )
        ) {
            is JwEnvelopeResult.Ok -> result.payload
            is JwEnvelopeResult.HttpFailure -> {
                when (result.status) {
                    403 -> return GradeRefreshOutcome.RoleContextRequired
                    504 -> return GradeRefreshOutcome.ServerUnavailable
                    else -> return GradeRefreshOutcome.RequestRejected
                }
            }
            JwEnvelopeResult.NetworkFailure -> return GradeRefreshOutcome.NetworkError
            is JwEnvelopeResult.BusinessError -> return GradeRefreshOutcome.RequestRejected
            JwEnvelopeResult.Malformed -> return GradeRefreshOutcome.MalformedResponse("响应不是合法 JSON 信封")
            JwEnvelopeResult.TooLarge -> return GradeRefreshOutcome.MalformedResponse("响应超过大小护栏")
        }

        val parsed = when (val outcome = CjcxGradeParser.parseGrades(payload)) {
            is CjcxGradeParser.ParseResult.Rejected ->
                return GradeRefreshOutcome.MalformedResponse(outcome.reason)
            is CjcxGradeParser.ParseResult.Success -> outcome
        }
        val snapshot = JwGradeSnapshot(
            refreshedAtEpochMillis = nowEpochMillis(),
            providerId = JwCampusServices.PROVIDER_ID,
            sourceCapability = SOURCE_CAPABILITY,
            totalCreditsText = parsed.totalCreditsText,
            entries = parsed.entries,
        )
        if (!store.replace(snapshot)) return GradeRefreshOutcome.StorageFailure
        return GradeRefreshOutcome.Success(
            entryCount = snapshot.entries.size,
            semesterCount = snapshot.groupedBySemester().size,
        )
    }

    companion object {
        /** 来源能力标识：成绩明细以 cjcx 为源；「已修」学分仍以 xywccx 为权威。 */
        const val SOURCE_CAPABILITY = "cjcx.xscjcx"
    }
}
