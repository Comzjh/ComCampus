package com.xmu.course.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xmu.course.ui.welcome.WelcomeScreen
import com.xmu.course.ui.import.ImportScreen
import com.xmu.course.ui.import.WebViewScreen
import com.xmu.course.ui.course.CourseManagerScreen
import com.xmu.course.ui.course.SkipCourseScreen
import com.xmu.course.ui.manager.TimetableManagerScreen
import com.xmu.course.ui.settings.TimetableSettingsScreen
import com.xmu.course.ui.settings.SettingsScreen
import com.xmu.course.ui.background.BackgroundPickerScreen
import com.xmu.course.ui.background.BackgroundEditorScreen
import com.xmu.course.ui.timetable.TimetableScreen
import com.xmu.course.ui.guide.UserGuideScreen
import com.xmu.course.ui.support.SupportScreen

/** 所有非底部导航路由集中定义，避免运行时字符串错配。 */
object AppRoutes {
    const val TIMETABLE = "timetable"
    const val IMPORT = "import"
    const val WEBVIEW = "webview"
    const val SETTINGS = "settings"
    const val TIMETABLE_MANAGER = "timetable_manager"
    const val COURSE_MANAGER = "course_manager"
    const val SKIP_COURSES = "skip_courses"
    const val TIMETABLE_SETTINGS = "timetable_settings"
    const val BACKGROUND_PICKER = "background_picker"
    const val BACKGROUND_EDITOR = "background_editor"
    const val USER_GUIDE = "user_guide"
    const val SUPPORT = "support"
}

/** 底部导航目标定义。 */
enum class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Timetable("timetable", "课表", Icons.Filled.CalendarMonth),
    Import("import", "导入", Icons.Filled.CloudDownload),
    Guide(AppRoutes.USER_GUIDE, "教程", Icons.AutoMirrored.Filled.MenuBook),
    Settings("settings", "设置", Icons.Filled.Settings),
}

/**
 * 应用根布局：底部导航 + NavHost。
 */
@Composable
fun XmuCourseApp(
    initialCourseId: Long? = null,
    onInitialCourseConsumed: (Long?) -> Unit = {},
) {
    // 首次启动引导：只显示一次（SharedPreferences 标记）。
    val context = androidx.compose.ui.platform.LocalContext.current
    var showWelcome by remember {
        mutableStateOf(
            context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                .getBoolean("onboarding_done", false).not(),
        )
    }
    if (showWelcome) {
        WelcomeScreen(
            onGetStarted = {
                context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("onboarding_done", true).apply()
                showWelcome = false
            },
        )
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(AppDestination.Timetable.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Timetable.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppDestination.Timetable.route) {
                TimetableScreen(
                    initialCourseId = initialCourseId,
                    onInitialCourseConsumed = onInitialCourseConsumed,
                )
            }
            composable(AppDestination.Import.route) { ImportScreen(onOpenWebView = { navController.navigate("webview") }) }
            composable("webview") { WebViewScreen(onBack = { navController.popBackStack() }) }
            composable(AppDestination.Settings.route) {
                SettingsScreen(
                    onOpenTimetableManager = { navController.navigate("timetable_manager") },
                    onOpenCourseManager = { navController.navigate("course_manager") },
                    onOpenTimetableSettings = { navController.navigate("timetable_settings") },
                    onOpenBackgroundPicker = { navController.navigate("background_picker") },
                    onOpenSupport = { navController.navigate(AppRoutes.SUPPORT) },
                )
            }
            composable("timetable_manager") {
                TimetableManagerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTimetableSettings = { navController.navigate("timetable_settings") },
                )
            }
            composable("timetable_settings") { TimetableSettingsScreen(onBack = { navController.popBackStack() }) }
            composable("course_manager") {
                CourseManagerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSkipCourses = { navController.navigate("skip_courses") },
                )
            }
            composable("skip_courses") { SkipCourseScreen(onBack = { navController.popBackStack() }) }
            composable("background_picker") {
                BackgroundPickerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEditor = { navController.navigate("background_editor") },
                )
            }
            composable("background_editor") {
                BackgroundEditorScreen(onBack = { navController.popBackStack() })
            }
            composable(AppRoutes.USER_GUIDE) { UserGuideScreen(onBack = { navController.popBackStack() }) }
            composable(AppRoutes.SUPPORT) { SupportScreen(onBack = { navController.popBackStack() }) }
        }
    }
}






