package com.xmu.course.ui


import android.app.Application
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.rememberNavController
import com.xmu.course.XmuCourseApplication
import com.xmu.course.AppLinks
import com.xmu.course.adapter.jw.XmuJwAdapter
import com.xmu.course.ui.welcome.WelcomeScreen
import com.xmu.course.ui.welcome.StartupDestinationChooserScreen
import com.xmu.course.contracts.presentation.StartupDestination
import com.xmu.course.ui.import.ImportScreen
import com.xmu.course.ui.import.ImportViewModel
import com.xmu.course.ui.import.WebViewScreen
import com.xmu.course.ui.course.CourseManagerScreen
import com.xmu.course.ui.course.SkipCourseScreen
import com.xmu.course.ui.manager.TimetableManagerScreen
import com.xmu.course.ui.settings.TimetableSettingsScreen
import com.xmu.course.ui.settings.SettingsScreen
import com.xmu.course.ui.settings.SettingsViewModel
import com.xmu.course.ui.background.BackgroundPickerScreen
import com.xmu.course.ui.background.BackgroundEditorScreen
import com.xmu.course.ui.timetable.TimetableScreen
import com.xmu.course.ui.timetable.TimetableViewModel
import com.xmu.course.ui.timetable.TimetableViewModelFactory
import com.xmu.course.ui.guide.UserGuideScreen
import com.xmu.course.ui.support.SupportScreen
import com.xmu.course.ui.auth.AuthCenterScreen
import com.xmu.course.ui.auth.AuthCenterViewModel
import com.xmu.course.ui.auth.AuthCenterViewModelFactory
import com.xmu.course.ui.auth.JwAuthScreen
import com.xmu.course.ui.auth.JwAcademicReportScreen
import com.xmu.course.ui.auth.JwAcademicRefreshController
import com.xmu.course.ui.home.HomeScreen
import com.xmu.course.ui.campusservice.CampusServiceScreen
import com.xmu.course.ui.campusservice.CampusServiceViewModel
import com.xmu.course.ui.providermanagement.ProviderDataManagementScreen
import com.xmu.course.ui.providermanagement.ProviderDataManagementViewModel
import com.xmu.course.ui.providermanagement.ProviderDataManagementViewModelFactory
import com.xmu.course.ui.providermanagement.ProviderManagementScreen
import com.xmu.course.ui.profile.ProfileScreen
import com.xmu.course.ui.providermanagement.ProviderManagementViewModel
import com.xmu.course.di.FlowAuthStateSource
import com.xmu.course.di.ProviderDescriptors
import com.xmu.course.di.toContractAuthState
import com.xmu.course.contracts.provider.ProviderManagementEntry
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.data.privacy.TronClassDataOwner
import com.xmu.course.data.privacy.WiseduTimetableDataOwner
import com.xmu.course.data.privacy.AcademicImportDataOwner
import com.xmu.course.ui.datamanagement.DataManagementScreen
import com.xmu.course.ui.datamanagement.DataManagementSource
import com.xmu.course.ui.tronclass.TronClassScreen
import com.xmu.course.ui.tronclass.TronClassViewModel
import com.xmu.course.ui.tronclass.TronClassViewModelFactory
import com.xmu.course.ui.tronclass.auth.EXTRA_CLEAR_TRONCLASS_AUTH
import com.xmu.course.ui.tronclass.auth.TronClassAuthActivity
import com.xmu.course.ui.todo.TodoScreen
import com.xmu.course.ui.todo.TodoViewModel
import com.xmu.course.ui.todo.TodoViewModelFactory
import com.xmu.course.ui.grades.GradesSandboxScreen
import com.xmu.course.ui.grades.GradesSandboxViewModel
import com.xmu.course.ui.grades.GradesSandboxViewModelFactory
import com.xmu.course.ui.academic.AcademicDataSourcesScreen
import com.xmu.course.ui.academic.AcademicDataSourcesViewModel
import com.xmu.course.ui.academic.AcademicGpaScreen
import com.xmu.course.ui.academic.AcademicGpaViewModel
import com.xmu.course.ui.academic.AcademicHistoryScreen
import com.xmu.course.ui.academic.AcademicHistoryViewModel
import com.xmu.course.ui.academic.AcademicHomeScreen
import com.xmu.course.ui.academic.AcademicHomeViewModel
import com.xmu.course.ui.academic.AcademicSemesterScreen
import com.xmu.course.ui.academic.AcademicViewModels
import com.xmu.course.ui.academiccompletion.AcademicCompletionScreen
import com.xmu.course.ui.academiccompletion.AcademicCompletionViewModel
import com.xmu.course.ui.academiccompletion.AcademicCompletionViewModelFactory
import com.xmu.course.ui.update.UpdateDialog
import com.xmu.course.ui.update.UpdateViewModel
import com.xmu.course.ui.update.UpdateViewModelFactory
import com.xmu.course.ui.guide.GuideContent
import com.xmu.course.ui.tutorial.LocalTutorialGuideLauncher
import com.xmu.course.ui.tutorial.LocalTutorialPreference

