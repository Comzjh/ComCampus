package com.xmu.course.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RC 导航契约（BUG-01 / BUG-04 / NAV-A / NAV-B）。
 *
 * 锁死三件事：导入成功回跳目标由发起页决定；学业子页永远挂在「学业」root 下；
 * 「设置」只剩一个产品入口，次级 settings 路由不得复活。
 */
class RcNavigationContractTest {

    @Test
    fun importOriginParsesRouteArgumentConservatively() {
        assertEquals(TimetableImportOrigin.HOME, TimetableImportOrigin.fromArgument("home"))
        assertEquals(TimetableImportOrigin.HOME, TimetableImportOrigin.fromArgument("HOME"))
        assertEquals(TimetableImportOrigin.TIMETABLE, TimetableImportOrigin.fromArgument("timetable"))
        // 参数缺失或被塞入任何不可识别的值：保守回落课表，绝不回弹 WebView。
        assertEquals(TimetableImportOrigin.TIMETABLE, TimetableImportOrigin.fromArgument(null))
        assertEquals(TimetableImportOrigin.TIMETABLE, TimetableImportOrigin.fromArgument("settings"))
        assertEquals(TimetableImportOrigin.TIMETABLE, TimetableImportOrigin.fromArgument(""))
    }

    @Test
    fun importRouteCarriesOriginAndStillBelongsToTimetableTab() {
        assertEquals("import?origin=home", TimetableImportOrigin.HOME.importRoute)
        assertEquals("webview?origin=home", TimetableImportOrigin.HOME.webviewRoute)
        // 注册用的路由模板与具体发起页路由各自成立。
        assertEquals("import?origin={origin}", AppRoutes.IMPORT_WITH_ORIGIN)
        assertEquals("webview?origin={origin}", AppRoutes.WEBVIEW_WITH_ORIGIN)
        assertEquals("import?origin=timetable", TimetableImportOrigin.TIMETABLE.importRoute)
        assertEquals("webview?origin=timetable", TimetableImportOrigin.TIMETABLE.webviewRoute)
        assertEquals(AppDestination.Timetable, appDestinationForRoute(TimetableImportOrigin.HOME.importRoute))
        assertEquals(AppDestination.Timetable, appDestinationForRoute(AppRoutes.WEBVIEW_WITH_ORIGIN))
    }

    @Test
    fun importSuccessDestinationIsTheOriginScreenItself() {
        assertEquals(AppRoutes.HOME, TimetableImportOrigin.HOME.route)
        assertEquals(AppRoutes.TIMETABLE, TimetableImportOrigin.TIMETABLE.route)
        // 只有两个真实入口：首页与课表页；个人中心/设置没有课表导入入口。
        assertEquals(listOf("HOME", "TIMETABLE"), TimetableImportOrigin.entries.map { it.name })
    }

    @Test
    fun secondarySettingsRouteIsGoneAndProfileDetailStaysADetailPage() {
        assertNull(appDestinationForRoute("settings"))
        assertTrue(AppDestination.entries.all { destination -> !destination.childRoutes.contains("settings") })
        assertEquals(AppDestination.Profile, appDestinationForRoute(AppRoutes.PROFILE))
        assertEquals(AppDestination.Profile, appDestinationForRoute(AppRoutes.PROFILE_DETAIL))
    }

    @Test
    fun academicChildrenAllRemainSiblingsUnderAcademicRoot() {
        listOf(
            AppRoutes.ACADEMIC_GPA,
            AppRoutes.GRADES_SANDBOX,
            AppRoutes.ACADEMIC_SEMESTER,
            AppRoutes.ACADEMIC_HISTORY,
            AppRoutes.ACADEMIC_COMPLETION,
            AppRoutes.ACADEMIC_DATA_SOURCES,
        ).forEach { route ->
            assertEquals(AppDestination.Grades, appDestinationForRoute(route))
        }
    }
}
