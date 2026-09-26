package com.xmu.course.di

import com.xmu.course.contracts.provider.ProviderCapability
import com.xmu.course.contracts.provider.ProviderDescriptor
import com.xmu.course.contracts.provider.SyncPolicy

/**
 * 只读 Provider 身份描述（Phase 5.3.1）：仅声明稳定机器身份，不承载任何业务逻辑。
 *
 * - Wisedu：课表来源，用户在官方 WebView 手动导入（MANUAL_ONLY）；
 * - TronClass：Todo 来源，前台可见时允许刷新（FOREGROUND_ALLOWED）；
 * - JW：复用 XmuJwAdapter.descriptor()（CAMPUS_SERVICE / MANUAL_ONLY），不在此重复声明。
 *
 * 不创建 ProviderRegistry；聚合由组合根 XmuCourseApp 完成。
 */
object ProviderDescriptors {
    val WISEDU = ProviderDescriptor(
        id = "xmu.wisedu",
        capabilities = setOf(ProviderCapability.TIMETABLE),
        syncPolicy = SyncPolicy.MANUAL_ONLY,
    )

    val TRONCLASS = ProviderDescriptor(
        id = "xmu.tronclass",
        capabilities = setOf(ProviderCapability.TODO),
        syncPolicy = SyncPolicy.FOREGROUND_ALLOWED,
    )
}