/** 所有非底部导航路由集中定义，避免运行时字符串错配。 */
object AppRoutes {
    const val HOME = "home"
    const val TIMETABLE = "timetable"
    const val IMPORT = "import"
    /** 导入页路由模板：origin 记录发起页，成功后回该页而不是硬编码课表。 */
    const val IMPORT_WITH_ORIGIN = "import?origin={origin}"
    const val TRONCLASS = "tronclass"
    const val TRONCLASS_COURSES = "tronclass_courses"
    const val TODO = "todo"
    const val GRADES_SANDBOX = "grades_sandbox"
    const val GRADES = "grades"
    const val ACADEMIC_SEMESTER = "academic_semester"
    const val ACADEMIC_HISTORY = "academic_history"
    const val ACADEMIC_GPA = "academic_gpa"
    const val ACADEMIC_DATA_SOURCES = "academic_data_sources"
    const val ACADEMIC_COMPLETION = "academic_completion"
    const val WEBVIEW = "webview"
    /** WebView 抓取页沿用同一 origin，抓取成功后仍回原发起页。 */
    const val WEBVIEW_WITH_ORIGIN = "webview?origin={origin}"
    const val JW_AUTH = "jw_auth"
    const val JW_ACADEMIC_REPORT = "jw_academic_report"
    /** 学业首页一次性刷新：可选 autoRefresh 参数；仍由用户手势触发。 */
    const val JW_ACADEMIC_REPORT_AUTO = "jw_academic_report?autoRefresh={autoRefresh}"
    const val JW_CERTIFICATE = "jw_certificate"
    const val CAMPUS_SERVICE = "campus_service"
    const val PROVIDER_MANAGEMENT = "provider_management"
    const val PROVIDER_DATA_MANAGEMENT = "provider_data_management/{providerId}"
    // NAV-B：次级 settings 路由已删除，「设置」root 只有 AppDestination.Profile 一个入口。
    const val TIMETABLE_MANAGER = "timetable_manager"
    const val COURSE_MANAGER = "course_manager"
    const val SKIP_COURSES = "skip_courses"
    const val TIMETABLE_SETTINGS = "timetable_settings"
    const val BACKGROUND_PICKER = "background_picker"
    const val BACKGROUND_EDITOR = "background_editor"
    const val USER_GUIDE = "user_guide"
    const val USER_GUIDE_WITH_SECTION = "user_guide?section={section}"
    const val SUPPORT = "support"
    const val PROFILE = "profile"
    const val PROFILE_DETAIL = "profile_detail"
    const val DATA_MANAGEMENT = "data_management"
    const val DATA_SOURCE_DETAIL = "data_management/{sourceId}"

    /** E2：教程「详细教程」跳转的指南深链接。 */
    fun guideRoute(sectionId: String): String = "user_guide?section=$sectionId"
}

/** 底部导航目的地定义：一级 5 tab + 各自二级页面分组。 */
enum class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val childRoutes: Set<String> = emptySet(),
) {
    Home(
        route = AppRoutes.HOME,
        label = "首页",
        icon = Icons.Outlined.Today,
    ),
    Timetable(
        route = AppRoutes.TIMETABLE,
        label = "课表",
        icon = Icons.Outlined.CalendarMonth,
        childRoutes = setOf(
            AppRoutes.IMPORT,
            AppRoutes.WEBVIEW,
            AppRoutes.TIMETABLE_MANAGER,
            AppRoutes.COURSE_MANAGER,
            AppRoutes.SKIP_COURSES,
            AppRoutes.TIMETABLE_SETTINGS,
            AppRoutes.BACKGROUND_PICKER,
            AppRoutes.BACKGROUND_EDITOR,
        ),
    ),
    Todo(route = AppRoutes.TODO, label = "待办", icon = Icons.Outlined.CheckCircle),
    Grades(
        route = AppRoutes.GRADES,
        label = "学业",
        icon = Icons.Outlined.Grade,
        childRoutes = setOf(
            AppRoutes.GRADES_SANDBOX,
            AppRoutes.ACADEMIC_SEMESTER,
            AppRoutes.ACADEMIC_HISTORY,
            AppRoutes.ACADEMIC_GPA,
            AppRoutes.ACADEMIC_DATA_SOURCES,
            AppRoutes.ACADEMIC_COMPLETION,
            AppRoutes.JW_ACADEMIC_REPORT,
        ),
    ),
    Profile(
        route = AppRoutes.PROFILE,
        label = "设置",
        icon = Icons.Outlined.Settings,
        childRoutes = setOf(
            AppRoutes.PROFILE_DETAIL,
            AppRoutes.TRONCLASS,
            AppRoutes.TRONCLASS_COURSES,
            AppRoutes.JW_AUTH,
            AppRoutes.USER_GUIDE,
            AppRoutes.SUPPORT,
            AppRoutes.CAMPUS_SERVICE,
            AppRoutes.PROVIDER_MANAGEMENT,
            AppRoutes.PROVIDER_DATA_MANAGEMENT.substringBefore("/{"),
            AppRoutes.JW_ACADEMIC_REPORT,
            AppRoutes.JW_CERTIFICATE,
        ),
    ),
}

/** 导入页与抓取页共用的 origin 路由参数声明。 */
internal fun originArgument(): NamedNavArgument = navArgument(TimetableImportOrigin.ARGUMENT) {
    type = NavType.StringType
    defaultValue = TimetableImportOrigin.DEFAULT.name.lowercase()
}

/** 从返回栈条目读取 origin 参数原文。 */
internal fun originArgumentOf(entry: NavBackStackEntry): String? =
    entry.arguments?.getString(TimetableImportOrigin.ARGUMENT)

/** 按当前路由查找所属底部 tab（含二级页面分组），未命中返回 null。 */
fun appDestinationForRoute(route: String?): AppDestination? =
    route?.substringBefore('?')?.let { r ->
        AppDestination.entries.firstOrNull { destination ->
            destination.route == r ||
                destination.childRoutes.any { child -> r == child || r.startsWith("$child/") }
        }
    }

