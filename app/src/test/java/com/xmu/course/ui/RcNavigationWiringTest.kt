package com.xmu.course.ui

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RC 装配层回归（BUG-01 / BUG-02 / BUG-04 / NAV-A / NAV-B / NAV-10）。
 *
 * 应用只有一个导航装配点 `XmuCourseApp`，因此这里用源码结构锁定回跳与页面归属契约，
 * 与 `RcNavigationContractTest`（纯函数）和 ViewModel 一次性事件测试互补。
 */
class RcNavigationWiringTest {

    private val app by lazy { lines("XmuCourseApp.kt") }
    private val joined: String by lazy { app.joinToString("\n") }

    @Test
    fun settingsHasExactlyOneRootInstance() {
        val settingsCalls = Regex("(?<![A-Za-z])SettingsScreen\\(").findAll(joined).count()
        assertEquals("「设置」只允许一个装配实例", 1, settingsCalls)
        assertFalse(app.any { "composable(AppRoutes.SETTINGS)" in it })
        assertTrue("个人中心仍是唯一有效入口", app.any { "onOpenProfile = { navController.navigate(AppRoutes.PROFILE_DETAIL) }" in it })
        assertFalse("不得再向次级 settings 压栈", app.any { "navigate(AppRoutes.SETTINGS)" in it })
    }

    @Test
    fun timetableImportReturnsToOriginThroughOneShotClaim() {
        assertTrue(app.any { "if (importViewModel.claimImportCompletion())" in it })
        assertTrue(app.any { "popBackStack(origin.route, inclusive = false)" in it })
        assertTrue(app.any { "navigateTab(origin.route)" in it })
        // 旧缺陷：成功后硬编码弹回课表，导致从首页发起导入时被回弹。
        assertFalse(app.any { "popBackStack(AppRoutes.TIMETABLE, inclusive = false)" in it })
        assertEquals("导入页与抓取页共享同一导入会话", 2, app.count { "viewModel = importViewModel," in it })
        assertTrue(app.any { "onOpenImport = { navController.navigate(TimetableImportOrigin.HOME.importRoute) }," in it })
        assertTrue(app.any { "onOpenImport = { navController.navigate(TimetableImportOrigin.TIMETABLE.importRoute) }," in it })
    }

    @Test
    fun tronClassSuccessNavigatesToTodoOnce() {
        assertTrue(app.any { "if (tronViewModel.claimSyncCompletion())" in it })
        val claimLine = app.indexOfFirst { "if (tronViewModel.claimSyncCompletion())" in it }
        assertTrue(claimLine >= 0)
        assertTrue(
            "认领成功后回待办",
            app.subList(claimLine, claimLine + 3).any { "navigateTab(AppDestination.Todo.route)" in it },
        )
    }

    @Test
    fun academicChildrenUseBoundedSiblingNavigation() {
        listOf(
            "onOpenGpa = { navigateAcademicChild(AppRoutes.ACADEMIC_GPA) },",
            "onOpenSimulation = { navigateAcademicChild(AppRoutes.GRADES_SANDBOX) },",
            "onOpenSandbox = { navigateAcademicChild(AppRoutes.GRADES_SANDBOX) },",
            "onOpenGpaSettings = { navigateAcademicChild(AppRoutes.ACADEMIC_GPA) },",
        ).forEach { expected ->
            assertTrue("缺少兄弟化导航装配: $expected", app.any { line -> line.trim() == expected })
        }
        assertFalse(
            "GPA/模拟不得再裸 navigate",
            app.any { "onOpenGpa = { navController.navigate" in it || "onOpenSandbox = { navController.navigate" in it },
        )
        assertTrue(
            "重复点击需单顶去重",
            app.any { "popUpTo(AppRoutes.GRADES) { inclusive = false }" in it } &&
                Regex("launchSingleTop = true").containsMatchIn(joined),
        )
    }

    /**
     * NAV-10 / F2 全量覆盖：以装配层的真实路由清单为唯一来源，
     * 逐个校验「每个详情页的每个 TopAppBar 都有标准返回箭头」，不再依赖手写抽样。
     */
    @Test
    fun routedDetailScreensExposeStandardBackArrow() {
        val routed = routedScreenNames()
        assertTrue("装配层至少应解析出 25 个页面，实际 $routed", routed.size >= 25)
        val detail = routed.filter { it !in rootScreens }
        assertTrue("详情页数量异常，路由解析可能失效：$detail", detail.size >= 20)

        detail.sorted().forEach { screen ->
            val source = screenSource(screen)
            val bars = countOccurrences(source, "TopAppBar(")
            val navIcons = countOccurrences(source, "navigationIcon = {")
            val labels = countOccurrences(source, "contentDescription = \"返回\"")
            val arrows = countOccurrences(source, "ArrowBack")
            val buttons = countOccurrences(source, "IconButton(")
            assertTrue("$screen 缺少 TopAppBar", bars >= 1)
            assertEquals("$screen 存在没有返回箭头的 TopAppBar", bars, navIcons)
            assertEquals("$screen 返回按钮缺少统一的「返回」标签", bars, labels)
            // 每处箭头 = 1 次 import + 1 次使用；不允许多余或弃用图标残留。
            assertEquals("$screen 的 ArrowBack 数量与 TopAppBar 不匹配", bars + 1, arrows)
            assertTrue("$screen 返回按钮必须由 IconButton 提供 >=48dp 触达区", buttons >= bars)
        }
    }

