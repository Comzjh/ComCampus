package com.xmu.course.data.academiccompletion

import com.xmu.course.contracts.provider.PrivacyDataOwner

/**
 * 学业完成快照的隐私数据属主（Phase 10.1 审查决议 RC-8）。
 *
 * 归属：本数据的来源是"用户主动选择的本地文件导入"（LOCAL FILE IMPORT），
 * 不属于任何 Provider，因此不挂接到 xmu.jw / xmu.wisedu；也不伪装成 AcademicRecord。
 * 复用既有 PrivacyDataOwner 契约，未修改契约本身。
 *
 * 删除安全模型：
 * - 清除范围 = 脱敏快照文件 + 全部本地学分覆盖，仅此而已；
 * - 纯本地文件删除：无网络、无同步、无认证清理；
 * - 仅用户主动触发（页面确认对话框 / 未来统一管理入口）。
 */
class AcademicCompletionDataOwner(
    private val store: AcademicCompletionStore,
) : PrivacyDataOwner {

    override suspend fun countLocalData(): Int = when (val current = store.current()) {
        AcademicCompletionStore.State.Empty -> 0
        is AcademicCompletionStore.State.Loaded -> current.snapshot.let { snapshot ->
            snapshot.enrolledCourses.size +
                snapshot.completedCoursesOutsidePlan.size +
                snapshot.localOverrides.size
        }
        is AcademicCompletionStore.State.StorageFailure ->
            throw IllegalStateException("Academic completion cache is unreadable")
    }

    override suspend fun clearLocalData() {
        check(store.clearAll()) { "Academic completion cache could not be cleared" }
    }
}