/** Resolve shared pages to the nearest owning tab already present in the active back stack. */
internal fun appDestinationForRouteStack(routes: List<String?>): AppDestination? {
    val currentRoute = routes.lastOrNull()
    val current = currentRoute?.substringBefore('?')
    val sharedRoute = current == AppRoutes.JW_ACADEMIC_REPORT ||
        current == AppRoutes.DATA_MANAGEMENT ||
        current?.startsWith("${AppRoutes.DATA_MANAGEMENT}/") == true
    if (!sharedRoute) return appDestinationForRoute(currentRoute)

    val owner = routes.dropLast(1).asReversed().firstNotNullOfOrNull { route ->
        val normalized = route?.substringBefore('?')
        val isShared = normalized == AppRoutes.JW_ACADEMIC_REPORT ||
            normalized == AppRoutes.DATA_MANAGEMENT ||
            normalized?.startsWith("${AppRoutes.DATA_MANAGEMENT}/") == true
        if (isShared) null else appDestinationForRoute(route)
    }
    return owner ?: if (current == AppRoutes.JW_ACADEMIC_REPORT) {
        AppDestination.Grades
    } else {
        AppDestination.Profile
    }
}

/** Resolve shared destinations against the most recently visible owning tab. */
internal fun appDestinationForCurrentRoute(
    route: String?,
    lastKnownOwner: AppDestination?,
): AppDestination? {
    val normalized = route?.substringBefore('?')
    val isShared = normalized == AppRoutes.JW_ACADEMIC_REPORT ||
        normalized == AppRoutes.DATA_MANAGEMENT ||
        normalized?.startsWith("${AppRoutes.DATA_MANAGEMENT}/") == true
    if (!isShared) return appDestinationForRoute(route)
    return lastKnownOwner ?: if (normalized == AppRoutes.JW_ACADEMIC_REPORT) {
        AppDestination.Grades
    } else {
        AppDestination.Profile
    }
}

/** 查询完成时仅从仍显示的自动刷新页退回一步；已由用户离开的页面不再改变返回栈。 */
internal fun finishAcademicAutoRefreshIfCurrent(
    currentRoute: String?,
    popBackStack: () -> Boolean,
): Boolean {
    if (currentRoute != AppRoutes.JW_ACADEMIC_REPORT_AUTO) return false
    return popBackStack()
}

private val routesWithoutMotion = setOf(
    AppRoutes.IMPORT,
    AppRoutes.WEBVIEW,
    AppRoutes.JW_AUTH,
    AppRoutes.JW_ACADEMIC_REPORT,
    AppRoutes.JW_CERTIFICATE,
)

private fun normalizedRoute(route: String?): String? =
    route?.substringBefore('?')?.substringBefore('/')

/** Credential-bearing institutional WebViews must never be sampled for backdrop effects. */
internal fun isGlassBackdropCaptureAllowed(route: String?): Boolean =
    normalizedRoute(route) !in routesWithoutMotion

private const val APP_TAB_CROSSFADE_DURATION_MS = 150
private const val APP_PAGE_TRANSITION_DURATION_MS = 180
private const val APP_PAGE_SLIDE_DIVISOR = 32

/** Keep institutional authentication and WebView routes still during navigation transitions. */
internal fun shouldAnimateAppRouteTransition(fromRoute: String?, toRoute: String?): Boolean {
    val from = normalizedRoute(fromRoute) ?: return false
    val to = normalizedRoute(toRoute) ?: return false
    return from !in routesWithoutMotion && to !in routesWithoutMotion
}

