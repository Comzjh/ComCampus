package com.xmu.course.data.jwgrades

import com.xmu.course.contracts.provider.PrivacyDataOwner

/**
 * cjcx 成绩单缓存的隐私数据属主。
 *
 * 数据来源：用户在官方页面内手动触发的 JW 只读刷新（非 Provider 后台同步）。
 * 清除范围 = 本机成绩单 JSON 缓存仅此而已；不触碰登录状态、凭据与其他数据集。
 */
class JwGradeDataOwner(
    private val store: JwGradeStore,
) : PrivacyDataOwner {

    override suspend fun countLocalData(): Int = when (val current = store.current()) {
        JwGradeStore.State.Empty -> 0
        is JwGradeStore.State.Loaded -> current.snapshot.entries.size
        is JwGradeStore.State.StorageFailure ->
            throw IllegalStateException("JW grade cache is unreadable")
    }

    override suspend fun clearLocalData() {
        check(store.clearAll()) { "JW grade cache could not be cleared" }
    }
}
