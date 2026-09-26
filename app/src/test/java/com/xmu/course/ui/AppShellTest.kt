package com.xmu.course.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppShellTest {

    @Test
    fun bottomTabsMatchProductDesign() {
        assertEquals(
            listOf("首页", "课表", "待办", "学业", "设置"),
            AppDestination.entries.map { it.label },
        )
        assertEquals(
            listOf("home", "timetable", "todo", "grades", "profile"),
            AppDestination.entries.map { it.route },
        )
        assertEquals("home", AppRoutes.HOME)
        assertEquals("profile", AppRoutes.PROFILE)
    }

    @Test
    fun routeGroupingMapsChildrenToTabs() {
        assertEquals(AppDestination.Home, appDestinationForRoute(AppRoutes.HOME))
        assertEquals(AppDestination.Timetable, appDestinationForRoute(AppRoutes.IMPORT))
        assertEquals(AppDestination.Timetable, appDestinationForRoute(AppRoutes.TIMETABLE))
        assertEquals(AppDestination.Timetable, appDestinationForRoute(AppRoutes.BACKGROUND_PICKER))
        assertEquals(AppDestination.Todo, appDestinationForRoute(AppRoutes.TODO))
        assertEquals(AppDestination.Grades, appDestinationForRoute(AppRoutes.ACADEMIC_SEMESTER))
        assertEquals(AppDestination.Grades, appDestinationForRoute(AppRoutes.ACADEMIC_HISTORY))
        assertEquals(AppDestination.Grades, appDestinationForRoute(AppRoutes.ACADEMIC_GPA))
        assertNull(appDestinationForRoute("grades_transcript"))
        assertNull(appDestinationForRoute("academic_import"))
        assertEquals(AppDestination.Profile, appDestinationForRoute(AppRoutes.CAMPUS_SERVICE))
        assertEquals(AppDestination.Profile, appDestinationForRoute(AppRoutes.PROFILE_DETAIL))
        assertEquals(
            AppDestination.Profile,
            appDestinationForRoute("provider_data_management/xmu.jw"),
        )
        assertEquals(
            AppDestination.Profile,
            appDestinationForRoute(AppRoutes.PROVIDER_DATA_MANAGEMENT),
        )
        // NAV-B：次级 settings 路由已删除，只保留唯一「设置」root（profile）。
        assertNull(appDestinationForRoute("settings"))
        assertEquals(AppDestination.Profile, appDestinationForRoute(AppRoutes.TRONCLASS))
    }

    @Test
    fun unknownRoutesHaveNoTab() {
        assertNull(appDestinationForRoute(null))
        assertNull(appDestinationForRoute("no_such_route"))
        // 前缀相似但不同组的页面不应被误归入其他 tab
        assertNull(appDestinationForRoute("settings_detail"))
    }

    @Test
    fun sharedPagesKeepTheirSourceTabAndHaveDeepLinkFallbacks() {
        assertEquals(
            AppDestination.Grades,
            appDestinationForRouteStack(listOf(AppRoutes.GRADES, AppRoutes.ACADEMIC_DATA_SOURCES, AppRoutes.JW_ACADEMIC_REPORT)),
        )
        assertEquals(
            AppDestination.Profile,
            appDestinationForRouteStack(listOf(AppRoutes.PROFILE, AppRoutes.CAMPUS_SERVICE, AppRoutes.JW_ACADEMIC_REPORT)),
        )
        assertEquals(AppDestination.Grades, appDestinationForRouteStack(listOf(AppRoutes.JW_ACADEMIC_REPORT)))
        assertEquals(
            AppDestination.Grades,
            appDestinationForRouteStack(
                listOf(AppRoutes.GRADES, AppRoutes.ACADEMIC_DATA_SOURCES, AppRoutes.DATA_MANAGEMENT, "${AppRoutes.DATA_MANAGEMENT}/local"),
            ),
        )
        assertEquals(AppDestination.Profile, appDestinationForRouteStack(listOf(AppRoutes.PROFILE, AppRoutes.DATA_MANAGEMENT)))
        assertEquals(AppDestination.Profile, appDestinationForRouteStack(listOf(AppRoutes.DATA_MANAGEMENT)))
    }

    @Test
    fun sharedRouteSelectionUsesLastVisibleTabWithoutReadingInternalNavBackStack() {
        assertEquals(
            AppDestination.Grades,
            appDestinationForCurrentRoute(AppRoutes.JW_ACADEMIC_REPORT, AppDestination.Grades),
        )
        assertEquals(
            AppDestination.Profile,
            appDestinationForCurrentRoute("${AppRoutes.DATA_MANAGEMENT}/local", AppDestination.Profile),
        )
        assertEquals(AppDestination.Grades, appDestinationForCurrentRoute(AppRoutes.JW_ACADEMIC_REPORT, null))
        assertEquals(AppDestination.Profile, appDestinationForCurrentRoute(AppRoutes.DATA_MANAGEMENT, null))
        assertEquals(AppDestination.Timetable, appDestinationForCurrentRoute(AppRoutes.TIMETABLE, AppDestination.Profile))
    }

    @Test
    fun credentialWebViewsAreExcludedFromGlassBackdropCapture() {
        listOf(
            AppRoutes.IMPORT_WITH_ORIGIN,
            AppRoutes.WEBVIEW_WITH_ORIGIN,
            AppRoutes.JW_AUTH,
            AppRoutes.JW_ACADEMIC_REPORT,
            AppRoutes.JW_ACADEMIC_REPORT_AUTO,
            AppRoutes.JW_CERTIFICATE,
        ).forEach { route ->
            assertFalse("凭据 WebView 不得进入毛玻璃采样: $route", isGlassBackdropCaptureAllowed(route))
        }
        assertTrue(isGlassBackdropCaptureAllowed(AppRoutes.HOME))
    }

    @Test
    fun institutionalWebViewsDoNotAnimateAcrossRouteChanges() {
        listOf(
            AppRoutes.IMPORT_WITH_ORIGIN,
            AppRoutes.WEBVIEW_WITH_ORIGIN,
            AppRoutes.JW_AUTH,
            AppRoutes.JW_ACADEMIC_REPORT,
            AppRoutes.JW_ACADEMIC_REPORT_AUTO,
            AppRoutes.JW_CERTIFICATE,
        ).forEach { route ->
            assertFalse("认证/WebView 路由不应动画进入: $route", shouldAnimateAppRouteTransition(AppRoutes.HOME, route))
            assertFalse("认证/WebView 路由不应动画退出: $route", shouldAnimateAppRouteTransition(route, AppRoutes.HOME))
        }
        assertFalse(shouldAnimateAppRouteTransition(null, AppRoutes.HOME))
    }

    @Test
    fun topLevelTabsUseCrossfadeClassificationOnly() {
        assertTrue(isTopLevelTabSwitch(AppRoutes.HOME, AppRoutes.TIMETABLE))
        assertTrue(isTopLevelTabSwitch(AppRoutes.GRADES, AppRoutes.PROFILE))
        assertTrue(isTopLevelTabSwitch(AppRoutes.TIMETABLE_MANAGER, AppRoutes.HOME))
        assertTrue(isTopLevelTabSwitch(AppRoutes.HOME, AppRoutes.COURSE_MANAGER))
        assertFalse(isTopLevelTabSwitch(AppRoutes.TIMETABLE_MANAGER, AppRoutes.TIMETABLE))
        assertTrue(isTopLevelTabSwitch(AppRoutes.DATA_MANAGEMENT, AppRoutes.HOME))
        assertFalse(isTopLevelTabSwitch(AppRoutes.DATA_MANAGEMENT, AppRoutes.PROFILE, isPop = true))
        assertFalse(isTopLevelTabSwitch(AppRoutes.HOME, AppRoutes.HOME))
        assertTrue(shouldAnimateAppRouteTransition(AppRoutes.HOME, AppRoutes.COURSE_MANAGER))
        assertTrue(shouldAnimateAppRouteTransition(AppRoutes.COURSE_MANAGER, AppRoutes.HOME))
    }
}