/** Top-level tabs change in place, so they crossfade instead of using a push/pop motion. */
internal fun isTopLevelTabSwitch(
    fromRoute: String?,
    toRoute: String?,
    isPop: Boolean = false,
): Boolean {
    val from = normalizedRoute(fromRoute) ?: return false
    val to = normalizedRoute(toRoute) ?: return false
    if (from == to) return false
    val fromTab = appDestinationForRoute(from)
    val toTab = appDestinationForRoute(to)
    if (fromTab != null && toTab != null) return fromTab != toTab

    val targetIsTabRoot = AppDestination.entries.any { it.route == to }
    return !isPop && fromTab == null && targetIsTabRoot && from == AppRoutes.DATA_MANAGEMENT
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.appEnterTransition(
    isPop: Boolean,
): EnterTransition {
    val fromRoute = initialState.destination.route
    val toRoute = targetState.destination.route
    if (!shouldAnimateAppRouteTransition(fromRoute, toRoute)) return EnterTransition.None
    if (isTopLevelTabSwitch(fromRoute, toRoute, isPop)) {
        return fadeIn(animationSpec = tween(APP_TAB_CROSSFADE_DURATION_MS))
    }

    val direction = if (isPop) -1 else 1
    return fadeIn(animationSpec = tween(APP_PAGE_TRANSITION_DURATION_MS)) +
        slideInHorizontally(
            animationSpec = tween(APP_PAGE_TRANSITION_DURATION_MS),
            initialOffsetX = { fullWidth -> fullWidth / APP_PAGE_SLIDE_DIVISOR * direction },
        )
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.appExitTransition(
    isPop: Boolean,
): ExitTransition {
    val fromRoute = initialState.destination.route
    val toRoute = targetState.destination.route
    if (!shouldAnimateAppRouteTransition(fromRoute, toRoute)) return ExitTransition.None
    if (isTopLevelTabSwitch(fromRoute, toRoute, isPop)) {
        return fadeOut(animationSpec = tween(APP_TAB_CROSSFADE_DURATION_MS))
    }

    val direction = if (isPop) 1 else -1
    return fadeOut(animationSpec = tween(APP_PAGE_TRANSITION_DURATION_MS)) +
        slideOutHorizontally(
            animationSpec = tween(APP_PAGE_TRANSITION_DURATION_MS),
            targetOffsetX = { fullWidth -> fullWidth / APP_PAGE_SLIDE_DIVISOR * direction },
        )
}

/**
 * 应用根布局：底部导航 + NavHost。
 */
@Composable
fun XmuCourseApp(
    initialCourseId: Long? = null,
    onInitialCourseConsumed: (Long?) -> Unit = {},
    initialOpenTodo: Boolean = false,
    onInitialTodoConsumed: () -> Unit = {},
) {
    val appViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "XmuCourseApp requires a ViewModelStoreOwner"
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val application = context.applicationContext as? XmuCourseApplication
        ?: error("XmuCourseApp requires XmuCourseApplication")
    val onboardingPreference = application.appContainer.onboardingPreference
    var showWelcome by remember {
        mutableStateOf(!onboardingPreference.isOnboardingCompleted())
    }
    if (showWelcome) {
        WelcomeScreen(
            onGetStarted = {
                onboardingPreference.setOnboardingCompleted()
                showWelcome = false
            },
        )
        return
    }

    val startupDestinationPreference = application.appContainer.startupDestinationPreference
    var showStartupChoice by remember {
        mutableStateOf(startupDestinationPreference.destination() == null)
    }
    if (showStartupChoice) {
        StartupDestinationChooserScreen(
            onChoose = { destination ->
                startupDestinationPreference.setDestination(destination)
                showStartupChoice = false
            },
        )
        return
    }
    val startRoute = if (startupDestinationPreference.destination() == StartupDestination.TIMETABLE) {
        AppDestination.Timetable.route
    } else {
        AppDestination.Home.route
    }

    val navController = rememberNavController()
    val hazeState = remember { HazeState() }
    val navigationSurfaceColor = MaterialTheme.colorScheme.surface
    val navigationGlassStyle = remember(navigationSurfaceColor) {
        HazeStyle(
            backgroundColor = navigationSurfaceColor,
            tint = HazeTint(navigationSurfaceColor.copy(alpha = 0.72f)),
            blurRadius = 20.dp,
            noiseFactor = 0.05f,
            fallbackTint = HazeTint(navigationSurfaceColor.copy(alpha = 0.78f)),
        )
    }
    var openTodoEditorRequested by remember { mutableStateOf(false) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val glassBackdropCaptureAllowed = isGlassBackdropCaptureAllowed(currentRoute)
    val initialTab = appDestinationForRoute(startRoute) ?: AppDestination.Home
    var lastKnownOwnerRoute by rememberSaveable(startRoute) { mutableStateOf(initialTab.route) }
    LaunchedEffect(currentRoute) {
        appDestinationForRoute(currentRoute)?.let { lastKnownOwnerRoute = it.route }
    }
    val lastKnownOwner = AppDestination.entries.firstOrNull { it.route == lastKnownOwnerRoute }
    val selectedTab = appDestinationForCurrentRoute(currentRoute, lastKnownOwner)
    val navigateTab: (String) -> Unit = { route ->
        if (route == AppDestination.Profile.route) {
            // Phase 16 验收：离开"我的"后再次进入总是回到根设置页，不恢复二级详情。
            navController.navigate(route) {
                popUpTo(0) { saveState = true }
                launchSingleTop = true
            }
        } else {
            navController.navigate(route) {
                popUpTo(startRoute) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    // BUG-01：导入会话只有一个 ViewModel（Activity 作用域），导入页与抓取页共享同一状态；
    // 回跳归属集中在 completeTimetableImport，成功事件按令牌只消费一次。
    val importViewModel: ImportViewModel = viewModel()
    val completeTimetableImport: (TimetableImportOrigin) -> Unit = { origin ->
        if (importViewModel.claimImportCompletion()) {
            // 回到发起页；发起页不在栈内时退回收纳式 tab 导航，绝不回弹 WebView。
            if (!navController.popBackStack(origin.route, inclusive = false)) {
                navigateTab(origin.route)
            }
        }
    }
    // BUG-04：学业子页兄弟化——始终以「学业」root 为父，重复点击不叠加副本。
    val navigateAcademicChild: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(AppRoutes.GRADES) { inclusive = false }
            launchSingleTop = true
        }
    }
    val navigateSingleTop: (String) -> Unit = { route ->
        navController.navigate(route) { launchSingleTop = true }
    }
    val jwAdapter = remember {
        XmuJwAdapter(
            onOpenAcademicReport = { navController.navigate(AppRoutes.JW_ACADEMIC_REPORT) },
            onOpenCertificate = { navController.navigate(AppRoutes.JW_CERTIFICATE) },
            openDocumentStream = context.contentResolver::openInputStream,
        )
    }
    val database = remember(application) { application.appContainer.database }
    val wiseduDataOwner = remember(database) {
        WiseduTimetableDataOwner(database) { TimetablePrefs.setCurrent(context, null) }
    }
    val tronDataOwner = remember(database) { TronClassDataOwner(database) }
    val academicImportDataOwner = remember(database) { AcademicImportDataOwner(database) }
    val academicCompletionDataOwner = remember(application) { application.appContainer.academicCompletionDataOwner }
    val jwGradeDataOwner = remember(application) { application.appContainer.jwGradeDataOwner }
    // 双源刷新控制器：官方查询页手动刷新与学业首页一次性自动刷新共用同一装配。
    val jwAcademicRefreshController = remember(application) {
        JwAcademicRefreshController(
            academicStore = application.appContainer.academicCompletionStore,
            gradeStore = application.appContainer.jwGradeStore,
        )
    }
    val dataManagementSources = remember(
        wiseduDataOwner,
        tronDataOwner,
        academicImportDataOwner,
        academicCompletionDataOwner,
        jwGradeDataOwner,
    ) {
        listOf(
            DataManagementSource("xmu.wisedu", "课表数据", "手动导入的学期、课程与课表", wiseduDataOwner),
            DataManagementSource("xmu.tronclass", "畅课数据", "畅课同步的课程与待办", tronDataOwner),
            DataManagementSource("xmu.academic_import", "旧导入成绩（本地）", "停用导入通道留下的本机成绩，可与教务成绩分别清除", academicImportDataOwner),
            DataManagementSource("xmu.academic_completion", "培养方案进度快照", "教务刷新后保存在本机的培养方案与课程进度", academicCompletionDataOwner),
            DataManagementSource("xmu.jw_grades", "成绩数据缓存", "手动刷新缓存的教务成绩单", jwGradeDataOwner),
        )
    }
    val updateFactory = remember(application) { UpdateViewModelFactory.fromApplication(application) }
    val updateViewModel: UpdateViewModel = viewModel(factory = updateFactory)
    val updateState by updateViewModel.uiState.collectAsState()
    val todoFactory = remember(application) { TodoViewModelFactory.fromApplication(application) }
    val todoViewModel: TodoViewModel = viewModel(factory = todoFactory)
    val timetableFactory = remember(application) { TimetableViewModelFactory.fromApplication(application) }
    val timetableViewModel: TimetableViewModel = viewModel(factory = timetableFactory)
    val authFactory = remember(application) { AuthCenterViewModelFactory.fromApplication(application) }
    val authViewModel: AuthCenterViewModel = viewModel(factory = authFactory)
    val providerManagementEntries = remember {
        // 状态源只读 AuthCenterViewModel 已有状态（同一 StateFlow 快照），
        // 无网络请求；JW 与 Wisedu 共享同域 SSO 会话，复用其派生状态，不自动检查 JW。
        listOf(
            ProviderManagementEntry(
                descriptor = ProviderDescriptors.WISEDU,
                dataOwner = wiseduDataOwner,
                statusSource = FlowAuthStateSource {
                    authViewModel.uiState.value.wisedu.status.toContractAuthState()
                },
            ),
            ProviderManagementEntry(
                descriptor = ProviderDescriptors.TRONCLASS,
                dataOwner = tronDataOwner,
                statusSource = FlowAuthStateSource {
                    authViewModel.uiState.value.tronClass.status.toContractAuthState()
                },
            ),
            ProviderManagementEntry(
                descriptor = jwAdapter.descriptor(),
                statusSource = FlowAuthStateSource {
                    authViewModel.uiState.value.wisedu.status.toContractAuthState()
                },
            ),
        )
    }
    DisposableEffect(todoViewModel, timetableViewModel) {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    todoViewModel.onForeground()
                    timetableViewModel.onForeground()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    todoViewModel.onBackground()
                    timetableViewModel.onBackground()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            todoViewModel.onForeground()
            timetableViewModel.onForeground()
        }
        onDispose {
            lifecycle.removeObserver(observer)
            todoViewModel.onBackground()
            timetableViewModel.onBackground()
        }
    }

    LaunchedEffect(Unit) {
        updateViewModel.checkAutomatically()
    }

    LaunchedEffect(initialOpenTodo) {
        if (initialOpenTodo) {
            navController.navigate(AppDestination.Todo.route) {
                popUpTo(startRoute) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            onInitialTodoConsumed()
        }
    }

    Scaffold(
        bottomBar = {
            Column(
                modifier = if (glassBackdropCaptureAllowed) {
                    Modifier.hazeChild(
                        state = hazeState,
                        style = navigationGlassStyle,
                    )
                } else {
                    Modifier
                },
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
                NavigationBar(
                    containerColor = if (glassBackdropCaptureAllowed) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    tonalElevation = 0.dp,
                ) {
                    AppDestination.entries.forEach { destination ->
                        val selected = selectedTab == destination
                        val tabTint by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = tween(durationMillis = 180),
                            label = "${destination.route}-tab-tint",
                        )
                        val iconScale = animateFloatAsState(
                            targetValue = if (selected) 1.045f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                            label = "${destination.route}-tab-icon-scale",
                        )
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTab(destination.route) },
                            icon = {
                                Icon(
                                    destination.icon,
                                    contentDescription = destination.label,
                                    tint = tabTint,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = iconScale.value
                                        scaleY = iconScale.value
                                    },
                                )
                            },
                            label = { Text(destination.label) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        CompositionLocalProvider(
            LocalTutorialPreference provides application.appContainer.tutorialPreferenceStore,
            LocalTutorialGuideLauncher provides { tutorialId ->
                GuideContent.sectionIdForTutorial(tutorialId)?.let { section ->
                    navController.navigate(AppRoutes.guideRoute(section))
                }
            },
        ) {
        NavHost(
            navController = navController,
            startDestination = startRoute,
            enterTransition = { appEnterTransition(isPop = false) },
            exitTransition = { appExitTransition(isPop = false) },
            popEnterTransition = { appEnterTransition(isPop = true) },
            popExitTransition = { appExitTransition(isPop = true) },
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .then(
                    if (glassBackdropCaptureAllowed) {
                        Modifier.haze(state = hazeState)
                    } else {
                        Modifier
                    },
                ),
        ) {
            composable(AppDestination.Home.route) {
                HomeScreen(
                    timetableViewModel = timetableViewModel,
                    todoViewModel = todoViewModel,
                    onOpenTimetable = { navigateTab(AppDestination.Timetable.route) },
                    onOpenTodo = { navigateTab(AppDestination.Todo.route) },
                    onAddTodo = {
                        openTodoEditorRequested = true
                        navigateTab(AppDestination.Todo.route)
                    },
                    onOpenImport = { navController.navigate(TimetableImportOrigin.HOME.importRoute) },
                )
            }
            composable(AppDestination.Timetable.route) {
                TimetableScreen(
                    viewModel = timetableViewModel,
                    initialCourseId = initialCourseId,
                    onInitialCourseConsumed = onInitialCourseConsumed,
                    onOpenImport = { navController.navigate(TimetableImportOrigin.TIMETABLE.importRoute) },
                    onOpenSettings = { navController.navigate(AppRoutes.TIMETABLE_SETTINGS) },
                )
            }
            composable(
                AppRoutes.IMPORT_WITH_ORIGIN,
                arguments = listOf(originArgument()),
            ) { backStackEntry ->
                val origin = TimetableImportOrigin.fromArgument(originArgumentOf(backStackEntry))
                ImportScreen(
                    viewModel = importViewModel,
                    onOpenWebView = { navController.navigate(origin.webviewRoute) },
                    onBack = { navController.popBackStack() },
                    onOpenGuide = { navController.navigate(AppRoutes.USER_GUIDE) },
                    onImportCompleted = { completeTimetableImport(origin) },
                )
            }
            composable(
                AppRoutes.WEBVIEW_WITH_ORIGIN,
                arguments = listOf(originArgument()),
            ) { backStackEntry ->
                val origin = TimetableImportOrigin.fromArgument(originArgumentOf(backStackEntry))
                WebViewScreen(
                    viewModel = importViewModel,
                    onBack = { navController.popBackStack() },
                    onImportCompleted = { completeTimetableImport(origin) },
                )
            }
            composable(AppRoutes.JW_AUTH) {
                JwAuthScreen(
                    onBack = { navController.popBackStack() },
                    onObservation = authViewModel::onWiseduObservation,
                )
            }
            composable(AppRoutes.JW_ACADEMIC_REPORT) {
                // 仅官方学业完成查询页提供手动刷新；证书页复用同一承载层但不挂控制器。
                JwAcademicReportScreen(
                    onBack = { navController.popBackStack() },
                    refreshController = jwAcademicRefreshController,
                )
            }
            composable(
                AppRoutes.JW_ACADEMIC_REPORT_AUTO,
                arguments = listOf(
                    navArgument("autoRefresh") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { backStackEntry ->
                // 学业首页的一次点击刷新：仍为手势触发，落回官方查询页后自动编排一次双源刷新。
                val autoRefresh = backStackEntry.arguments?.getBoolean("autoRefresh") == true
                JwAcademicReportScreen(
                    onBack = { navController.popBackStack() },
                    refreshController = jwAcademicRefreshController,
                    autoRefresh = autoRefresh,
                    onAutoRefreshFinished = {
                        finishAcademicAutoRefreshIfCurrent(
                            currentRoute = navController.currentDestination?.route,
                            popBackStack = { navController.popBackStack() },
                        )
                    },
                )
            }
            composable(AppRoutes.JW_CERTIFICATE) {
                JwAcademicReportScreen(
                    onBack = { navController.popBackStack() },
                    pageUrl = AppLinks.JW_CERTIFICATE_URL,
                    pageTitle = "证明申请",
                )
            }
            composable(AppRoutes.CAMPUS_SERVICE) {
                CampusServiceScreen(
                    viewModel = remember { CampusServiceViewModel(jwAdapter) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(AppRoutes.PROVIDER_MANAGEMENT) {
                ProviderManagementScreen(
                    viewModel = remember { ProviderManagementViewModel(providerManagementEntries) },
                    onBack = { navController.popBackStack() },
                    onManageData = { providerId ->
                        navController.navigate(
                            AppRoutes.PROVIDER_DATA_MANAGEMENT.replace("{providerId}", providerId),
                        )
                    },
                )
            }
            composable(
                AppRoutes.PROVIDER_DATA_MANAGEMENT,
                arguments = listOf(navArgument("providerId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val providerId = backStackEntry.arguments?.getString("providerId").orEmpty()
                val entry = providerManagementEntries.firstOrNull { it.descriptor.id == providerId }
                if (entry == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    val dataManagementViewModel: ProviderDataManagementViewModel = viewModel(
                        viewModelStoreOwner = appViewModelStoreOwner,
                        key = "provider-data-management:${entry.descriptor.id}",
                        factory = remember(entry) {
                            ProviderDataManagementViewModelFactory(entry.descriptor.id, entry.dataOwner)
                        },
                    )
                    ProviderDataManagementScreen(
                        viewModel = dataManagementViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(AppRoutes.DATA_MANAGEMENT) {
                DataManagementScreen(
                    sources = dataManagementSources,
                    onOpenSource = { sourceId ->
                        navController.navigate(AppRoutes.DATA_MANAGEMENT + "/" + sourceId)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                AppRoutes.DATA_SOURCE_DETAIL,
                arguments = listOf(navArgument("sourceId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val sourceId = backStackEntry.arguments?.getString("sourceId").orEmpty()
                val source = dataManagementSources.firstOrNull { it.sourceId == sourceId }
                if (source == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    val dataManagementViewModel: ProviderDataManagementViewModel = viewModel(
                        viewModelStoreOwner = appViewModelStoreOwner,
                        key = "provider-data-management:${source.sourceId}",
                        factory = remember(source) {
                            ProviderDataManagementViewModelFactory(source.sourceId, source.owner)
                        },
                    )
                    ProviderDataManagementScreen(
                        viewModel = dataManagementViewModel,
                        onBack = { navController.popBackStack() },
                        title = source.title,
                    )
                }
            }
            // Phase 16 验收："我的" tab 默认入口为根设置页；个人中心降为二级页面。
            composable(AppDestination.Profile.route) {
                val settingsViewModel: SettingsViewModel = viewModel()
                SettingsScreen(
                    onOpenTimetableManager = { navController.navigate(AppRoutes.TIMETABLE_MANAGER) },
                    onOpenCourseManager = { navController.navigate(AppRoutes.COURSE_MANAGER) },
                    onOpenTimetableSettings = { navController.navigate(AppRoutes.TIMETABLE_SETTINGS) },
                    onOpenBackgroundPicker = { navController.navigate(AppRoutes.BACKGROUND_PICKER) },
                    onOpenProfile = { navController.navigate(AppRoutes.PROFILE_DETAIL) },
                    automaticUpdateCheckEnabled = updateState.automaticCheckEnabled,
                    updateCheckInProgress = updateState.checking,
                    updateCheckResult = updateState.result,
                    onAutomaticUpdateCheckChanged = updateViewModel::setAutomaticCheckEnabled,
                    onCheckForUpdates = updateViewModel::checkManually,
                    onAutoRefreshTodoChanged = settingsViewModel::setAutoRefreshTodo,
                    onReplayGuide = { showWelcome = true },
                    onOpenGuide = { navController.navigate(AppRoutes.USER_GUIDE) },
                )
            }
            composable(AppRoutes.PROFILE_DETAIL) {
                ProfileScreen(
                    onBack = { navController.popBackStack() },
                    // NAV-B：默认打开页面回到唯一「设置」root，不再压入第二个 Settings 实例。
                    onOpenSettings = { navigateTab(AppDestination.Profile.route) },
                    onOpenAuthCenter = { navController.navigate(AppRoutes.TRONCLASS) },
                    onOpenGuide = { navController.navigate(AppRoutes.USER_GUIDE) },
                    onOpenSupport = { navController.navigate(AppRoutes.SUPPORT) },
                    onOpenProviderManagement = { navController.navigate(AppRoutes.PROVIDER_MANAGEMENT) },
                    onOpenDataManagement = { navController.navigate(AppRoutes.DATA_MANAGEMENT) },
                    onOpenTronClass = { navController.navigate(AppRoutes.TRONCLASS_COURSES) },
                )
            }
            composable(AppRoutes.TRONCLASS) {
                val clearTronDataLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { authViewModel.refresh() }
                AuthCenterScreen(
                    viewModel = authViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenWisedu = { navController.navigate(AppRoutes.JW_AUTH) },
                    onLogoutWisedu = authViewModel::clearWiseduState,
                    wiseduLogoutSupported = false,
                    onOpenTronClass = { navController.navigate(AppRoutes.TRONCLASS_COURSES) },
                    onLogoutTronClass = {
                        clearTronDataLauncher.launch(
                            Intent(context, TronClassAuthActivity::class.java)
                                .putExtra(EXTRA_CLEAR_TRONCLASS_AUTH, true),
                        )
                    },
                )
            }
            composable(AppRoutes.TRONCLASS_COURSES) {
                val application = context.applicationContext as Application
                val factory = remember(application) { TronClassViewModelFactory.fromApplication(application) }
                val tronViewModel: TronClassViewModel = viewModel(factory = factory)
                val authLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result -> tronViewModel.onAuthResult(result.resultCode) }
                val clearWebDataLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result -> tronViewModel.onWebDataCleared(result.resultCode) }
                TronClassScreen(
                    viewModel = tronViewModel,
                    onBack = { navController.popBackStack() },
                    onSyncCompleted = {
                        // BUG-02：只有真实同步成功且首次消费时才回待办；失败/取消保持原页。
                        if (tronViewModel.claimSyncCompletion()) {
                            navigateTab(AppDestination.Todo.route)
                        }
                    },
                    onLaunchAuth = {
                        authLauncher.launch(Intent(context, TronClassAuthActivity::class.java))
                    },
                    onClearWebData = {
                        clearWebDataLauncher.launch(
                            Intent(context, TronClassAuthActivity::class.java)
                                .putExtra(EXTRA_CLEAR_TRONCLASS_AUTH, true),
                        )
                    },
                )
            }
            composable(AppDestination.Todo.route) {
                TodoScreen(
                    viewModel = todoViewModel,
                    onOpenTronClass = {
                        // Phase 16 验收：畅课属于"我的"组，不得把课程页压入待办返回栈。
                        navController.navigate(AppDestination.Profile.route) {
                            popUpTo(AppDestination.Todo.route) { saveState = true }
                            launchSingleTop = true
                        }
                        navController.navigate(AppRoutes.TRONCLASS_COURSES)
                    },
                    openEditorRequest = openTodoEditorRequested,
                    onOpenEditorRequestConsumed = { openTodoEditorRequested = false },
                )
            }

            composable(AppRoutes.GRADES) {
                // 学业首页：只读聚合官方快照（培养方案 + 官方成绩单缓存 + 本地 GPA 策略），离线可浏览。
                val academicHomeViewModel: AcademicHomeViewModel =
                    viewModel(factory = AcademicViewModels.homeFactory(application))
                AcademicHomeScreen(
                    viewModel = academicHomeViewModel,
                    onRefresh = {
                        // MANUAL_ONLY：刷新仍是用户点击；官方会话页内自动编排一次双源查询。
                        navController.navigate(
                            "${AppRoutes.JW_ACADEMIC_REPORT}?autoRefresh=true",
                        ) { launchSingleTop = true }
                    },
                    onOpenSemester = { navigateAcademicChild(AppRoutes.ACADEMIC_SEMESTER) },
                    onOpenPlan = { navigateAcademicChild(AppRoutes.ACADEMIC_COMPLETION) },
                    onOpenHistory = { navigateAcademicChild(AppRoutes.ACADEMIC_HISTORY) },
                    onOpenGpa = { navigateAcademicChild(AppRoutes.ACADEMIC_GPA) },
                    onOpenSimulation = { navigateAcademicChild(AppRoutes.GRADES_SANDBOX) },
                    onOpenDataSources = { navigateAcademicChild(AppRoutes.ACADEMIC_DATA_SOURCES) },
                )
            }
            composable(AppRoutes.ACADEMIC_DATA_SOURCES) {
                // 数据与来源：刷新入口降级到这里；只读展示本机存量，原始页面独立分组。
                val dataSourcesViewModel: AcademicDataSourcesViewModel =
                    viewModel(factory = AcademicViewModels.dataSourcesFactory(application))
                AcademicDataSourcesScreen(
                    viewModel = dataSourcesViewModel,
                    onBack = { navController.popBackStack() },
                    onRefresh = {
                        // MANUAL_ONLY：刷新仍是用户点击。
                        navController.navigate(
                            "${AppRoutes.JW_ACADEMIC_REPORT}?autoRefresh=true",
                        ) { launchSingleTop = true }
                    },
                    onOpenDataManagement = { navController.navigate(AppRoutes.DATA_MANAGEMENT) },
                    // 数据来源 → GPA：只做单顶去重，返回仍回数据来源。
                    onOpenGpaSettings = { navigateSingleTop(AppRoutes.ACADEMIC_GPA) },
                    onOpenAcademicReport = { navController.navigate(AppRoutes.JW_ACADEMIC_REPORT) },
                    onOpenCertificate = { navController.navigate(AppRoutes.JW_CERTIFICATE) },
                    onOpenCampusService = { navController.navigate(AppRoutes.CAMPUS_SERVICE) },
                )
            }
            composable(AppRoutes.GRADES_SANDBOX) {
                val gradesFactory = remember(application) {
                    GradesSandboxViewModelFactory.fromApplication(application)
                }
                val gradesViewModel: GradesSandboxViewModel = viewModel(factory = gradesFactory)
                GradesSandboxScreen(
                    viewModel = gradesViewModel,
                    onBack = { navController.popBackStack() },
                    onRefresh = {
                        // MANUAL_ONLY：模拟页刷新同样由用户点击触发。
                        navController.navigate(
                            "${AppRoutes.JW_ACADEMIC_REPORT}?autoRefresh=true",
                        ) { launchSingleTop = true }
                    },
                    onOpenGpaSettings = { navigateAcademicChild(AppRoutes.ACADEMIC_GPA) },
                    onOpenSemester = { navigateSingleTop(AppRoutes.ACADEMIC_SEMESTER) },
                )
            }
            composable(AppRoutes.ACADEMIC_SEMESTER) {
                // 本学期：在修课程与学分确认，与培养方案详情共用同一 ViewModel 语义。
                val semesterViewModel: AcademicCompletionViewModel =
                    viewModel(factory = AcademicCompletionViewModelFactory.fromApplication(application))
                AcademicSemesterScreen(
                    viewModel = semesterViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(AppRoutes.ACADEMIC_HISTORY) {
                val historyViewModel: AcademicHistoryViewModel =
                    viewModel(factory = AcademicViewModels.historyFactory(application))
                AcademicHistoryScreen(
                    viewModel = historyViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(AppRoutes.ACADEMIC_GPA) {
                val gpaViewModel: AcademicGpaViewModel =
                    viewModel(factory = AcademicViewModels.gpaFactory(application))
                AcademicGpaScreen(
                    viewModel = gpaViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenSandbox = { navigateAcademicChild(AppRoutes.GRADES_SANDBOX) },
                )
            }
            composable(AppRoutes.ACADEMIC_COMPLETION) {
                val completionFactory = remember(application) {
                    AcademicCompletionViewModelFactory.fromApplication(application)
                }
                val completionViewModel: AcademicCompletionViewModel =
                    viewModel(factory = completionFactory)
                AcademicCompletionScreen(
                    viewModel = completionViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenSemester = { navigateSingleTop(AppRoutes.ACADEMIC_SEMESTER) },
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
            composable(
                AppRoutes.USER_GUIDE_WITH_SECTION,
                arguments = listOf(
                    navArgument("section") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                UserGuideScreen(
                    onBack = { navController.popBackStack() },
                    highlightSection = backStackEntry.arguments?.getString("section"),
                )
            }
            composable(AppRoutes.SUPPORT) { SupportScreen(onBack = { navController.popBackStack() }) }
        }
        }
    }

    (updateState.result as? com.xmu.course.data.update.UpdateCheckResult.Available)?.let { update ->
        UpdateDialog(
            update = update,
            onDismiss = updateViewModel::dismissResult,
            downloading = updateState.downloading,
            downloadedApkPath = updateState.downloadedApkPath,
            downloadError = updateState.downloadError,
            onDownload = { updateViewModel.download(update) },
        )
    }
}




