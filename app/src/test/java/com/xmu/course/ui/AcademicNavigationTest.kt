package com.xmu.course.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 学业 IA 导航契约测试。
 *
 * 锁定：底部五 tab 不变；「学业」root 为真实学业首页；学业子页面全部归属学业 tab；
 * 旧成绩工具箱路由已从导航中彻底消失；带查询参数的官方页路由仍能归属正确 tab。
 */
class AcademicNavigationTest {

    @Test
    fun academicRootKeepsLegacyRouteValueForLaunchPreferenceCompatibility() {
        assertEquals("grades", AppDestination.Grades.route)
        assertEquals("学业", AppDestination.Grades.label)
        assertEquals(AppDestination.Grades, appDestinationForRoute(AppRoutes.GRADES))
    }

    @Test
    fun bottomTabsRemainFiveInOrder() {
        assertEquals(
            listOf(
                AppRoutes.HOME,
                AppRoutes.TIMETABLE,
                AppRoutes.TODO,
                AppRoutes.GRADES,
                AppRoutes.PROFILE,
            ),
            AppDestination.entries.map { it.route },
        )
    }

    @Test
    fun academicChildrenGroupUnderAcademicTab() {
        listOf(
            AppRoutes.ACADEMIC_SEMESTER,
            AppRoutes.ACADEMIC_HISTORY,
            AppRoutes.ACADEMIC_GPA,
            AppRoutes.ACADEMIC_COMPLETION,
            AppRoutes.GRADES_SANDBOX,
        ).forEach { route ->
            assertEquals(AppDestination.Grades, appDestinationForRoute(route))
        }
    }

    @Test
    fun officialPageWithAutoRefreshParameterStillBelongsToAcademicTab() {
        assertEquals(
            AppDestination.Grades,
            appDestinationForRoute("${AppRoutes.JW_ACADEMIC_REPORT}?autoRefresh=true"),
        )
    }

    @Test
    fun legacyToolboxRoutesAreNotNavigableAnywhere() {
        listOf("grades_transcript", "grades_manual", "academic_import").forEach { legacy ->
            assertNull(appDestinationForRoute(legacy))
            assertTrue(AppDestination.entries.none { it.route == legacy })
            assertTrue(AppDestination.entries.all { !it.childRoutes.contains(legacy) })
        }
    }
}