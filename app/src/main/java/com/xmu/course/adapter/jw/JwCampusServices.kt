package com.xmu.course.adapter.jw

/**
 * JW 稳定服务标识：不含 URL、不含用户信息、不绑定 WebView。
 *
 * 仅 [ACADEMIC_COMPLETION] 当前实现并可打开；其余为未来能力声明，
 * 必须等对应功能就绪后才允许进入 services() 清单。
 */
object JwCampusServices {
    /** Provider 级稳定标识。 */
    const val PROVIDER_ID = "xmu.jw"

    /** 学业完成查询（当前唯一实现）。 */
    const val ACADEMIC_COMPLETION = "xmu.jw.academic_completion"

    /** 证明申请（未来能力，尚未实现）。 */
    const val CERTIFICATE = "xmu.jw.certificate"

    /** 成绩单 / Transcript（未来能力，尚未实现）。 */
    const val TRANSCRIPT = "xmu.jw.transcript"
}