    /** F1：五个根页不得出现左上角返回。 */
    @Test
    fun rootScreensHaveNoTopLeftBack() {
        rootScreens.forEach { screen ->
            val source = screenSource(screen)
            assertFalse("$screen 是根页，不得出现返回箭头", source.contains("ArrowBack"))
            assertFalse("$screen 是根页，不得设置 navigationIcon", source.contains("navigationIcon = {"))
        }
    }

    /** F4：全站统一 AutoMirrored 箭头，禁止页面自定义或已弃用的箭头实现。 */
    @Test
    fun backArrowUsesSingleStandardIconEverywhere() {
        allUiSources().forEach { (relativePath, source) ->
            assertFalse("$relativePath 使用了弃用的 Icons.Filled.ArrowBack", source.contains("Icons.Filled.ArrowBack"))
            assertFalse(
                "$relativePath 仍导入弃用的 filled.ArrowBack",
                source.contains("androidx.compose.material.icons.filled.ArrowBack"),
            )
            Regex("navigationIcon = \\{(.|\\n)*?\\n\\s{0,16}\\},").findAll(source).forEach { block ->
                if (block.value.contains("IconButton")) {
                    assertTrue(
                        "$relativePath 返回按钮缺少统一的「返回」标签",
                        block.value.contains("contentDescription = \"返回\""),
                    )
                }
            }
        }
    }

    /** F3：三个 WebView 承载页的返回必须先消化站内历史，再出栈，避免双次回退与死箭头。 */
    @Test
    fun webViewHostsResolveBackThroughHistoryFirst() {
        listOf(
            listOf("import", "WebViewScreen.kt"),
            listOf("auth", "JwAuthScreen.kt"),
            listOf("auth", "JwAcademicReportScreen.kt"),
        ).forEach { relative ->
            val label = relative.joinToString("/")
            val source = Files.readAllLines(uiSource(*relative.toTypedArray())).joinToString("\n")
            assertTrue("$label 缺少 WebView 历史判定", source.contains("canGoBack"))
            assertTrue("$label 缺少站内回退", source.contains("goBack()"))
            assertTrue("$label 缺少系统返回接管", source.contains("BackHandler"))
            val arrowBlock = Regex("navigationIcon = \\{[\\s\\S]*?ArrowBack").find(source)
            assertTrue("$label 找不到返回按钮区块", arrowBlock != null)
            assertTrue(
                "$label 左上角箭头必须与系统返回共用同一历史优先判定",
                arrowBlock!!.value.contains("canGoBack") || arrowBlock.value.contains("navigateBack"),
            )
        }
    }

    private val rootScreens = setOf(
        "HomeScreen",
        "TimetableScreen",
        "TodoScreen",
        "SettingsScreen",
        "AcademicHomeScreen",
    )

    /** 从唯一的装配点解析真实路由清单：每个 composable( 区块内的第一个 *Screen( 调用。 */
    private fun routedScreenNames(): Set<String> {
        val starts = Regex("\\bcomposable\\(").findAll(joined).map { it.range.first }.toList()
        assertTrue("装配层未解析到任何 composable 路由", starts.isNotEmpty())
        return starts.mapIndexed { index, start ->
            val segment = joined.substring(start, starts.getOrNull(index + 1) ?: joined.length)
            Regex("([A-Z][A-Za-z0-9]*Screen)\\s*\\(").find(segment)?.groupValues?.get(1)
        }.filterNotNull().toSet()
    }

    private val screenImplementations: Map<String, Path> by lazy {
        buildMap {
            Files.walk(uiRoot()).use { stream ->
                stream.filter { it.toString().endsWith(".kt") }.forEach { path ->
                    val text = Files.readAllLines(path).joinToString("\n")
                    Regex("(?m)^fun\\s+([A-Z][A-Za-z0-9]*Screen)\\s*[\\(<]").findAll(text).forEach { match ->
                        val name = match.groupValues[1]
                        if (!containsKey(name)) put(name, path)
                    }
                }
            }
        }
    }

    private fun screenSource(screen: String): String {
        val path = screenImplementations[screen] ?: error("找不到页面实现：$screen")
        return Files.readAllLines(path).joinToString("\n")
    }

    private fun allUiSources(): List<Pair<String, String>> =
        Files.walk(uiRoot()).use { stream ->
            stream.filter { it.toString().endsWith(".kt") }.map { path ->
                uiRoot().relativize(path).toString().replace('\\', '/') to
                    Files.readAllLines(path).joinToString("\n")
            }.toList()
        }

    private fun countOccurrences(text: String, needle: String): Int {
        var index = text.indexOf(needle)
        var count = 0
        while (index >= 0) {
            count++
            index = text.indexOf(needle, index + needle.length)
        }
        return count
    }

    private fun uiRoot(): Path {
        val tail = listOf("src", "main", "java", "com", "xmu", "course", "ui")
        val candidates = listOf(
            Paths.get(tail.first(), *tail.drop(1).toTypedArray()),
            Paths.get("app", *tail.toTypedArray()),
        )
        return candidates.firstOrNull(Files::isDirectory)
            ?: error("Cannot locate ui source root from ${Paths.get("").toAbsolutePath()}")
    }

    private fun lines(fileName: String): List<String> = Files.readAllLines(uiSource(fileName))

    private fun uiSource(vararg parts: String): Path {
        val tail = listOf("com", "xmu", "course", "ui") + parts.toList()
        val candidates = listOf(
            Paths.get("src", "main", "java", *tail.toTypedArray()),
            Paths.get("app", "src", "main", "java", *tail.toTypedArray()),
        )
        return candidates.firstOrNull(Files::isRegularFile)
            ?: error("Cannot locate ${parts.last()} from ${Paths.get("").toAbsolutePath()}")
    }
}
