package com.xmu.course.data.academic

import android.content.Context
import com.xmu.course.domain.grades.OutsidePlanGpaPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 方案外课程 GPA 策略的本地偏好（SharedPreferences 持久化 + StateFlow 响应式）。
 *
 * 只影响本地派生 GPA；绝不写回学校；未知/损坏取值一律安全回退 UNCONFIRMED，
 * 保证「学校数据异常时不替用户做决定」的默认语义。
 */
class AcademicGpaPolicyStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _policy = MutableStateFlow(read())
    val policy: StateFlow<OutsidePlanGpaPolicy> = _policy.asStateFlow()

    fun setPolicy(value: OutsidePlanGpaPolicy) {
        prefs.edit().putString(KEY_POLICY, value.name).apply()
        _policy.value = value
    }

    private fun read(): OutsidePlanGpaPolicy = runCatching {
        OutsidePlanGpaPolicy.valueOf(prefs.getString(KEY_POLICY, null) ?: OutsidePlanGpaPolicy.UNCONFIRMED.name)
    }.getOrDefault(OutsidePlanGpaPolicy.UNCONFIRMED)

    private companion object {
        const val PREFS = "academic_prefs"
        const val KEY_POLICY = "gpa_outside_plan_policy"
    }
}
